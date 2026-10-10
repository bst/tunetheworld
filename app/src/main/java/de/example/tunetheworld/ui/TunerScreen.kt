package de.example.tunetheworld.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.example.tunetheworld.R
import de.example.tunetheworld.music.NoteNames
import kotlin.math.roundToInt

@Composable
fun TunerScreen(viewModel: TunerViewModel = viewModel()) {
    val settingsLabel = stringResource(R.string.settings)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val language = NoteNames.languageOf(locale)

    LifecycleStartEffect(Unit) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }

    // Keep the screen on while tuning
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val note = state.note
    val noteName = note?.let { NoteNames.name(it.pitchClass, language) } ?: stringResource(R.string.no_note)
    val percentRounded = note?.percent?.roundToInt()
    val description = if (note != null && percentRounded != null) {
        stringResource(R.string.a11y_reading, noteName, note.octave, percentRounded)
    } else {
        stringResource(R.string.hint_play)
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.app_title), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { showSettings = true }) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = settingsLabel)
            }
        }
        Text(
            stringResource(R.string.info_line, state.settings.referenceHz, modeLabel(state.settings.transposition)),
            style = MaterialTheme.typography.bodyMedium
        )

        LevelMeter(
            levelDb = state.levelDb,
            gateDb = state.settings.gateDb,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Greyed out while the note is only being held after the tone ended
            val contentAlpha = if (state.isActive || note == null) 1f else 0.4f
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.alpha(contentAlpha)) {
                Text(noteName, fontSize = 96.sp, style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp))
                if (note != null) {
                    Text(note.octave.toString(), fontSize = 32.sp, modifier = Modifier.padding(top = 16.dp))
                }
            }
            NeedleGauge(percent = note?.percent, modifier = Modifier.fillMaxWidth().alpha(contentAlpha))
            if (note != null && percentRounded != null) {
                val sign = when {
                    percentRounded > 0 -> "+"
                    percentRounded < 0 -> "−"
                    else -> ""
                }
                Text(
                    "$sign${kotlin.math.abs(percentRounded)} %",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.alpha(contentAlpha)
                )
                Text(
                    stringResource(R.string.hertz_format, String.format(locale, "%.1f", note.frequencyHz)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(contentAlpha)
                )
            } else {
                Text(
                    stringResource(R.string.hint_play),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            onReferenceChange = viewModel::setReferenceHz,
            onTranspositionChange = viewModel::setTransposition,
            onGateChange = viewModel::setGateDb,
            onDismiss = { showSettings = false }
        )
    }
}
