@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.settings.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.rootGraphAs
import voice.core.ui.OnboardingButton
import voice.core.ui.OnboardingScaffold
import voice.core.ui.OnboardingStep
import voice.core.ui.VoiceTheme
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import java.io.IOException
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface BackupGraph {
  val progressBackup: ProgressBackup
}

@BindingContainer
@ContributesTo(AppScope::class)
object BackupProvider {

  @Provides
  @IntoSet
  fun backupNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Backup> { key ->
    NavEntry(key) {
      BackupScreen()
    }
  }
}

@Composable
private fun BackupScreen() {
  val progressBackup = rootGraphAs<BackupGraph>().progressBackup
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf<BackupStatus>(BackupStatus.None) }

  val exportLauncher = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    busy = true
    scope.launch {
      val text = withContext(Dispatchers.IO) { progressBackup.exportText() }
      val written = withContext(Dispatchers.IO) { context.writeTo(uri, text) }
      busy = false
      status = if (written) BackupStatus.Exported else BackupStatus.Failed
    }
  }
  val importLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    busy = true
    scope.launch {
      val text = withContext(Dispatchers.IO) { context.readFrom(uri) }
      val result = if (text == null) {
        BackupStatus.Failed
      } else {
        when (val imported = withContext(Dispatchers.IO) { progressBackup.import(text) }) {
          ProgressBackup.ImportResult.Unreadable -> BackupStatus.Failed
          is ProgressBackup.ImportResult.Done -> BackupStatus.Imported(imported)
        }
      }
      busy = false
      status = result
    }
  }

  BackupScreen(
    busy = busy,
    status = status,
    onExport = { exportLauncher.launch("voice-backup.json") },
    onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
  )
}

private sealed interface BackupStatus {
  data object None : BackupStatus
  data object Exported : BackupStatus
  data object Failed : BackupStatus

  data class Imported(val result: ProgressBackup.ImportResult.Done) : BackupStatus
}

@Composable
private fun BackupScreen(
  busy: Boolean,
  status: BackupStatus,
  onExport: () -> Unit,
  onImport: () -> Unit,
) {
  OnboardingScaffold(
    step = null,
    onBack = null,
    title = stringResource(StringsR.string.settings_library_backup_title),
    subtitle = stringResource(StringsR.string.backup_subtitle),
    hero = {},
    details = {
      Spacer(Modifier.height(24.dp))
      StatusText(status)
    },
    actions = {
      OnboardingButton(
        text = stringResource(StringsR.string.backup_export),
        onClick = onExport,
        enabled = !busy,
      )
      Spacer(Modifier.height(12.dp))
      OnboardingButton(
        text = stringResource(StringsR.string.backup_import),
        onClick = onImport,
        enabled = !busy,
        primary = false,
      )
    },
  )
}

@Composable
private fun StatusText(status: BackupStatus) {
  Column {
    when (status) {
      BackupStatus.None -> {}
      BackupStatus.Exported -> Text(
        text = stringResource(StringsR.string.backup_export_done),
        style = MaterialTheme.typography.bodyLarge,
      )
      BackupStatus.Failed -> Text(
        text = stringResource(StringsR.string.backup_import_failed),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
      )
      is BackupStatus.Imported -> {
        val result = status.result
        Text(
          text = pluralStringResource(
            StringsR.plurals.backup_import_restored,
            result.books,
            result.books,
            result.bookmarks,
          ),
          style = MaterialTheme.typography.bodyLarge,
        )
        if (result.notFound.isNotEmpty()) {
          Spacer(Modifier.height(4.dp))
          Text(
            text = pluralStringResource(
              StringsR.plurals.backup_import_not_found,
              result.notFound.size,
              result.notFound.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private fun Context.writeTo(
  uri: Uri,
  text: String,
): Boolean = try {
  contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) } != null
} catch (_: IOException) {
  false
}

private fun Context.readFrom(uri: Uri): String? = try {
  contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
} catch (_: IOException) {
  null
}

@Composable
@Preview
private fun BackupScreenPreview() {
  VoiceTheme {
    BackupScreen(
      busy = false,
      status = BackupStatus.None,
      onExport = {},
      onImport = {},
    )
  }
}
