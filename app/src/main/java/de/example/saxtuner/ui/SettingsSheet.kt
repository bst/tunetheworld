package de.example.saxtuner.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import de.example.saxtuner.R
import de.example.saxtuner.data.SettingsRepository
import de.example.saxtuner.data.TunerSettings
import de.example.saxtuner.engine.TunerConfig
import de.example.saxtuner.music.AppLanguage
import de.example.saxtuner.music.NoteNames
import de.example.saxtuner.music.Transposition
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun modeLabel(t: Transposition): String = stringResource(
    when (t) {
        Transposition.TENOR_BB -> R.string.mode_tenor
        Transposition.ALTO_EB -> R.string.mode_alto
        Transposition.CONCERT -> R.string.mode_concert
    }
)

private fun currentLanguage(): AppLanguage {
    val locales = AppCompatDelegate.getApplicationLocales()
    // Empty means the app follows the system language
    val first = if (locales.isEmpty) Locale.getDefault() else locales[0]!!
    return NoteNames.languageOf(first)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: TunerSettings,
    onReferenceChange: (Int) -> Unit,
    onTranspositionChange: (Transposition) -> Unit,
    onGateChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.reference_pitch), style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = { onReferenceChange(settings.referenceHz - 1) }) { Text("−") }
                Text(
                    stringResource(R.string.hertz_format, settings.referenceHz.toString()),
                    style = MaterialTheme.typography.headlineSmall
                )
                TextButton(onClick = { onReferenceChange(settings.referenceHz + 1) }) { Text("+") }
            }
            Slider(
                value = settings.referenceHz.toFloat(),
                onValueChange = { onReferenceChange(it.roundToInt()) },
                valueRange =
                    SettingsRepository.MIN_REFERENCE_HZ.toFloat()..SettingsRepository.MAX_REFERENCE_HZ.toFloat(),
                steps = SettingsRepository.MAX_REFERENCE_HZ - SettingsRepository.MIN_REFERENCE_HZ - 1
            )
            Button(onClick = { onReferenceChange(SettingsRepository.DEFAULT_REFERENCE_HZ) }) {
                Text(stringResource(R.string.reset_reference))
            }

            Text(stringResource(R.string.display_mode), style = MaterialTheme.typography.titleMedium)
            val modes = Transposition.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.transposition == mode,
                        onClick = { onTranspositionChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                    ) { Text(modeLabel(mode)) }
                }
            }

            Text(stringResource(R.string.sensitivity), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.sensitivity_hint), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = settings.gateDb,
                onValueChange = { onGateChange(it.roundToInt().toFloat()) },
                valueRange = TunerConfig.MIN_GATE_DB..TunerConfig.MAX_GATE_DB,
                steps = (TunerConfig.MAX_GATE_DB - TunerConfig.MIN_GATE_DB).toInt() - 1
            )
            Text(
                stringResource(R.string.gate_format, settings.gateDb.roundToInt()),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            val languages = AppLanguage.entries
            val selected = currentLanguage()
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                languages.forEachIndexed { index, language ->
                    SegmentedButton(
                        selected = selected == language,
                        onClick = {
                            // Android stores the choice itself and recreates the activity
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, languages.size)
                    ) {
                        Text(
                            stringResource(
                                if (language ==
                                    AppLanguage.DE
                                ) {
                                    R.string.language_de
                                } else {
                                    R.string.language_en
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}
