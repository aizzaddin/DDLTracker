# DDL Change Tracker — IntelliJ/DataGrip Plugin Requirements

## Overview

An IntelliJ Platform plugin that automatically intercepts DDL statements executed by any user in IntelliJ IDEA or DataGrip, formats them, writes them as `.sql` files, and commits them to a shared Git repository. The goal is to provide a full audit trail of database structural changes across the team.

---

## Target Environment

- **IDE**: IntelliJ IDEA and/or DataGrip (IntelliJ Platform SDK)
- **Language**: Kotlin + Gradle (IntelliJ Plugin conventions)
- **Target Database**: Oracle
- **Git Library**: JGit (no dependency on system `git` binary)
- **Git Strategy**: Shared remote repository, `pull --rebase` before each push to handle multi-user concurrency

---

## Functional Requirements

### FR-01 — DDL Interception

- The plugin MUST intercept all SQL statements executed via the IDE's database console or query runner
- Only DDL statements MUST be captured; DML (INSERT, UPDATE, DELETE) and SELECT MUST be ignored
- DDL keywords to capture (Oracle dialect):
    - `CREATE TABLE`, `CREATE INDEX`, `CREATE SEQUENCE`, `CREATE VIEW`, `CREATE SYNONYM`
    - `CREATE OR REPLACE` (procedure, function, trigger, package)
    - `ALTER TABLE`, `ALTER INDEX`
    - `DROP TABLE`, `DROP INDEX`, `DROP SEQUENCE`, `DROP VIEW`
    - `RENAME`
    - `TRUNCATE TABLE`
- Interception MUST work for both single-statement and multi-statement script executions
- Each DDL statement MUST be captured as an individual change record

### FR-02 — Change Record

Each captured DDL change MUST produce a record containing:

| Field | Description |
|---|---|
| `sql` | The raw DDL SQL (trimmed, normalized whitespace) |
| `timestamp` | Local `LocalDateTime` at time of execution |
| `user` | OS username (`System.getProperty("user.name")`) |
| `datasource` | Name of the datasource/connection as configured in the IDE |
| `schema` | Schema name extracted from the SQL or from the active connection |
| `objectName` | Table/index/sequence name extracted from the SQL |
| `actionType` | e.g., `CREATE_TABLE`, `ALTER_TABLE`, `DROP_INDEX` |

### FR-03 — File Writer

- Each DDL change MUST be written to a `.sql` file before committing
- File path pattern:
  ```
  {git_repo_root}/migrations/{schema}/{yyyyMMdd_HHmmss}__{ACTION_TYPE}_{OBJECT_NAME}.sql
  ```
  Example: `migrations/HR/20260503_142200__ALTER_TABLE_EMPLOYEES.sql`
- File MUST include a header comment block:
  ```sql
  -- DDL Change Tracker
  -- Timestamp  : 2026-05-03T14:22:00
  -- User       : wildan
  -- Datasource : ORACLE_DEV
  -- Schema     : HR
  
  ALTER TABLE HR.EMPLOYEES ADD (DEPT_ID NUMBER(10));
  ```
- Directories MUST be created automatically if they do not exist

### FR-04 — Git Commit

- After writing the file, the plugin MUST stage and commit it using JGit
- Commit message format:
  ```
  [ALTER TABLE] HR.EMPLOYEES — wildan @ 2026-05-03T14:22:00
  ```
- Git author MUST use the OS username and a placeholder email: `{username}@local`
- If a remote is configured, the plugin MUST:
    1. `pull --rebase` before pushing
    2. Retry up to 3 times with exponential backoff (500ms, 1000ms, 2000ms) on `TransportException`
    3. If rebase conflict occurs (unlikely given unique filenames): abort rebase, retain the local `.sql` file, and notify the user via IDE notification
    4. If all retries fail: save the file locally and notify the user that push failed

### FR-05 — Branch Selection

- The plugin MUST allow the user to select the active Git branch per datasource/connection
- Branch name convention: matches the ongoing project name (e.g., `feature/approval-batch-phase2`)
- On plugin startup or first DDL capture, if no branch is configured, the plugin MUST prompt the user to select or enter a branch name
- The plugin MUST be able to:
    - List existing local and remote branches from the configured repository
    - Checkout an existing branch
    - Create and checkout a new branch (from `main`/`master` by default)
- Branch selection MUST be persisted per datasource in plugin settings

### FR-06 — Settings UI

The plugin MUST provide a Settings panel under **Settings → Tools → DDL Change Tracker** with the following fields:

| Field | Type | Description |
|---|---|---|
| Git Repository Path | Text field + Browse button | Absolute path to local Git repo directory |
| Remote URL | Text field | Optional. Remote repo URL (HTTPS or SSH) |
| Active Branch | Dropdown + New Branch button | Select or create branch; list fetched from repo |
| Auto-commit on DDL | Toggle (default: ON) | Disable to queue changes without committing |
| Auto-push after commit | Toggle (default: OFF) | Automatically push after each commit |
| Track all datasources | Toggle (default: ON) | OFF = only track selected datasources |
| Excluded schemas | Text field (comma-separated) | e.g., `SYS,SYSTEM,DBSNMP` |

