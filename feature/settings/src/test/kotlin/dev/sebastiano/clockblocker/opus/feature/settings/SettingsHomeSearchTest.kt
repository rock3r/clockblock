package dev.sebastiano.clockblocker.opus.feature.settings

import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.feature.onboarding.HomeZoneTags
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Home time zone: the same search as onboarding. With the keyboard up and too little room, the editor's
 * card grows to the whole window, the field sits at the top and the results fill the space down to the keyboard. The
 * keyboard is simulated by dispatching real IME insets to the compose view (the editor fills its dialog's window, so
 * it is rendered here straight in the activity's).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsHomeSearchTest : ClockblockScreenshotTest() {
    private val calls = mutableListOf<String>()
    private var query by mutableStateOf("lon")
    private var results: ImmutableList<Place> by mutableStateOf(ManyResults)
    private lateinit var view: View
    private lateinit var backDispatcher: OnBackPressedDispatcher

    private fun show(darkTheme: Boolean = false, fontScale: Float? = null) =
        setContent(darkTheme = darkTheme, fontScale = fontScale) {
            view = LocalView.current
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            Box(Modifier.fillMaxSize()) {
                HomeZoneEditor(
                    profile = DemoData.profile,
                    query = query,
                    results = results,
                    resultsQuery = query,
                    onQueryChange = { calls += "query:$it" },
                    onSelect = { calls += "place:${it.code}" },
                    onClose = { calls += "close" },
                    now = DemoData.Now,
                )
                // Stands in for the keyboard in goldens.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.ime)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }

    private fun keyboard(heightDp: Int) {
        compose.runOnUiThread {
            val px = (heightDp * view.resources.displayMetrics.density).toInt()
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
                .setVisible(WindowInsetsCompat.Type.ime(), px > 0)
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    private fun enterSearch() {
        compose.onNodeWithTag(SettingsTags.HomeSearch).performSemanticsAction(SemanticsActions.RequestFocus)
        keyboard(PhoneKeyboardDp)
    }

    private fun assertFullScreen() {
        compose.onNodeWithTag(SettingsTags.HomeSearch).assertIsFocused()
        compose.onNodeWithText("Where's home?").assertDoesNotExist()
        compose.onNodeWithText("Close").assertDoesNotExist()
        // The card fills the window: the field sits at its top, under the card's padding.
        compose.onNodeWithTag(SettingsTags.HomeEditor).getUnclippedBoundsInRoot().top.value shouldBe (0f plusOrMinus 1f)
        compose.onNodeWithTag(SettingsTags.HomeSearch).getUnclippedBoundsInRoot().top.value shouldBe (24f plusOrMinus 12f)
    }

    private fun assertDialog() {
        compose.onNodeWithText("Where's home?").assertIsDisplayed()
        compose.onNodeWithText("Close").assertExists()
    }

    /** The activity window's bounds (a focused field may add a popup root for its handle). */
    private fun window() = compose.onAllNodes(isRoot()).onFirst().getUnclippedBoundsInRoot()

    /** How many result rows sit wholly on screen above a keyboard [keyboardDp] tall. */
    private fun fullyAboveKeyboard(keyboardDp: Int): Int {
        val keyboardTop = window().bottom - keyboardDp.dp
        return ManyResults.count { place ->
            val node = compose.onAllNodesWithTag(SettingsTags.homeResult(place))
            node.fetchSemanticsNodes().isNotEmpty() && node.onFirst().getUnclippedBoundsInRoot().bottom <= keyboardTop
        }
    }

    @Test
    fun `focusing the search with the keyboard up takes the whole window`() {
        show()
        enterSearch()
        assertFullScreen()
        fullyAboveKeyboard(PhoneKeyboardDp) shouldBeGreaterThanOrEqual 5
    }

    @Test
    fun `the editor keeps its title for talkback in full screen`() {
        show()
        enterSearch()
        compose.onNodeWithTag(SettingsTags.HomeEditor)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Where's home?"))
    }

    @Test
    fun `with a hardware keyboard (no IME) the editor stays a dialog`() {
        show()
        compose.onNodeWithTag(SettingsTags.HomeSearch).performClick()
        settle()
        compose.onNodeWithTag(SettingsTags.HomeSearch).assertIsFocused()
        assertDialog()
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun `on a tall tablet enough results fit, so the editor stays a dialog`() {
        show()
        enterSearch()
        assertDialog()
    }

    @Test
    fun `hiding the keyboard leaves the search`() {
        show()
        enterSearch()
        keyboard(0)
        compose.onNodeWithTag(SettingsTags.HomeSearch).assertIsNotFocused()
        assertDialog()
    }

    @Test
    fun `the field's arrow leaves the search`() {
        show()
        enterSearch()
        compose.onNodeWithTag(HomeZoneTags.CloseSearch).performClick()
        settle()
        compose.onNodeWithTag(SettingsTags.HomeSearch).assertIsNotFocused()
        assertDialog()
    }

    @Test
    fun `back leaves the search before it closes the editor`() {
        show()
        enterSearch()
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        settle()
        compose.onNodeWithTag(SettingsTags.HomeSearch).assertIsNotFocused()
        assertDialog()
        calls shouldContainExactly emptyList()
    }

    @Test
    fun `picking a result saves it`() {
        show()
        enterSearch()
        compose.onNodeWithTag(SettingsTags.homeResult(ManyResults[2])).performClick()
        settle()
        calls shouldContainExactly listOf("place:${ManyResults[2].code}")
        compose.onNodeWithTag(SettingsTags.HomeSearch).assertIsNotFocused()
    }

    @Test
    fun `a tap outside the card closes the editor`() {
        show()
        compose.onNodeWithText("Where's home?").assertIsDisplayed()
        // Above the centred card: the scrim.
        compose.onRoot().performTouchInput { click(Offset(centerX, 4f)) }
        calls shouldContainExactly listOf("close")
    }

    @Test
    fun `talkback hears how many places match`() {
        show()
        compose.onNodeWithTag(HomeZoneTags.ResultCount).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test fun fullScreenGolden() = golden("settings_editor_home_search_mode")

    @Test fun fullScreenGoldenDark() = golden("settings_editor_home_search_mode_dark", darkTheme = true)

    @Test fun fullScreenGoldenLargeFont() = golden("settings_editor_home_search_mode_fontscale_1_5", fontScale = 1.5f)

    private fun golden(name: String, darkTheme: Boolean = false, fontScale: Float? = null) {
        show(darkTheme = darkTheme, fontScale = fontScale)
        enterSearch()
        capture(name)
    }

    private companion object {
        /** A Pixel 7 Pro's Gboard with its suggestion strip, in dp. */
        const val PhoneKeyboardDp = 330

        val ManyResults: ImmutableList<Place> = persistentListOf(
            DemoData.LHR, DemoData.LGW, DemoData.LIS, DemoData.LAX, DemoData.SFO, DemoData.SIN, DemoData.SYD, DemoData.JFK,
        )
    }
}
