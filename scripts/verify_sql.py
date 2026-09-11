#!/usr/bin/env python3
"""
Verifies Daybook's SQL without an Android device.

There is no Android SDK or Gradle available in this environment, so the schema
and every DAO query are extracted straight out of the Kotlin sources and
executed against Python's sqlite3, which is the same engine Android ships.

Checks performed:
  1. Every CREATE statement in DaybookDatabase.SCHEMA executes.
  2. Every SELECT/COUNT string constant in the DAOs parses and runs, with the
     right number of bound parameters.
  3. The declared column names match what the DAO cursor readers ask for.
  4. tasks.meeting_id really is ON DELETE SET NULL, so deleting a meeting keeps
     its action items.
  5. The app-wide task ordering puts open work before closed work.

Exit code is non-zero if anything fails.
"""

from __future__ import annotations

import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/sr2ma/daybook"
DB_FILE = SRC / "data/DaybookDatabase.kt"
DAO_DIR = SRC / "data/dao"

SQL_START = re.compile(r"^\s*(CREATE|SELECT|INSERT|UPDATE|DELETE|PRAGMA)\b", re.IGNORECASE)
TRIPLE = re.compile(r'"""(.*?)"""', re.DOTALL)
SINGLE = re.compile(r'"((?:[^"\\\n]|\\.)*)"')

failures: list[str] = []
checks = 0


def check(label: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    if ok:
        print(f"  PASS  {label}")
    else:
        print(f"  FAIL  {label}{(' :: ' + detail) if detail else ''}")
        failures.append(label)


def sql_literals(path: Path) -> list[str]:
    """Every string literal in `path` that looks like SQL, in source order."""
    text = path.read_text(encoding="utf-8")
    found: list[tuple[int, str]] = []
    for match in TRIPLE.finditer(text):
        body = match.group(1)
        if SQL_START.match(body):
            found.append((match.start(), body.strip()))
    # Blank out triple-quoted regions so the single-quote pass cannot re-read them.
    masked = TRIPLE.sub(lambda m: '"""' + ("x" * len(m.group(1))) + '"""', text)
    for match in SINGLE.finditer(masked):
        body = match.group(1)
        if SQL_START.match(body):
            found.append((match.start(), body.strip()))
    found.sort(key=lambda pair: pair[0])
    return [body for _, body in found]


def main() -> int:
    print("== 1. schema ==")
    # Extract only the SCHEMA val block — stop before MIGRATIONS_ constants.
    raw = DB_FILE.read_text(encoding="utf-8")
    # Find the SCHEMA listOf( ... ) block up to the line before MIGRATIONS_V2
    migrations_pos = raw.find("val MIGRATIONS_V2")
    schema_region = raw[:migrations_pos] if migrations_pos != -1 else raw
    schema_match = re.search(r'val SCHEMA[^=]*=\s*listOf\((.*)\)', schema_region, re.DOTALL)
    schema_block = schema_match.group(1) if schema_match else schema_region
    import tempfile, os
    with tempfile.NamedTemporaryFile(mode='w', suffix='.kt', delete=False, encoding='utf-8') as tmp:
        tmp.write(schema_block)
        tmp_path = Path(tmp.name)
    schema = [s for s in sql_literals(tmp_path) if s.upper().startswith("CREATE")]
    os.unlink(tmp_path)
    check("schema statements were extracted", len(schema) >= 9, f"found {len(schema)}")

    connection = sqlite3.connect(":memory:")
    connection.execute("PRAGMA foreign_keys = ON")
    for statement in schema:
        head = " ".join(statement.split())[:58]
        try:
            connection.execute(statement)
            check(f"executes: {head}", True)
        except sqlite3.Error as error:
            check(f"executes: {head}", False, str(error))

    tables = {
        row[0]
        for row in connection.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
        )
    }
    check("tables created", tables == {"meetings", "tasks", "log_entries", "passes"}, str(sorted(tables)))

    indices = {
        row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type = 'index'")
    }
    check("eight named indices exist", len([i for i in indices if i.startswith("idx_")]) == 8, str(sorted(indices)))

    print("\n== 2. DAO queries ==")
    for dao in sorted(DAO_DIR.glob("*.kt")):
        for statement in sql_literals(dao):
            placeholders = statement.count("?")
            head = f"{dao.name}: {' '.join(statement.split())[:52]}"
            try:
                connection.execute(statement, tuple(["1"] * placeholders)).fetchall()
                check(head, True)
            except sqlite3.Error as error:
                check(head, False, str(error))

    print("\n== 3. columns the DAOs read ==")
    columns_by_table = {
        table: {row[1] for row in connection.execute(f"PRAGMA table_info({table})")}
        for table in ("tasks", "log_entries", "meetings")
    }
    dao_tables = {"TaskDao.kt": "tasks", "LogDao.kt": "log_entries", "MeetingDao.kt": "meetings"}
    reader = re.compile(r'(?:req|opt)(?:String|Long|Int|Boolean)\("([a-z_]+)"\)')
    writer = re.compile(r'put\("([a-z_]+)"')
    for dao_name, table in dao_tables.items():
        text = (DAO_DIR / dao_name).read_text(encoding="utf-8")
        referenced = set(reader.findall(text)) | set(writer.findall(text))
        unknown = referenced - columns_by_table[table]
        check(f"{dao_name} only touches real {table} columns", not unknown, str(sorted(unknown)))

    print("\n== 4. behaviour ==")
    connection.execute(
        "INSERT INTO meetings (id, title, day, created_at, updated_at) VALUES (7, 'Kickoff', '2026-09-03', 1, 1)"
    )
    connection.execute(
        "INSERT INTO tasks (id, title, priority, status, due_date, meeting_id, created_at, updated_at)"
        " VALUES (1, 'Send recap', 2, 'OPEN', '2026-09-01', 7, 1, 1)"
    )
    connection.execute(
        "INSERT INTO tasks (id, title, priority, status, created_at, updated_at)"
        " VALUES (2, 'Old thing', 1, 'DONE', 1, 1)"
    )
    connection.execute(
        "INSERT INTO tasks (id, title, priority, status, due_date, created_at, updated_at)"
        " VALUES (3, 'Ship export', 3, 'OPEN', '2026-09-05', 1, 1)"
    )
    connection.commit()

    linked = connection.execute("SELECT COUNT(*) FROM tasks WHERE meeting_id = 7").fetchone()[0]
    check("action item is linked to its meeting", linked == 1, str(linked))

    connection.execute("DELETE FROM meetings WHERE id = 7")
    row = connection.execute("SELECT meeting_id, title FROM tasks WHERE id = 1").fetchone()
    check("deleting a meeting keeps the task", row is not None)
    check("deleting a meeting nulls meeting_id (ON DELETE SET NULL)", row is not None and row[0] is None, str(row))

    order_sql = next(
        s for s in sql_literals(DAO_DIR / "TaskDao.kt")
        if s.upper().startswith("SELECT * FROM TASKS") and "ORDER BY" in s.upper() and "?" not in s
    )
    ordered = [r[0] for r in connection.execute(order_sql.replace("SELECT *", "SELECT id"))]
    check("open tasks sort before closed ones", ordered == [1, 3, 2], str(ordered))

    print(f"\n{checks - len(failures)}/{checks} checks passed")
    if failures:
        print("FAILED:")
        for name in failures:
            print(f"  - {name}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
