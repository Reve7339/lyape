"""SQL real de cuentas y migración, ejecutado sin Gradle ni emulador.

Complementa las pruebas Room; no sustituye la validación Android.
"""

import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "app/src/main/java/com/lyape/data"


class CashSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.row_factory = sqlite3.Row
        self.addCleanup(self.db.close)
        schema = json.loads((ROOT / "app/schemas/com.lyape.data.AppDatabase/5.json").read_text())
        self.movements_schema = next(e for e in schema["database"]["entities"] if e["tableName"] == "movements")
        for entity in schema["database"]["entities"]:
            self.db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
            for index in entity["indices"]:
                self.db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        self.insert_movement("old", 4000, at=500, source="NOTIFICATION", account=None, legacy=True)
        migration = (
            (DATA / "Migrations.kt").read_text()
            .split("val MIGRATION_5_6", 1)[1]
            .split("val MIGRATION_6_7", 1)[0]
        )
        # Todas las sentencias de esta migración son literales; incluye el seed compartido.
        statements = re.findall(r'db\.execSQL\(\s*(""".*?"""|"[^"\n]*")', migration, re.S)
        self.assertGreater(len(statements), 5)
        for statement in statements:
            self.db.execute(statement[3:-3] if statement.startswith('"""') else statement[1:-1])
        source = (DATA / "AccountsDao.kt").read_text()
        self.balances_sql = re.search(r'const val BALANCES = """(.*?)"""', source, re.S)[1]

    def insert_movement(self, id, amount, at=1100, source="MANUAL", classification="BUSINESS", account="yape", legacy=False):
        fields = self.movements_schema["fields"]
        values = {f["columnName"]: 0 if f["affinity"] == "INTEGER" else "" for f in fields}
        values.update(id=id, amountCents=amount, occurredAt=at, source=source, classification=classification)
        if not legacy:
            values["accountId"] = account
        columns = ",".join(values)
        placeholders = ",".join("?" for _ in values)
        self.db.execute(f"INSERT INTO movements ({columns}) VALUES ({placeholders})", tuple(values.values()))

    def set_balance(self, account="yape", business=10000, personal=2000, at=1000):
        sequence = self.db.execute("SELECT COALESCE(MAX(sequence),0) FROM cash_operations").fetchone()[0]
        movement_sequence = self.db.execute("SELECT COALESCE(MAX(rowid),0) FROM movements").fetchone()[0]
        self.db.execute("UPDATE cash_accounts SET baselineAt=?,baselineBusinessCents=?,baselinePersonalCents=?,"
                        "baselineOperationSequence=?,baselineMovementSequence=? WHERE id=?",
                        (at, business, personal, sequence, movement_sequence, account))

    def balance(self, account="yape"):
        row = self.db.execute(self.balances_sql + " WHERE a.id=?", (account,)).fetchone()
        return (row["baselineBusinessCents"] + row["movementBusiness"] + row["operationBusiness"],
                row["baselinePersonalCents"] + row["movementPersonal"] + row["operationPersonal"])

    def operation(self, id, amount, source="yape", destination=None, classification="BUSINESS", voided=None):
        self.db.execute("INSERT INTO cash_operations (id,kind,fromAccountId,toAccountId,classification,"
                        "amountCents,occurredAt,voidedAt) VALUES (?,?,?,?,?,?,?,?)",
                        (id, "TRANSFER" if destination else "WITHDRAWAL", source, destination,
                         classification, amount, 1200, voided))

    def test_migration_preserves_old_income_without_inventing_balance(self):
        old = self.db.execute("SELECT amountCents, accountId FROM movements WHERE id='old'").fetchone()
        self.assertEqual((4000, "yape"), tuple(old))
        self.assertEqual((0, 0), self.balance())
        self.assertIsNone(self.db.execute("SELECT baselineAt FROM cash_accounts WHERE id='yape'").fetchone()[0])

    def test_migrated_tables_and_indices_match_exported_room_schema(self):
        schema = json.loads((ROOT / "app/schemas/com.lyape.data.AppDatabase/6.json").read_text())
        with sqlite3.connect(":memory:") as fresh:
            for entity in schema["database"]["entities"]:
                name = entity["tableName"]
                fresh.execute(entity["createSql"].replace("${TABLE_NAME}", name))
                for index in entity["indices"]:
                    fresh.execute(index["createSql"].replace("${TABLE_NAME}", name))
                # SQLite column order is not part of Room's schema validation.
                expected = {tuple(row)[1:] for row in fresh.execute(f"PRAGMA table_info('{name}')")}
                actual = {tuple(row)[1:] for row in self.db.execute(f"PRAGMA table_info('{name}')")}
                self.assertEqual(expected, actual, name)
                for index in entity["indices"]:
                    index_name = index["name"]
                    expected_index = fresh.execute(f"PRAGMA index_info('{index_name}')").fetchall()
                    actual_index = [tuple(row) for row in self.db.execute(f"PRAGMA index_info('{index_name}')")]
                    self.assertEqual(expected_index, actual_index, index_name)

    def test_new_income_personal_and_excluded_use_separate_buckets(self):
        self.set_balance()
        self.insert_movement("business", 1000)
        self.insert_movement("personal", 500, classification="PERSONAL")
        self.insert_movement("excluded", 9999, classification="EXCLUDED")
        self.insert_movement("no-account", 9999, account=None)
        self.insert_movement("backdated", 9999, at=999)
        self.assertEqual((11000, 2500), self.balance())
        self.db.execute("UPDATE movements SET classification='PERSONAL' WHERE id='business'")
        self.assertEqual((10000, 3500), self.balance())

    def test_exact_baseline_millisecond_does_not_count_existing_rows_twice(self):
        self.insert_movement("already-there", 300, at=1000)
        self.set_balance()
        self.insert_movement("new-delivery", 700, at=1000)
        self.assertEqual((10700, 2000), self.balance())

    def test_transfer_conserves_total_and_personal_never_spends_business(self):
        self.set_balance()
        self.set_balance("cash", business=0, personal=0)
        self.operation("transfer", 500, destination="cash", classification="PERSONAL")
        self.assertEqual((10000, 1500), self.balance())
        self.assertEqual((0, 500), self.balance("cash"))
        self.assertEqual(12000, sum(self.balance()) + sum(self.balance("cash")))

    def test_voiding_restores_balance_without_deleting_history(self):
        self.set_balance()
        self.operation("expense-payment", 1500)
        self.assertEqual((8500, 2000), self.balance())
        self.db.execute("UPDATE cash_operations SET voidedAt=1300 WHERE id='expense-payment'")
        self.assertEqual((10000, 2000), self.balance())
        self.assertEqual(1, self.db.execute("SELECT count(*) FROM cash_operations").fetchone()[0])

    def test_voiding_old_payment_reopens_debt_without_changing_new_balance(self):
        self.set_balance()
        self.operation("payment", 1500)
        self.db.execute("UPDATE cash_operations SET kind='EXPENSE_PAYMENT',expenseId='expense',expensePeriod='2026-10' WHERE id='payment'")
        self.set_balance(business=6000, personal=1000, at=1400)
        source = (DATA / "AccountsDao.kt").read_text()
        void_query = re.search(r'@Query\("([^"]+)"\)\s*suspend fun voidOperation', source)[1]
        paid_query = re.search(r'@Query\("([^"]+)"\)\s*suspend fun paid', source)[1]
        self.assertEqual(1500, self.db.execute(paid_query, {"id": "expense", "period": "2026-10"}).fetchone()[0])
        self.db.execute(void_query, {"id": "payment", "timestamp": 1500})
        self.assertEqual((6000, 1000), self.balance())
        self.assertEqual(0, self.db.execute(paid_query, {"id": "expense", "period": "2026-10"}).fetchone()[0])

    def test_expense_history_filters_and_limits_without_hiding_voided_records(self):
        self.operation("first", 100)
        self.operation("second", 200, voided=1300)
        self.operation("unrelated", 300)
        self.db.execute("UPDATE cash_operations SET expenseId='expense' WHERE id IN ('first','second')")
        source = (DATA / "AccountsDao.kt").read_text()
        query = re.search(r'@Query\("([^"]+)"\)\s*fun observeExpenseOperations', source)[1]
        rows = self.db.execute(query, {"expenseId": "expense", "limit": 1}).fetchall()
        self.assertEqual(["second"], [row["id"] for row in rows])
        self.assertEqual(1300, rows[0]["voidedAt"])
        rows = self.db.execute(query, {"expenseId": "expense", "limit": 25}).fetchall()
        self.assertEqual(["second", "first"], [row["id"] for row in rows])

    def test_reconciliation_excludes_old_operations_and_income(self):
        self.set_balance()
        self.operation("before", 500)
        self.insert_movement("before-reset", 200, at=1300)
        self.set_balance(business=5000, personal=0, at=1400)
        self.operation("after", 300)
        self.insert_movement("after-reset", 150, at=1500)
        self.assertEqual((4850, 0), self.balance())

    def test_sql_overflow_errors_instead_of_converting_money_to_float(self):
        self.set_balance(business=0, personal=0)
        self.insert_movement("max", 9223372036854775807)
        self.insert_movement("overflow", 1)
        with self.assertRaises(sqlite3.OperationalError):
            self.balance()


if __name__ == "__main__":
    unittest.main()
