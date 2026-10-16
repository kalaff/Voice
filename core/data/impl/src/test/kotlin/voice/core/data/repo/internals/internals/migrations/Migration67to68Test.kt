package voice.core.data.repo.internals.internals.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.runner.RunWith
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.allMigrations
import voice.core.data.repo.internals.getString
import voice.core.data.repo.internals.mapRows
import kotlin.test.Test
import kotlin.test.assertEquals

private const val DB_NAME = "testDb"

@RunWith(AndroidJUnit4::class)
class Migration67to68Test {

  @Rule
  @JvmField
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDb::class.java,
  )

  @Test
  fun `books start with no intro skip after the migration`() {
    val migrated = migrate {
      insert(book(id = "content://book"))
    }

    val rows = migrated.query("SELECT id, introSkipSeconds FROM content2")
      .mapRows { listOf(getString("id"), getString("introSkipSeconds")) }

    assertEquals(expected = listOf(listOf("content://book", "0")), actual = rows)
  }

  private fun migrate(atVersion67: SupportSQLiteDatabase.() -> Unit): SupportSQLiteDatabase {
    helper.createDatabase(DB_NAME, 67).use(atVersion67)
    return helper.runMigrationsAndValidate(DB_NAME, 68, true, *allMigrations())
  }

  private fun SupportSQLiteDatabase.insert(values: ContentValues) {
    insert("content2", SQLiteDatabase.CONFLICT_FAIL, values)
  }

  private fun book(id: String) = ContentValues().apply {
    put("id", id)
    put("playbackSpeed", 1F)
    put("skipSilence", 0)
    put("isActive", 1)
    put("lastPlayedAt", "1970-01-01T00:00:00Z")
    put("name", "Dune")
    put("addedAt", "1970-01-01T00:00:00Z")
    put("chapters", "[\"$id/1.mp3\"]")
    put("currentChapter", "$id/1.mp3")
    put("positionInChapter", 0L)
    put("gain", 0F)
  }
}
