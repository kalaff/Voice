package voice.core.playback.session

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.playback.di.PlaybackScope
import kotlin.math.abs
import voice.core.strings.R as StringsR

/**
 * The notification and lock screen read their buttons from the session, so the speed button lives here to show the
 * current speed whenever playback speed changes.
 */
@SingleIn(PlaybackScope::class)
@Inject
class NotificationSpeedButton(private val context: Context) {

  private var session: MediaSession? = null
  private var speed: Float = 1F

  fun attach(session: MediaSession) {
    this.session = session
    update()
  }

  fun refresh(speed: Float) {
    this.speed = speed
    update()
  }

  private fun update() {
    session?.setMediaButtonPreferences(buttons())
  }

  private fun buttons(): List<CommandButton> {
    return listOf(
      CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
        .setDisplayName(context.getString(StringsR.string.playback_action_rewind))
        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
        .setSlots(CommandButton.SLOT_BACK)
        .build(),
      CommandButton.Builder(iconFor(speed))
        .setSessionCommand(SessionCommand(CustomCommand.CYCLE_SPEED_ACTION, Bundle.EMPTY))
        .setDisplayName(speedText(speed))
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .build(),
      CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
        .setDisplayName(context.getString(StringsR.string.playback_action_fast_forward))
        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
        .setSlots(CommandButton.SLOT_FORWARD)
        .build(),
    )
  }

  private fun speedText(speed: Float): String {
    val whole = speed == speed.toInt().toFloat()
    return (if (whole) speed.toInt().toString() else speed.toString()) + "×"
  }

  private fun iconFor(speed: Float): Int {
    val icons = listOf(
      0.5F to CommandButton.ICON_PLAYBACK_SPEED_0_5,
      0.8F to CommandButton.ICON_PLAYBACK_SPEED_0_8,
      1F to CommandButton.ICON_PLAYBACK_SPEED_1_0,
      1.2F to CommandButton.ICON_PLAYBACK_SPEED_1_2,
      1.5F to CommandButton.ICON_PLAYBACK_SPEED_1_5,
      1.8F to CommandButton.ICON_PLAYBACK_SPEED_1_8,
      2F to CommandButton.ICON_PLAYBACK_SPEED_2_0,
    )
    return icons.minBy { abs(speed - it.first) }.second
  }
}
