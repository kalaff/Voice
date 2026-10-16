@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.playbackScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import voice.core.ui.icons.VoiceIcons
import voice.features.playbackScreen.view.RollingText
import java.text.NumberFormat
import voice.core.strings.R as StringsR

private const val MAX_SKIP_SECONDS = 60
private val SKIP_PRESETS = listOf(0, 5, 10, 15, 20)

@Composable
internal fun SkipIntroSheet(
  dialogState: BookPlayDialogViewState.SkipIntroDialog,
  onSkipSecondsChange: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val skipSeconds = dialogState.introSkipSeconds
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberBottomSheetState(
      initialValue = Hidden,
      enabledValues = setOf(Hidden, Expanded),
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(StringsR.string.playback_skip_intro_title),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(16.dp))
      RollingText(
        text = secondsText(skipSeconds),
        style = MaterialTheme.typography.displayLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.height(16.dp))
      val decrease = stringResource(StringsR.string.sleep_timer_dialog_action_decrement)
      val increase = stringResource(StringsR.string.sleep_timer_dialog_action_increment)
      val title = stringResource(StringsR.string.playback_skip_intro_title)
      Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
          onClick = { onSkipSecondsChange((skipSeconds - 1).coerceAtLeast(0)) },
          shapes = IconButtonDefaults.shapes(),
          enabled = skipSeconds > 0,
        ) {
          Icon(
            imageVector = VoiceIcons.Remove,
            contentDescription = "$decrease, $title",
          )
        }
        val sliderState = rememberSliderState(
          value = skipSeconds.toFloat(),
          trackRange = 0F..MAX_SKIP_SECONDS.toFloat(),
        )
        SideEffect { sliderState.value = skipSeconds.toFloat() }
        Slider(
          modifier = Modifier
            .weight(1F)
            .padding(horizontal = 12.dp),
          state = sliderState,
          onValueChange = { onSkipSecondsChange(it.toInt().coerceIn(0, MAX_SKIP_SECONDS)) },
        )
        FilledTonalIconButton(
          onClick = { onSkipSecondsChange((skipSeconds + 1).coerceAtMost(MAX_SKIP_SECONDS)) },
          shapes = IconButtonDefaults.shapes(),
          enabled = skipSeconds < MAX_SKIP_SECONDS,
        ) {
          Icon(
            imageVector = VoiceIcons.Add,
            contentDescription = "$increase, $title",
          )
        }
      }
      Spacer(Modifier.height(16.dp))
      val numberFormat = remember { NumberFormat.getIntegerInstance() }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
      ) {
        SKIP_PRESETS.forEachIndexed { index, preset ->
          ToggleButton(
            checked = skipSeconds == preset,
            onCheckedChange = { onSkipSecondsChange(preset) },
            modifier = Modifier
              .weight(1F)
              .semantics { role = Role.RadioButton },
            shapes = when (index) {
              0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
              SKIP_PRESETS.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
              else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
          ) {
            Text(
              text = if (preset == 0) {
                stringResource(StringsR.string.playback_skip_intro_off)
              } else {
                numberFormat.format(preset)
              },
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun secondsText(seconds: Int): String {
  return if (seconds == 0) {
    stringResource(StringsR.string.playback_skip_intro_off)
  } else {
    pluralStringResource(StringsR.plurals.duration_seconds, seconds, seconds)
  }
}
