package com.wildanaizzaddin.ddltracker.service

import com.wildanaizzaddin.ddltracker.model.DDLChange
import java.time.LocalDateTime

object DDLFilterService {

    // Order matters: more specific patterns first (CREATE OR REPLACE before plain CREATE)
    private val PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("""(?i)^\s*CREATE\s+OR\s+REPLACE\s+\w+\s+(\S+)""") to "CREATE_OR_REPLACE",
        Regex("""(?i)^\s*CREATE\s+TABLE\s+(\S+)""")              to "CREATE_TABLE",
        Regex("""(?i)^\s*CREATE\s+INDEX\s+(\S+)""")              to "CREATE_INDEX",
        Regex("""(?i)^\s*CREATE\s+SEQUENCE\s+(\S+)""")           to "CREATE_SEQUENCE",
        Regex("""(?i)^\s*CREATE\s+VIEW\s+(\S+)""")               to "CREATE_VIEW",
        Regex("""(?i)^\s*CREATE\s+SYNONYM\s+(\S+)""")            to "CREATE_SYNONYM",
        Regex("""(?i)^\s*ALTER\s+TABLE\s+(\S+)""")               to "ALTER_TABLE",
        Regex("""(?i)^\s*ALTER\s+INDEX\s+(\S+)""")               to "ALTER_INDEX",
        Regex("""(?i)^\s*DROP\s+TABLE\s+(\S+)""")                to "DROP_TABLE",
        Regex("""(?i)^\s*DROP\s+INDEX\s+(\S+)""")                to "DROP_INDEX",
        Regex("""(?i)^\s*DROP\s+SEQUENCE\s+(\S+)""")             to "DROP_SEQUENCE",
        Regex("""(?i)^\s*DROP\s+VIEW\s+(\S+)""")                 to "DROP_VIEW",
        Regex("""(?i)^\s*TRUNCATE\s+TABLE\s+(\S+)""")            to "TRUNCATE_TABLE",
        Regex("""(?i)^\s*RENAME\s+(\S+)""")                      to "RENAME",
    )

    fun isDDL(sql: String): Boolean = PATTERNS.any { (regex, _) -> regex.containsMatchIn(sql) }

    fun parse(sql: String, datasource: String, activeSchema: String): DDLChange? {
        val normalized = sql.trim().replace(Regex("""\s+"""), " ")
        for ((pattern, actionType) in PATTERNS) {
            val match = pattern.find(normalized) ?: continue
            val rawObject = match.groupValues[1].uppercase().trimEnd(';', ' ')
            val (schema, objectName) = if ('.' in rawObject) {
                rawObject.substringBefore('.') to rawObject.substringAfter('.')
            } else {
                activeSchema.uppercase() to rawObject
            }
            return DDLChange(
                sql = normalized,
                timestamp = LocalDateTime.now(),
                user = System.getProperty("user.name") ?: "unknown",
                datasource = datasource,
                schema = schema.replace(Regex("[\\[\\]]"), ""),
                objectName = objectName,
                actionType = actionType
            )
        }
        return null
    }
}
