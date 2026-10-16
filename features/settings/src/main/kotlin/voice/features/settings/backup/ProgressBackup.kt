package voice.features.settings.backup

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.Bookmark
import voice.core.data.ChapterId
import voice.core.data.ListeningEvent
import voice.core.data.repo.BookRepository
import voice.core.data.repo.BookmarkRepo
import voice.core.data.store.CurrentBookStore
import voice.core.data.syncKey
import voice.core.playback.PlayerController
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Writes the listener's progress and bookmarks into a file and reads it back on any device that has the same books.
 */
@Inject
class ProgressBackup(
  private val bookRepository: BookRepository,
  private val bookmarkRepo: BookmarkRepo,
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
  private val playerController: PlayerController,
) {

  internal suspend fun exportText(): String {
    val books = bookRepository.all().mapNotNull { book ->
      val syncKey = book.chapters.syncKey() ?: return@mapNotNull null
      BackupBook(
        syncKey = syncKey,
        name = book.content.name,
        currentChapterIndex = book.content.currentChapterIndex,
        positionInChapter = book.content.positionInChapter,
        lastPlayedAt = book.content.lastPlayedAt.toEpochMilli(),
        playbackSpeed = book.content.playbackSpeed,
        bookmarks = bookmarkRepo.bookmarks(book.content).map { bookmark ->
          BackupBookmark(
            id = bookmark.id.value.toString(),
            chapterIndex = book.content.chapters.indexOf(bookmark.chapterId),
            time = bookmark.time,
            title = bookmark.title,
            addedAt = bookmark.addedAt.toEpochMilli(),
            kind = bookmark.kind.name,
          )
        },
      )
    }
    return BackupFile(books = books).encode()
  }

  internal suspend fun import(text: String): ImportResult {
    val backup = parseBackupFile(text) ?: return ImportResult.Unreadable
    val local = bookRepository.all().associateBy { it.chapters.syncKey() }
    var restoredBooks = 0
    var restoredBookmarks = 0
    val notFound = mutableListOf<String>()
    for (backupBook in backup.books) {
      val book = local[backupBook.syncKey]
      if (book == null) {
        notFound += backupBook.name
        continue
      }
      val chapter = book.chapters.getOrNull(backupBook.currentChapterIndex)
      if (chapter == null) {
        notFound += backupBook.name
        continue
      }
      bookRepository.updateBook(book.id) {
        it.copy(
          currentChapter = chapter.id,
          positionInChapter = backupBook.positionInChapter.coerceIn(0L, chapter.duration),
          lastPlayedAt = Instant.ofEpochMilli(backupBook.lastPlayedAt),
          playbackSpeed = backupBook.playbackSpeed,
        )
      }
      restoredBooks++
      restoredBookmarks += importBookmarks(book, backupBook)
      followLivePlayer(book.id, chapter.id, backupBook.positionInChapter)
    }
    return ImportResult.Done(
      books = restoredBooks,
      bookmarks = restoredBookmarks,
      notFound = notFound,
    )
  }

  private suspend fun importBookmarks(
    book: Book,
    backupBook: BackupBook,
  ): Int {
    var restored = 0
    for (backupBookmark in backupBook.bookmarks) {
      val chapter = book.chapters.getOrNull(backupBookmark.chapterIndex) ?: continue
      val uuid = try {
        Uuid.parse(backupBookmark.id)
      } catch (_: IllegalArgumentException) {
        continue
      }
      bookmarkRepo.addBookmark(
        Bookmark(
          bookId = book.id,
          chapterId = chapter.id,
          title = backupBookmark.title,
          time = backupBookmark.time.coerceIn(0L, chapter.duration),
          addedAt = Instant.ofEpochMilli(backupBookmark.addedAt),
          id = Bookmark.Id(uuid),
          kind = Bookmark.Kind.entries.firstOrNull { it.name == backupBookmark.kind } ?: Bookmark.Kind.Note,
        ),
      )
      restored++
    }
    return restored
  }

  /** The player flushes its own position on the next pause, so a restored book that is open has to move there too. */
  private suspend fun followLivePlayer(
    bookId: BookId,
    chapterId: ChapterId,
    positionInChapter: Long,
  ) {
    if (currentBookStoreId.data.first() != bookId) return
    playerController.setPosition(
      time = positionInChapter,
      id = chapterId,
      type = ListeningEvent.Type.Seek,
      source = ListeningEvent.Source.App,
    )
  }

  internal sealed interface ImportResult {
    data object Unreadable : ImportResult

    data class Done(
      val books: Int,
      val bookmarks: Int,
      val notFound: List<String>,
    ) : ImportResult
  }
}
