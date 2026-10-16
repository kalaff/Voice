package voice.core.playback

import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.playback.session.search.book
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultSpeedTest {

  private val book = book(
    chapters = listOf(
      Chapter(
        id = ChapterId("chapter"),
        name = "Chapter",
        duration = 10_000,
        fileLastModified = Instant.EPOCH,
        markData = emptyList(),
        fileSize = 0,
      ),
    ),
  )

  @Test
  fun `a book that was never played starts at the default speed`() {
    assertEquals(expected = 1.75F, actual = book.withDefaultSpeed { 1.75F }.content.playbackSpeed)
  }

  @Test
  fun `a book that was played keeps its own speed without reading the default`() {
    val played = book.update { it.copy(playbackSpeed = 1.25F, lastPlayedAt = Instant.ofEpochSecond(1)) }

    val result = played.withDefaultSpeed { error("the default speed is only read for a book that was never played") }

    assertEquals(expected = 1.25F, actual = result.content.playbackSpeed)
  }

  @Test
  fun `a book with a speed of its own but no last play keeps its speed`() {
    val legacy = book.update { it.copy(playbackSpeed = 1.25F) }

    assertEquals(expected = 1.25F, actual = legacy.withDefaultSpeed { 1.75F }.content.playbackSpeed)
  }

  @Test
  fun `with the override on, the global speed wins over the book's own`() {
    val played = book.update { it.copy(playbackSpeed = 1.25F, lastPlayedAt = Instant.ofEpochSecond(1)) }

    val result = played.withEffectiveSpeed(overrideEnabled = true, overrideSpeed = 1.5F) { 1.75F }

    assertEquals(expected = 1.5F, actual = result.content.playbackSpeed)
  }

  @Test
  fun `with the override off, the book falls back to its own and the default`() {
    val played = book.update { it.copy(playbackSpeed = 1.25F, lastPlayedAt = Instant.ofEpochSecond(1)) }

    assertEquals(
      expected = 1.25F,
      actual = played.withEffectiveSpeed(overrideEnabled = false, overrideSpeed = 1.5F) { 1.75F }.content.playbackSpeed,
    )
    assertEquals(
      expected = 1.75F,
      actual = book.withEffectiveSpeed(overrideEnabled = false, overrideSpeed = 1.5F) { 1.75F }.content.playbackSpeed,
    )
  }

  @Test
  fun `the cycle moves to the next preset and wraps at the end`() {
    assertEquals(expected = 1.25F, actual = nextPlaybackSpeed(1F))
    assertEquals(expected = 1.5F, actual = nextPlaybackSpeed(1.3F))
    assertEquals(expected = 0.75F, actual = nextPlaybackSpeed(2F))
    assertEquals(expected = 0.75F, actual = nextPlaybackSpeed(0.5F))
  }
}