Settings MUST be persisted using IntelliJ's `PersistentStateComponent` (stored in `ddl-tracker.xml`).

A **Test Connection** button MUST verify that:
- The given path contains a valid Git repository (`.git` directory exists)
- The selected branch exists or can be created

### FR-07 — Tool Window

- The plugin MUST provide a Tool Window panel (anchored at the bottom, labeled **DDL Tracker**)
- The Tool Window MUST display a table/list of recent DDL changes in the current session with columns:
    - Timestamp
    - User
    - Datasource
    - Schema
    - Action Type
    - Object Name
    - Commit Status (Committed / Pending / Failed)
- Clicking a row MUST show the full SQL in a preview pane below the table
- A **View in Git Log** button MUST open the IDE's built-in VCS log filtered to the DDL tracker repo path (if possible)
- A **Retry Push** button MUST be available for entries with `Failed` status

### FR-08 — Notifications

- On successful commit: show a balloon notification with commit message and branch name
- On push failure: show a sticky notification with error details and a **Retry** action
- On rebase conflict: show a sticky notification with instructions to resolve manually
- All notifications MUST use IntelliJ's `NotificationGroupManager` API

---

## Non-Functional Requirements

### NFR-01 — Performance

- DDL interception and file write MUST complete within 500ms
- Git commit MUST be executed on a background thread (not the EDT) using `ApplicationManager.getApplication().executeOnPooledThread()`
- Push (if enabled) MUST NOT block the IDE UI

### NFR-02 — Compatibility

- Plugin MUST be compatible with IntelliJ IDEA 2023.3+ and DataGrip 2023.3+
- IntelliJ Platform SDK version: `2024.1`
- Java/Kotlin target: JVM 17
- DatabaseTools plugin dependency MUST be declared in `plugin.xml`

### NFR-03 — Error Handling

- All Git operations MUST be wrapped in try-catch
- File I/O failures MUST be logged and surfaced to the user via notification
- The plugin MUST NOT throw uncaught exceptions that crash the IDE
- If `QueryExecutionListener` API is not available (e.g., non-DB IDE), the plugin MUST degrade gracefully

### NFR-04 — Security

- No credentials or passwords MUST be stored in plugin settings
- SSH key authentication and HTTPS token-based auth MUST be supported via the system's default Git credential store (JGit `CredentialsProvider` delegates to OS keychain)
- The plugin MUST NOT log SQL content at INFO level; use DEBUG only

---

## Project Structure

```
ddl-tracker-plugin/
├── build.gradle.kts
├── settings.gradle.kts
├── plugin.xml
└── src/
    └── main/
        └── kotlin/
            └── com/yourorg/ddltracker/
                ├── DDLTrackerPlugin.kt             # PostStartupActivity entry point
                ├── model/
                │   └── DDLChange.kt                # data class
                ├── listener/
                │   └── DDLQueryListener.kt         # QueryExecutionListener impl
                ├── service/
                │   ├── DDLFilterService.kt         # DDL detection + object name extraction
                │   ├── FileWriterService.kt        # .sql file writer
                │   └── GitCommitService.kt         # JGit wrapper (commit + push + rebase)
                ├── settings/
                │   ├── DDLTrackerSettings.kt       # PersistentStateComponent
                │   └── DDLTrackerSettingsUI.kt     # Settings panel (Swing/MigLayout)
                └── ui/
                    ├── DDLTrackerToolWindowFactory.kt
                    └── DDLTrackerToolWindow.kt     # Table + SQL preview
```

---

## Dependencies (`build.gradle.kts`)

```kotlin
plugins {
    id("org.jetbrains.intellij") version "1.17.0"
    kotlin("jvm") version "1.9.22"
}

intellij {
    version.set("2024.1")
    type.set("IC")
    plugins.set(listOf("DatabaseTools"))
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")
}
```

---

## `plugin.xml` Registration

```xml
<idea-plugin>
    <id>com.yourorg.ddl-tracker</id>
    <name>DDL Change Tracker</name>
    <version>1.0.0</version>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.database</depends>

    <extensions defaultExtensionNs="com.intellij">
        <postStartupActivity
            implementation="com.yourorg.ddltracker.DDLTrackerPlugin"/>

        <applicationConfigurable
            parentId="tools"
            instance="com.yourorg.ddltracker.settings.DDLTrackerSettingsUI"
            id="com.yourorg.ddltracker.settings"
            displayName="DDL Change Tracker"/>

        <applicationService
            serviceImplementation="com.yourorg.ddltracker.settings.DDLTrackerSettings"/>

        <toolWindow
            id="DDL Tracker"
            anchor="bottom"
            factoryClass="com.yourorg.ddltracker.ui.DDLTrackerToolWindowFactory"
            icon="/icons/ddl-tracker.svg"/>
    </extensions>
</idea-plugin>
```

---

## Git Repository Structure (Output)

```
{git_repo_root}/
├── .git/
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

## Out of Scope (v1)

- DML tracking (INSERT/UPDATE/DELETE)
- Schema diffing / reverse engineering from Oracle Data Dictionary
- Rollback/undo DDL generation
- Flyway/Liquibase migration file format output
- Web dashboard for audit log
- Slack/Teams notification integration