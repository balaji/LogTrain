package dev.balaji.moonrose.logtrain.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Rebuilds a version-11 database file from the committed schema JSON, then
 * opens it with the real migrations and NO destructive fallback. If a
 * migration's SQL doesn't match the exported schema, Room's identity-hash
 * validation fails here instead of silently wiping user data in production
 * (where the pre-v9 fallback is still installed).
 */
class MigrationTest {
    private val dbFile = File.createTempFile("logtrain-migration-test", ".db")

    @AfterTest
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun `a v11 database migrates to the current version without data loss`() {
        val schema = schemaJson(11)
        createDatabaseFromSchema(schema)

        val db = Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
            .build()
        try {
            runBlocking {
                // Probe row seeded at v11 survives the migration.
                val profile = assertNotNull(db.profileDao().observe().first())
                assertEquals("Probe", profile.name)

                // The migrated table is usable.
                db.aiInsightCacheDao().upsert(AiInsightCacheEntity("weekly_volume", "abc", "Text.", 1))
                assertEquals("Text.", db.aiInsightCacheDao().find("weekly_volume")?.text)
            }
        } finally {
            db.close()
        }
    }

    private fun schemaJson(version: Int): String {
        val path = "schemas/dev.balaji.moonrose.logtrain.db.AppDatabase/$version.json"
        val file = sequenceOf(File(path), File("sharedLogic/$path"))
            .firstOrNull(File::exists)
            ?: error("Committed schema $path not found from ${File(".").absolutePath}")
        return file.readText()
    }

    /** Executes every createSql from the schema JSON plus Room's master-table setup. */
    private fun createDatabaseFromSchema(schema: String) {
        val version = Regex("\"version\"\\s*:\\s*(\\d+)").find(schema)!!.groupValues[1].toInt()
        val identityHash = Regex("\"identityHash\"\\s*:\\s*\"([0-9a-f]+)\"").find(schema)!!.groupValues[1]
        val connection = BundledSQLiteDriver().open(dbFile.absolutePath)
        try {
            for (statement in createStatements(schema)) connection.execSQL(statement)
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            )
            connection.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$identityHash')",
            )
            connection.execSQL("PRAGMA user_version = $version")
            connection.execSQL(
                "INSERT INTO profile (id, name, age, gender, weight, height, created_at, updated_at) " +
                    "VALUES (1, 'Probe', 30, NULL, 80.0, NULL, 1, 1)",
            )
        } finally {
            connection.close()
        }
    }

    /**
     * Pulls every entity/index createSql out of the machine-generated schema
     * JSON, substituting the ${'$'}{TABLE_NAME} placeholder with the entity's
     * tableName (which always precedes its createSql statements).
     */
    private fun createStatements(schema: String): List<String> {
        val pair = Regex("\"(tableName|createSql)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val statements = mutableListOf<String>()
        var tableName: String? = null
        for (match in pair.findAll(schema)) {
            val (key, raw) = match.destructured
            val value = raw.replace("\\\"", "\"").replace("\\\\", "\\")
            when (key) {
                "tableName" -> tableName = value
                "createSql" -> statements += value.replace("\${TABLE_NAME}", checkNotNull(tableName))
            }
        }
        check(statements.isNotEmpty()) { "No createSql statements found in schema JSON" }
        return statements
    }
}
