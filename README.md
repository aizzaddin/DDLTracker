# DDL Change Tracker

An IntelliJ Platform plugin that automatically intercepts DDL statements executed in the IDE database console, writes them as `.sql` files, and commits them to a Git repository — giving your team a full audit trail of every database schema change.

---

## Requirements

| | |
|---|---|
| **IDE** | IntelliJ IDEA Ultimate 2024.1+ or DataGrip 2024.1+ |
| **JVM** | 17 |
| **Git repository** | A local Git repo (with optional remote) dedicated to DDL tracking |

> The plugin requires the **DatabaseTools** plugin (`com.intellij.database`), which is bundled in IDEA Ultimate and DataGrip. It does **not** work in IntelliJ IDEA Community.

---

## Build

```bash
./gradlew buildPlugin
```

Output: `build/distributions/DDLTrack-1.0.0.zip`

---

## Installation

1. Open **Settings → Plugins**
2. Click the gear icon → **Install Plugin from Disk…**
3. Select `DDLTrack-1.0.0.zip`
4. Restart the IDE when prompted

---

## Setup

Open **Settings → Tools → DDL Change Tracker**.

| Field | Description |
|---|---|
| **Git repository path** | Absolute path to your local DDL tracking repo (must contain a `.git` folder) |
| **Remote URL** | Remote URL for push (HTTPS or SSH). Leave blank to commit locally only |
| **Active branch** | Branch to commit DDL changes to (e.g. `main`, `feature/sprint-42`) |
| **Auto-commit on DDL** | Automatically stage and commit each DDL change as it happens (default: on) |
| **Auto-push after commit** | Push to remote after every commit (default: off) |
| **Excluded schemas** | Comma-separated schema names to ignore (default: `SYS,SYSTEM,DBSNMP`) |
| **Tracked datasources** | Check the datasources you want to track. Leave all unchecked to track every datasource |

Click **Refresh** next to the datasource list to reload connections from the current project's Database panel.

### ⚠️ Branch must match before using the plugin
![DDL Tracker Setup](./img/DDL%20Tracker%20Setup.png)
The plugin commits to whichever branch the Git repository is **currently checked out to** — it does not switch branches automatically. The **Active branch** field is used only for display (notifications, commit status). If the repo is on the wrong branch, DDL changes will land there instead.

**Before you start tracking, verify the repo is on the correct branch:**

```bash
# Check current branch
git -C /path/to/ddl-repo branch --show-current

# Switch to the target branch if it already exists
git -C /path/to/ddl-repo checkout main

# Or create and switch to a new branch
git -C /path/to/ddl-repo checkout -b feature/sprint-42
```

Make sure the branch shown by `branch --show-current` matches exactly what you typed in the **Active branch** field in settings. Do this every time you change the configured branch.

### Minimal setup (local only)

1. Create a Git repo: `git init ~/ddl-audit && cd ~/ddl-audit && git commit --allow-empty -m "init"`
2. Set **Git repository path** to `~/ddl-audit`
3. Enable **Auto-commit on DDL**
4. Click **OK**

---

## Usage

Run any DDL statement in the database console as you normally would. The plugin:

1. Detects the DDL and extracts schema, object name, and action type
2. Writes a `.sql` file under `migrations/{SCHEMA}/`
3. Commits it to the configured branch (if auto-commit is on)
4. Pushes to remote with `pull --rebase` before push, retrying up to 3 times (if auto-push is on)

### Tool Window

The **DDL Tracker** panel at the bottom of the IDE shows every change captured in the current session:

| Column | Description |
|---|---|
| Timestamp | When the statement was executed |
| User | OS username |
| Datasource | Connection name as configured in the IDE |
| Schema | Target schema |
| Action Type | e.g. `CREATE_TABLE`, `ALTER_TABLE`, `DROP_INDEX` |
| Object | Object name |
| Status | `PENDING` / `COMMITTED` / `FAILED` |

