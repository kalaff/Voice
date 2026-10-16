package voice.features.settings.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The listener's progress and bookmarks in one file, so a new device can start where the old one left off. Books are
 * known by their [voice.core.data.syncKey], because their ids differ on every device.
 */
@Serializable
internal data class BackupFile(
  val version: Int = CURRENT_VERSION,
  val books: List<BackupBook>,
) {
  companion object {
    const val CURRENT_VERSION = 1
  }
}

/**
 * @param currentChapterIndex the index of the chapter in the book's file list, because chapter ids are device local
 */
@Serializable
internal data class BackupBook(
  val syncKey: String,
  val name: String,
  val currentChapterIndex: Int,
  val positionInChapter: Long,
  val lastPlayedAt: Long,
  val playbackSpeed: Float,
  val bookmarks: List<BackupBookmark> = emptyList(),
)

@Serializable
internal data class BackupBookmark(
  val id: String,
  val chapterIndex: Int,
  val time: Long,
  val title: String? = null,
  val addedAt: Long,
  val kind: String,
)

private val json = Json { ignoreUnknownKeys = true }

internal fun BackupFile.encode(): String = json.encodeToString(this)

// a file the listener picked can be anything, so a broken one just fails
internal fun parseBackupFile(text: String): BackupFile? = try {
  json.decodeFromString<BackupFile>(text).takeIf { it.version == BackupFile.CURRENT_VERSION }
} catch (_: IllegalArgumentException) {
  null
}
