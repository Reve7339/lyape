"""SQL real de la migración 6→7, sin Android ni emulador.

Comprueba que el medio se separa del origen, que Yape queda asociado explícitamente y que Plin
no recibe una asociación ni una cuenta inventada.
"""

import json
from pathlib import Path
import re
import sqlite3
import unittest


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "app/src/main/java/com/lyape/data"
SCHEMAS = ROOT / "app/schemas/com.lyape.data.AppDatabase"


def create_schema(db, version):
    schema = json.loads((SCHEMAS / f"{version}.json").read_text())
    for entity in schema["database"]["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity["indices"]:
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    return schema


class CaptureSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.row_factory = sqlite3.Row
        self.addCleanup(self.db.close)
        schema = create_schema(self.db, 6)
        self.movements_schema = next(
            e for e in schema["database"]["entities"] if e["tableName"] == "movements"
        )
        self.db.execute("INSERT INTO cash_accounts VALUES ('yape','Yape','YAPE',NULL,0,0,0,0)")
        self.db.execute("INSERT INTO cash_accounts VALUES ('cash','Efectivo','CASH',NULL,0,0,0,0)")
        self.insert_movement("notification", 100, source="NOTIFICATION", account="yape")
        self.insert_movement("manual", 200, source="MANUAL", account=None)
        self.insert_movement("adjustment", -50, source="ADJUSTMENT", account=None)
        self.db.execute(
            "INSERT INTO monthly_expenses (id, month, name, amountCents) "
            "VALUES ('expense-1', '2026-10', 'Luz', 15000)"
        )

        migration = (DATA / "Migrations.kt").read_text().split("val MIGRATION_6_7", 1)[1]
        statements = re.findall(r'db\.execSQL\(\s*(""".*?"""|"[^"\n]*")', migration, re.S)
        self.assertGreaterEqual(len(statements), 4)
        for statement in statements:
            self.db.execute(statement[3:-3] if statement.startswith('"""') else statement[1:-1])

    def insert_movement(self, id, amount, source, account):
        values = {
            field["columnName"]: 0 if field["affinity"] == "INTEGER" else ""
            for field in self.movements_schema["fields"]
        }
        values.update(
            id=id,
            amountCents=amount,
            currency="PEN",
            occurredAt=1000,
            capturedAt=1000,
            source=source,
            classification="BUSINESS",
            reviewed=0,
            isEdited=0,
            createdAt=1000,
            updatedAt=1000,
            accountId=account,
        )
        columns = ",".join(values)
        placeholders = ",".join("?" for _ in values)
        self.db.execute(f"INSERT INTO movements ({columns}) VALUES ({placeholders})", tuple(values.values()))

    def test_medium_is_inferred_only_for_old_notifications(self):
        rows = {
            row["id"]: (row["medium"], row["accountId"])
            for row in self.db.execute("SELECT id, medium, accountId FROM movements")
        }
        self.assertEqual(("YAPE", "yape"), rows["notification"])
        self.assertEqual(("UNKNOWN", None), rows["manual"])
        self.assertEqual(("UNKNOWN", None), rows["adjustment"])

    def test_yape_is_explicit_but_plin_has_no_association_or_account(self):
        rows = self.db.execute(
            "SELECT provider, accountId FROM capture_accounts ORDER BY provider"
        ).fetchall()
        self.assertEqual([("YAPE", "yape")], [tuple(row) for row in rows])
        plin_accounts = self.db.execute(
            "SELECT id FROM cash_accounts WHERE kind='BANK' OR lower(name) LIKE '%plin%'"
        ).fetchall()
        self.assertEqual([], plin_accounts)

    def test_history_and_plan_are_preserved(self):
        self.assertEqual(
            3,
            self.db.execute("SELECT count(*) FROM movements").fetchone()[0],
        )
        self.assertEqual(
            15000,
            self.db.execute("SELECT amountCents FROM monthly_expenses WHERE id='expense-1'").fetchone()[0],
        )

    def test_migrated_schema_matches_exported_room_schema_7(self):
        expected = sqlite3.connect(":memory:")
        self.addCleanup(expected.close)
        schema = create_schema(expected, 7)
        for entity in schema["database"]["entities"]:
            name = entity["tableName"]
            expected_columns = {tuple(row)[1:] for row in expected.execute(f"PRAGMA table_info('{name}')")}
            actual_columns = {tuple(row)[1:] for row in self.db.execute(f"PRAGMA table_info('{name}')")}
            self.assertEqual(expected_columns, actual_columns, name)
            for index in entity["indices"]:
                index_name = index["name"]
                expected_index = expected.execute(f"PRAGMA index_info('{index_name}')").fetchall()
                actual_index = [tuple(row) for row in self.db.execute(f"PRAGMA index_info('{index_name}')")]
                self.assertEqual(expected_index, actual_index, index_name)


if __name__ == "__main__":
    unittest.main()