Click any row to see the full SQL in the preview pane below.

---

## Tracked DDL statements

| Statement | Action type |
|---|---|
| `CREATE TABLE` | `CREATE_TABLE` |
| `CREATE INDEX` | `CREATE_INDEX` |
| `CREATE VIEW` | `CREATE_VIEW` |
| `CREATE SEQUENCE` | `CREATE_SEQUENCE` |
| `CREATE SYNONYM` | `CREATE_SYNONYM` |
| `CREATE OR REPLACE …` | `CREATE_OR_REPLACE` |
| `ALTER TABLE` | `ALTER_TABLE` |
| `ALTER INDEX` | `ALTER_INDEX` |
| `DROP TABLE` | `DROP_TABLE` |
| `DROP INDEX` | `DROP_INDEX` |
| `DROP VIEW` | `DROP_VIEW` |
| `DROP SEQUENCE` | `DROP_SEQUENCE` |
| `TRUNCATE TABLE` | `TRUNCATE_TABLE` |
| `RENAME` | `RENAME` |

DML (`INSERT`, `UPDATE`, `DELETE`) and `SELECT` are ignored.

---

## Output format

### File path

```
{repo_root}/migrations/{SCHEMA}/{yyyyMMdd_HHmmss}__{ACTION_TYPE}_{OBJECT}.sql
```

Example: `migrations/HR/20260503_142200__ALTER_TABLE_EMPLOYEES.sql`

### File content

```sql
-- DDL Change Tracker
-- Timestamp  : 2026-05-03T14:22:00
-- User       : wildan
-- Datasource : ORACLE_DEV
-- Schema     : HR

ALTER TABLE HR.EMPLOYEES ADD (DEPT_ID NUMBER(10));
```

### Commit message

```
[ALTER TABLE] HR.EMPLOYEES — wildan @ 2026-05-03T14:22:00
```

### Repository layout

```
ddl-audit/
└── migrations/
    ├── HR/
    │   ├── 20260503_142200__ALTER_TABLE_EMPLOYEES.sql
    │   └── 20260503_150310__CREATE_INDEX_IDX_EMP_DEPT.sql
    ├── PAYROLL/
    │   └── 20260504_091500__CREATE_TABLE_SALARY_HISTORY.sql
    └── FINANCE/
        └── 20260504_110045__DROP_TABLE_TEMP_LEDGER.sql
```

---

## Project structure

```
DDLTrack/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── src/main/
    ├── kotlin/com/wildanaizzaddin/ddltracker/
    │   ├── DDLTrackerPlugin.kt                 # StartupActivity — registers DataAuditor
    │   ├── listener/
    │   │   └── DDLQueryListener.kt             # DataAuditor — fires after each SQL statement
    │   ├── model/
    │   │   └── DDLChange.kt                    # Change record data class
    │   ├── service/
    │   │   ├── DDLFilterService.kt             # DDL detection and parsing
    │   │   ├── FileWriterService.kt            # Writes .sql files with header comment
    │   │   └── GitCommitService.kt             # JGit commit + pull-rebase + push
    │   ├── settings/
    │   │   ├── DDLTrackerSettings.kt           # PersistentStateComponent (app-level)
    │   │   └── DDLTrackerSettingsUI.kt         # Settings panel (projectConfigurable)
    │   └── ui/
    │       ├── DDLTrackerToolWindowFactory.kt
    │       └── DDLTrackerToolWindow.kt         # Table + SQL preview pane
    └── resources/META-INF/
        └── plugin.xml
```

---

## Notes

- Git operations run on a background thread — the IDE is never blocked.
- The plugin uses **JGit** (no dependency on a system `git` binary).
- Settings are stored in `ddl-tracker.xml` under the application config directory.
- Schema filtering (`Excluded schemas`) is case-insensitive.
- Datasource filtering: if no datasources are checked, all datasources are tracked.
