package voice.core.playback

import voice.core.data.Book
import java.time.Instant

/** The speeds the quick controls offer; the notification cycle button walks the same list. */
val PLAYBACK_SPEED_PRESETS = listOf(0.75F, 1F, 1.25F, 1.5F, 2F)

/**
 * A book that was never played has no speed of its own yet, so it starts at [speed], the one the listener last
 * listened at. [speed] is only read for such a book. The player keeps it as the book's own speed once it loads the
 * book.
 */
inline fun Book.withDefaultSpeed(speed: () -> Float): Book {
  // books from before lastPlayedAt existed have none but can have a speed of their own
  return if (content.lastPlayedAt == Instant.EPOCH && content.playbackSpeed == 1F) {
    update { it.copy(playbackSpeed = speed()) }
  } else {
    this
  }
}

/** With the global speed override on, every book shows and plays at that one speed instead of its own. */
inline fun Book.withEffectiveSpeed(
  overrideEnabled: Boolean,
  overrideSpeed: Float,
  defaultSpeed: () -> Float,
): Book {
  if (overrideEnabled) return update { it.copy(playbackSpeed = overrideSpeed) }
  return withDefaultSpeed(defaultSpeed)
}

/** The next preset after [current], wrapping to the first; 1.3 for example moves on to 1.5. */
fun nextPlaybackSpeed(current: Float): Float {
  return PLAYBACK_SPEED_PRESETS.firstOrNull { it > current } ?: PLAYBACK_SPEED_PRESETS.first()
}
