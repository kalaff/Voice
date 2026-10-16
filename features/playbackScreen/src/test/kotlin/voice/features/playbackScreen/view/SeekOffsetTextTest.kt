package voice.features.playbackScreen.view

import kotlin.test.Test
import kotlin.test.assertEquals

class SeekOffsetTextTest {

  @Test
  fun `a jump forward is signed plus`() {
    assertEquals(expected = "+00:45", actual = seekOffsetText(offsetMs = 45_000, durationMs = 600_000))
  }

  @Test
  fun `a jump backward is signed minus`() {
    assertEquals(expected = "−0:00:45", actual = seekOffsetText(offsetMs = -45_000, durationMs = 3_600_000))
  }

  @Test
  fun `a drag in place shows no offset`() {
    assertEquals(expected = "+0:00", actual = seekOffsetText(offsetMs = 0, durationMs = 60_000))
  }

  @Test
  fun `the width follows the book length`() {
    assertEquals(expected = "+1:30", actual = seekOffsetText(offsetMs = 90_000, durationMs = 90_000))
  }
}
