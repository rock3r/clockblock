package dev.sebastiano.clockblocker.opus.feature.onboarding

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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.unit.dp
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.Place
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home step's full-height search: with the keyboard up and too little room, the field rises to the top, the bars
 * and the zone card fold away and the results fill the space down to the keyboard. The keyboard is simulated by
 * dispatching real IME insets to the compose view (Robolectric has no keyboard), so the layout code under test is
 * the one that runs on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingSearchModeTest : ClockblockScreenshotTest() {
    private val calls = mutableListOf<String>()
    private var state by mutableStateOf(searchState("lon", ManyResults))
    private lateinit var view: View
    private lateinit var backDispatcher: OnBackPressedDispatcher

    /** Acts like the ViewModel where it matters here: picking a place clears the search. */
    private val actions = object : OnboardingActions by OnboardingActions.None {
        override fun back() { calls += "back" }
        override fun queryChange(query: String) { calls += "query:$query" }
        override fun selectPlace(place: Place) {
            calls += "place:${place.code}"
            state = state.copy(homePlace = place, query = "", results = persistentListOf())
        }
    }

    private fun show(
        darkTheme: Boolean = false,
        fontScale: Float? = null,
        reduceMotion: Boolean = true,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = setContent(darkTheme = darkTheme, fontScale = fontScale, reduceMotion = reduceMotion) {
        view = LocalView.current
        backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            Box(Modifier.fillMaxSize()) {
                OnboardingContent(state, actions, Modifier.fillMaxSize())
                // Stands in for the keyboard in goldens: the IME area the layout leaves free.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.ime)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
    }

    /** Raises (or with 0, hides) a keyboard [heightDp] tall, as the window would report it. */
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

    /** Focuses the search with a phone-sized keyboard: the full-height search. */
    private fun enterSearch() {
        compose.onNodeWithTag(HomeZoneTags.Search).performClick()
        keyboard(PhoneKeyboardDp)
    }

    private fun assertSearchMode() {
        compose.onNodeWithTag(HomeZoneTags.Search).assertIsFocused()
        compose.onNodeWithTag(HomeZoneTags.Current).assertDoesNotExist()
        compose.onNodeWithText("Where's home?").assertDoesNotExist()
        compose.onNodeWithTag(OnboardingTags.Next).assertDoesNotExist()
        compose.onNodeWithTag(OnboardingTags.Back).assertDoesNotExist()
        // The field sits at the top of the step: just the step's padding above it.
        compose.onNodeWithTag(HomeZoneTags.Search).getUnclippedBoundsInRoot().top.value shouldBe (16f plusOrMinus 12f)
    }

    private fun assertNormalLayout() {
        compose.onNodeWithTag(HomeZoneTags.Current).assertExists()
        compose.onNodeWithText("Where's home?").assertExists()
        compose.onNodeWithTag(OnboardingTags.Next).assertIsDisplayed()
    }

    @Test
    fun `focusing the search with the keyboard up moves it to the top and shows several results`() {
        show()
        enterSearch()
        assertSearchMode()
        // Five of the eight rows fit between the field and a phone keyboard (one showed before).
        ManyResults.take(5).forEach { compose.onNodeWithTag(HomeZoneTags.result(it)).assertIsDisplayed() }
    }

    @Test
    fun `with a hardware keyboard (no IME) focusing keeps the step as it is`() {
        show()
        compose.onNodeWithTag(HomeZoneTags.Search).performClick()
        settle()
        compose.onNodeWithTag(HomeZoneTags.Search).assertIsFocused()
        assertNormalLayout()
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun `on a tall tablet enough results fit, so the step keeps its layout`() {
        show()
        compose.onNodeWithTag(HomeZoneTags.Search).performClick()
        keyboard(TabletKeyboardDp)
        assertNormalLayout()
        compose.onNodeWithTag(HomeZoneTags.result(ManyResults[2])).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun `a landscape phone goes full height too, with the results beside the field`() {
        show()
        compose.onNodeWithTag(HomeZoneTags.Search).performClick()
        keyboard(LandscapeKeyboardDp)
        compose.onNodeWithTag(HomeZoneTags.Current).assertDoesNotExist()
        compose.onNodeWithTag(OnboardingTags.Next).assertDoesNotExist()
        compose.onNodeWithText("Where's home?").assertDoesNotExist()
        fullyAboveKeyboard(LandscapeKeyboardDp) shouldBeGreaterThanOrEqual 3
        val field = compose.onNodeWithTag(HomeZoneTags.Search).getUnclippedBoundsInRoot()
        compose.onNodeWithTag(HomeZoneTags.result(ManyResults[0])).getUnclippedBoundsInRoot().left shouldBeGreaterThan field.right
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun `with a pixel 7 pro's landscape keyboard two whole results still show`() {
        show()
        compose.onNodeWithTag(HomeZoneTags.Search).performClick()
        keyboard(DeviceLandscapeKeyboardDp)
        fullyAboveKeyboard(DeviceLandscapeKeyboardDp) shouldBeGreaterThanOrEqual 2
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun `on a tall tablet with large text, either layout shows three whole results`() {
        show(fontScale = 1.5f)
        compose.onNodeWithTag(HomeZoneTags.Search).performClick()
        keyboard(TabletKeyboardDp)
        fullyAboveKeyboard(TabletKeyboardDp) shouldBeGreaterThanOrEqual 3
    }

    /** The activity window's bounds (a focused field may add a popup root for its handle). */
    private fun window() = compose.onAllNodes(isRoot()).onFirst().getUnclippedBoundsInRoot()

    /** How many result rows sit wholly on screen above a keyboard [keyboardDp] tall. */
    private fun fullyAboveKeyboard(keyboardDp: Int): Int {
        val root = window()
        val keyboardTop = root.bottom - keyboardDp.dp
        return ManyResults.count { place ->
            val node = compose.onAllNodesWithTag(HomeZoneTags.result(place))
            node.fetchSemanticsNodes().isNotEmpty() && node.onFirst().getUnclippedBoundsInRoot().let { it.top >= root.top && it.bottom <= keyboardTop }
        }
    }

    @Test
    fun `typing never moves the results while the search is in flight`() {
        show()
        enterSearch()
        val rowTop = compose.onNodeWithTag(HomeZoneTags.result(ManyResults[0])).getUnclippedBoundsInRoot().top
        // "lond" is typed; the old answer (for "lon") is still on screen until the new one arrives.
        state = state.copy(query = "lond")
        compose.waitForIdle()
        compose.onNodeWithTag(HomeZoneTags.result(ManyResults[0])).getUnclippedBoundsInRoot().top shouldBe rowTop
        // TalkBack's live region stays put and keeps the last settled count until the new answer settles.
        compose.onNodeWithTag(HomeZoneTags.ResultCount).assert(hasText("8 places match"))
        state = searchState("lond", persistentListOf(DemoData.LHR, DemoData.LGW))
        compose.waitForIdle()
        compose.onNodeWithTag(HomeZoneTags.ResultCount).assert(hasText("2 places match"))
        compose.onNodeWithTag(HomeZoneTags.result(ManyResults[0])).getUnclippedBoundsInRoot().top shouldBe rowTop
    }

    @Test
    @Config(qualifiers = "w411dp-h560dp-xxhdpi")
    fun `a search with no match scrolls its message into view`() {
        state = searchState("", persistentListOf())
        show()
        // A hardware keyboard: no IME, so the step keeps its layout and the field sits near the bottom of the view.
        compose.onNodeWithTag(HomeZoneTags.Search).performSemanticsAction(SemanticsActions.RequestFocus)
        settle()
        state = state.copy(query = "zzz")
        settle()
        state = searchState("zzz", persistentListOf())
        settle()
        val message = compose.onNodeWithTag(HomeZoneTags.ResultCount).assert(hasText("Nothing matches", substring = true)).getUnclippedBoundsInRoot()
        val bar = compose.onNodeWithTag(OnboardingTags.Next).getUnclippedBoundsInRoot()
        // Wholly visible: above the bottom bar.
        (message.bottom <= bar.top) shouldBe true
    }

    @Test
    fun `the keyboard's search key only puts the keyboard away`() {
        show()
        enterSearch()
        compose.onNodeWithTag(HomeZoneTags.Search).performImeAction()
        settle()
        calls.filter { it.startsWith("place:") } shouldBe emptyList()
        compose.onNodeWithTag(HomeZoneTags.Search).assertIsNotFocused()
    }

    @Test
    fun `on a phone the keyboard takes back first, then back leaves the step`() {
        show()
        enterSearch()
        // A real device: the first Back only hides the keyboard, which ends the search.
        keyboard(0)
        compose.onNodeWithTag(HomeZoneTags.Search).assertIsNotFocused()
        assertNormalLayout()
        calls shouldNotContain "back"
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        settle()
        calls shouldContainExactly listOf("back")
    }

    @Test
    fun `back leaves the search before it leaves the step`() {
        show()
        enterSearch()
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        settle()
        compose.onNodeWithTag(HomeZoneTags.Search).assertIsNotFocused()
        assertNormalLayout()
        calls shouldNotContain "back"

        compose.runOnUiThread { backDispatcher.onBackPressed() }
        settle()
        calls shouldContainExactly listOf("back")
    }

    @Test
    fun `picking a result leaves the search`() {
        show()
        enterSearch()
        compose.onNodeWithTag(HomeZoneTags.result(ManyResults[3])).performClick()
        settle()
        calls shouldContainExactly listOf("place:${ManyResults[3].code}")
        compose.onNodeWithTag(HomeZoneTags.Search).assertIsNotFocused()
        // The keyboard follows the focus out; the step comes back with the picked city in the card.
        keyboard(0)
        assertNormalLayout()
        compose.onNodeWithText(ManyResults[3].city, substring = true).assertIsDisplayed()
    }

    @Test
    fun `the field's arrow leaves the search`() {
        show()
        enterSearch()
        compose.onNodeWithTag(HomeZoneTags.CloseSearch).assertIsDisplayed().performClick()
        settle()
        compose.onNodeWithTag(HomeZoneTags.Search).assertIsNotFocused()
        assertNormalLayout()
    }

    @Test
    fun `hiding the keyboard leaves the search`() {
        show()
        enterSearch()
        keyboard(0)
        compose.onNodeWithTag(HomeZoneTags.Search).assertIsNotFocused()
        assertNormalLayout()
    }

    @Test
    fun `talkback hears how many places match`() {
        show()
        enterSearch()
        compose.onNodeWithTag(HomeZoneTags.ResultCount)
            .assert(hasText("8 places match"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `the search folds in place with motion on`() {
        show(reduceMotion = false)
        val before = compose.onNodeWithTag(HomeZoneTags.Search).getUnclippedBoundsInRoot().top
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(HomeZoneTags.Search).performClick()
        compose.runOnUiThread {
            val px = (PhoneKeyboardDp * view.resources.displayMetrics.density).toInt()
            ViewCompat.dispatchApplyWindowInsets(
                view,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build(),
            )
        }
        compose.mainClock.advanceTimeBy(120)
        val mid = compose.onNodeWithTag(HomeZoneTags.Search).getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(2_000)
        val after = compose.onNodeWithTag(HomeZoneTags.Search).getUnclippedBoundsInRoot().top
        // The field travels up through the frames (no jump straight to the top).
        (mid < before && mid > after) shouldBe true
    }

    @Test fun searchModeGolden() = golden("onboarding_2_home_search_mode")

    @Test fun searchModeGoldenDark() = golden("onboarding_2_home_search_mode_dark", darkTheme = true)

    @Test fun searchModeGoldenLargeFont() = golden("onboarding_2_home_search_mode_fontscale_1_5", fontScale = 1.5f)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun searchModeGoldenLandscape() = golden("onboarding_2_home_search_mode_landscape", keyboardDp = LandscapeKeyboardDp)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun searchModeGoldenLandscapeDevice() = golden("onboarding_2_home_search_mode_landscape_device", keyboardDp = DeviceLandscapeKeyboardDp)

    @Test fun searchModeGoldenRtl() = golden("onboarding_2_home_search_mode_rtl", layoutDirection = LayoutDirection.Rtl)

    private fun golden(
        name: String,
        darkTheme: Boolean = false,
        fontScale: Float? = null,
        keyboardDp: Int = PhoneKeyboardDp,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        show(darkTheme = darkTheme, fontScale = fontScale, layoutDirection = layoutDirection)
        // Focus without a tap, so no text-selection handle sits in the golden.
        compose.onNodeWithTag(HomeZoneTags.Search).performSemanticsAction(SemanticsActions.RequestFocus)
        keyboard(keyboardDp)
        compose.onNodeWithTag(OnboardingTags.Next).assertDoesNotExist()
        capture(name)
    }

    private companion object {
        /** A Pixel 7 Pro's Gboard with its suggestion strip, in dp. */
        const val PhoneKeyboardDp = 330
        const val TabletKeyboardDp = 380
        const val LandscapeKeyboardDp = 210

        /**
         * What a Pixel 7 Pro's landscape Gboard leaves of a 411 dp window, here without system bars: its 242 dp over
         * the navigation bar, plus the status and navigation bars (about 24 dp each), so ~121 dp stay.
         */
        const val DeviceLandscapeKeyboardDp = 290

        val ManyResults: ImmutableList<Place> = persistentListOf(
            DemoData.LHR, DemoData.LGW, DemoData.LIS, DemoData.LAX, DemoData.SFO, DemoData.SIN, DemoData.SYD, DemoData.JFK,
        )

        fun searchState(query: String, results: ImmutableList<Place>) =
            onboardingState(OnboardingStep.HomeZone, query = query).copy(results = results, searchMatchingQuery = query)
    }
}
