"""Ejecuta el SQL real del DAO sobre SQLite sin Android ni emulador.

Complementa (no sustituye) las pruebas instrumentadas de Room y la migración.
"""

import json
from pathlib import Path
import re
import sqlite3
import unittest


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "app/src/main/java/com/lyape/data"


class HistorySqlTest(unittest.TestCase):
    def setUp(self):
        source = (DATA / "MovementDao.kt").read_text()
        self.queries = {method: sql for sql, method in re.findall(
            r'@Query\(\s*"""(.*?)""",?\s*\)\s*fun (pageHistory|observeCounts)\(', source, re.S,
        )}
        # method -> SQL, extraído de las anotaciones que Room compila.
        version = re.search(r"version = (\d+)", (DATA / "AppDatabase.kt").read_text())[1]
        schema = json.loads((ROOT / f"app/schemas/com.lyape.data.AppDatabase/{version}.json").read_text())
        entity = next(e for e in schema["database"]["entities"] if e["tableName"] == "movements")
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.execute(entity["createSql"].replace("${TABLE_NAME}", "movements"))
        for index in entity["indices"]:
            self.db.execute(index["createSql"].replace("${TABLE_NAME}", "movements"))
        self.base = {f["columnName"]: 0 if f["affinity"] == "INTEGER" else "" for f in entity["fields"]}
        for i in range(201):
            self.insert(i)

    def insert(self, i, **overrides):
        record = {**self.base, "id": f"{i:032d}", "source": "MANUAL", "classification": "BUSINESS",
                  "amountCents": 100, "searchTitle": f"venta {i}", **overrides}
        columns = ",".join(f'"{c}"' for c in record)
        params = ",".join("?" for _ in record)
        self.db.execute(f"INSERT INTO movements ({columns}) VALUES ({params})", tuple(record.values()))

    def page(self, query="", classification=None, offset=0):
        return self.db.execute(
            self.queries["pageHistory"] + " LIMIT :limit OFFSET :offset",
            {"query": query, "classification": classification, "limit": 50, "offset": offset},
        ).fetchall()

    def test_pages_are_bounded_ordered_and_do_not_duplicate_ties(self):
        ids = []
        for offset in range(0, 201, 50):
            page = self.page(offset=offset)
            self.assertLessEqual(len(page), 50)
            ids.extend(row[0] for row in page)
        self.assertEqual([f"{i:032d}" for i in reversed(range(201))], ids)
        self.assertEqual(len(ids), len(set(ids)))

    def test_search_and_filter_reach_beyond_first_page(self):
        self.db.execute("UPDATE movements SET classification='PERSONAL', searchTitle='maria nunez' WHERE id=?", (f"{0:032d}",))
        self.assertEqual(1, len(self.page(query="maria", classification="PERSONAL")))
        self.assertEqual([], self.page(query="maria", classification="BUSINESS"))
        self.assertEqual([], self.page(query="no-existe"))

    def test_percent_underscore_and_quotes_are_literal(self):
        self.insert(202, searchDescription="pago 10%_literal", searchReason="devolucion o'connor")
        for query in ("%", "_", "10%_", "o'connor", "devolucion"):
            with self.subTest(query=query):
                self.assertEqual(1, len(self.page(query=query)))

    def test_counts_are_global_and_include_excluded(self):
        self.insert(202, classification="PERSONAL")
        self.insert(203, classification="EXCLUDED")
        self.assertEqual(50, len(self.page()))
        self.assertEqual((203, 201, 1, 1), self.db.execute(self.queries["observeCounts"]).fetchone())


if __name__ == "__main__":
    unittest.main()
