package io.github.bst.tunetheworld

import android.Manifest
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests for the tuner screen with the microphone permission granted.
 *
 * The emulator's microphone is silent or synthetic, so these tests check startup and the UI, not pitch
 * detection. Each test starts with cleared app data (see testInstrumentationRunnerArguments in build.gradle.kts),
 * so the settings are at their defaults.
 */
@RunWith(AndroidJUnit4::class)
class TunerScreenTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun infoLine(referenceHz: Int) =
        string(R.string.info_line, referenceHz, string(R.string.transposition_concert))

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun tunerScreenAppearsAndKeepsRunning() {
        composeRule.onNodeWithText(string(R.string.app_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.settings)).assertIsDisplayed()
        waitForText(infoLine(440))

        // Give the audio engine time to read and analyse a few blocks. A crash there ends the test process.
        SystemClock.sleep(ENGINE_RUN_MS)

        composeRule.onNodeWithText(string(R.string.app_title)).assertIsDisplayed()
        composeRule.onNodeWithText(infoLine(440)).assertIsDisplayed()
    }

    @Test
    fun changingTheReferencePitchUpdatesTheScreen() {
        waitForText(infoLine(440))
        composeRule.onNodeWithContentDescription(string(R.string.settings)).performClick()
        composeRule.onNodeWithText(string(R.string.reference_pitch)).assertIsDisplayed()

        // The first "+" belongs to the reference pitch, the second to the transposition
        composeRule.onAllNodesWithText("+")[0].performClick()

        // Settings are saved asynchronously, so wait for the new value to come back from the store
        waitForText(string(R.string.hertz_format, "441"))

        Espresso.pressBack()
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(string(R.string.reference_pitch)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText(infoLine(441)).assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val ENGINE_RUN_MS = 2_000L
    }
}
