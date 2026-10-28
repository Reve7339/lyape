"""Ejecuta el SQL de migración 7→8 en SQLite, sin Gradle ni dispositivos."""

import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = ROOT / "app/schemas/com.lyape.data.AppDatabase"


def create_schema(db, version):
    for entity in json.loads((SCHEMAS / f"{version}.json").read_text())["database"]["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))


class GoalMigrationSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        create_schema(self.db, 7)
        self.db.execute("INSERT INTO monthly_goals VALUES ('2026-10',50000)")
        self.db.execute("INSERT INTO goal_settings VALUES (0,'FIXED_MONTHLY',90000)")
        migration = (ROOT / "app/src/main/java/com/lyape/data/Migrations.kt").read_text()
        migration = migration.split("val MIGRATION_7_8", 1)[1].split("val MIGRATION_1_2", 1)[0]
        statements = re.findall(r'db\.execSQL\("([^"\n]+)"\)', migration)
        self.assertEqual(2, len(statements))
        for sql in statements:
            self.db.execute(sql)

    def test_old_goals_remain_income_with_same_amount_and_frequency(self):
        self.assertEqual(("2026-10", 50000, "INCOME"), self.db.execute("SELECT * FROM monthly_goals").fetchone())
        self.assertEqual((0, "FIXED_MONTHLY", 90000, "INCOME"), self.db.execute("SELECT * FROM goal_settings").fetchone())

    def test_migrated_goal_columns_match_room_8(self):
        with sqlite3.connect(":memory:") as fresh:
            create_schema(fresh, 8)
            for table in ("monthly_goals", "goal_settings"):
                self.assertEqual(
                    fresh.execute(f"PRAGMA table_info({table})").fetchall(),
                    self.db.execute(f"PRAGMA table_info({table})").fetchall(),
                )
