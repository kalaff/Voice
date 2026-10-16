package voice.features.settings.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.BookMetadata
import voice.core.data.Bookmark
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookRepository
import voice.core.data.repo.BookmarkRepo
import voice.core.data.syncKey
import voice.core.playback.PlayerController
import voice.features.settings.MemoryDataStore
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ProgressBackupTest {

  private val bookId = BookId("content://book")
  private val chapterId = ChapterId("file:///Audiobooks/1.mp3")
  private val book = book()

  private val bookRepository = mockk<BookRepository> {
    coEvery { all() } returns listOf(book)
    coEvery { get(book.id) } returns book
    coEvery { updateBook(book.id, any()) } returns Unit
  }
  private val bookmarkRepo = mockk<BookmarkRepo> {
    coEvery { bookmarks(any()) } returns emptyList()
    coEvery { addBookmark(any()) } returns Unit
  }
  private val currentBookStore = MemoryDataStore<BookId?>(null)
  private val playerController = mockk<PlayerController>(relaxed = true)
  private val backup = ProgressBackup(bookRepository, bookmarkRepo, currentBookStore, playerController)

  @Test
  fun `export names the book and its progress`() = runTest {
    val text = backup.exportText()

    val parsed = parseBackupFile(text)!!
    assertEquals(expected = 1, actual = parsed.books.size)
    assertEquals(expected = book.content.name, actual = parsed.books[0].name)
    assertEquals(expected = book.chapters.syncKey(), actual = parsed.books[0].syncKey)
    assertEquals(expected = 3_000L, actual = parsed.books[0].positionInChapter)
  }

  @Test
  fun `a book without file sizes is left out of the export`() = runTest {
    val sizelessChapter = chapter(id = ChapterId("file:///Audiobooks/x.mp3"), fileSize = 0)
    val sizeless = book.copy(
      content = book.content.copy(
        id = BookId("content://sizeless"),
        chapters = listOf(sizelessChapter.id),
        currentChapter = sizelessChapter.id,
        positionInChapter = 0,
      ),
      chapters = listOf(sizelessChapter),
    )
    coEvery { bookRepository.all() } returns listOf(book, sizeless)

    val parsed = parseBackupFile(backup.exportText())!!

    assertEquals(expected = listOf(book.content.name), actual = parsed.books.map { it.name })
  }

  @Test
  fun `import restores the progress of a matching book`() = runTest {
    val text = backup.exportText()

    val result = backup.import(text)

    assertIs<ProgressBackup.ImportResult.Done>(result)
    assertEquals(expected = 1, actual = result.books)
    assertEquals(expected = emptyList(), actual = result.notFound)
    val update = slot<(BookContent) -> BookContent>()
    coVerify { bookRepository.updateBook(book.id, capture(update)) }
    val content = update.captured(book.content)
    assertEquals(expected = chapterId, actual = content.currentChapter)
    assertEquals(expected = 3_000L, actual = content.positionInChapter)
  }

  @Test
  fun `a book that is not here is listed and skipped`() = runTest {
    val parsed = parseBackupFile(backup.exportText())!!
    val foreign = parsed.copy(books = parsed.books.map { it.copy(name = "Dune", syncKey = "other") })

    val result = backup.import(foreign.encode())

    assertIs<ProgressBackup.ImportResult.Done>(result)
    assertEquals(expected = 0, actual = result.books)
    assertEquals(expected = listOf("Dune"), actual = result.notFound)
  }

  @Test
  fun `a backup chapter that does not exist here is skipped`() = runTest {
    val parsed = parseBackupFile(backup.exportText())!!
    val broken = parsed.copy(books = parsed.books.map { it.copy(currentChapterIndex = 9) })

    val result = backup.import(broken.encode())

    assertIs<ProgressBackup.ImportResult.Done>(result)
    assertEquals(expected = listOf(book.content.name), actual = result.notFound)
  }

  @Test
  fun `a file that is not a backup is unreadable`() = runTest {
    assertIs<ProgressBackup.ImportResult.Unreadable>(backup.import("not json"))
    assertIs<ProgressBackup.ImportResult.Unreadable>(backup.import("""{"version":99,"books":[]}"""))
  }

  @Test
  fun `bookmarks come back with their book and chapter`() = runTest {
    val id = Uuid.random()
    val parsed = parseBackupFile(backup.exportText())!!
    val withBookmark = parsed.copy(
      books = parsed.books.map {
        it.copy(
          bookmarks = listOf(
            BackupBookmark(
              id = id.toString(),
              chapterIndex = 0,
              time = 1_500,
              title = "The good part",
              addedAt = 1_700_000_000_000,
              kind = "Quote",
            ),
            BackupBookmark(
              id = "not-a-uuid",
              chapterIndex = 0,
              time = 100,
              addedAt = 0,
              kind = "Note",
            ),
          ),
        )
      },
    )
    val saved = slot<Bookmark>()
    coEvery { bookmarkRepo.addBookmark(capture(saved)) } returns Unit

    val result = backup.import(withBookmark.encode())

    assertIs<ProgressBackup.ImportResult.Done>(result)
    assertEquals(expected = 1, actual = result.bookmarks)
    assertEquals(expected = Bookmark.Id(id), actual = saved.captured.id)
    assertEquals(expected = Bookmark.Kind.Quote, actual = saved.captured.kind)
    assertEquals(expected = chapterId, actual = saved.captured.chapterId)
    assertEquals(expected = 1_500L, actual = saved.captured.time)
  }

  @Test
  fun `the open book follows into the restored position`() = runTest {
    currentBookStore.updateData { book.id }

    val result = backup.import(backup.exportText())

    assertIs<ProgressBackup.ImportResult.Done>(result)
    coVerify { playerController.setPosition(time = 3_000, id = chapterId, any(), any()) }
  }

  private fun chapter(
    id: ChapterId,
    fileSize: Long = 100,
  ) = Chapter(
    id = id,
    name = "Chapter",
    duration = 10_000,
    fileLastModified = Instant.EPOCH,
    markData = emptyList(),
    fileSize = fileSize,
  )

  private fun book() = Book(
    content = BookContent(
      id = bookId,
      playbackSpeed = 1.25F,
      skipSilence = false,
      isActive = true,
      lastPlayedAt = Instant.ofEpochMilli(1_700_000_000_000),
      addedAt = Instant.EPOCH,
      chapters = listOf(chapterId),
      currentChapter = chapterId,
      positionInChapter = 3_000,
      gain = 0F,
      metadata = BookMetadata(
        name = "Dune",
        author = null,
        album = null,
        title = null,
        genre = null,
        narrator = null,
        series = null,
        part = null,
        cover = null,
        fileLastModified = null,
      ),
      customName = null,
      customCover = null,
    ),
    chapters = listOf(chapter(id = chapterId)),
  )
}
