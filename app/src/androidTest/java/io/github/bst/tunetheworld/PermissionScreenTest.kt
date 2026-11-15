package io.github.bst.tunetheworld

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test for the first launch, before the microphone permission has been granted. */
@RunWith(AndroidJUnit4::class)
class PermissionScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(@StringRes id: Int): String = composeRule.activity.getString(id)

    @Test
    fun withoutPermissionThePermissionScreenIsShown() {
        composeRule.onNodeWithText(string(R.string.permission_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.permission_button)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.settings)).assertDoesNotExist()
    }
}
