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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    private fun show(darkTheme: Boolean = false, fontScale: Float? = null, reduceMotion: Boolean = true) =
        setContent(darkTheme = darkTheme, fontScale = fontScale, reduceMotion = reduceMotion) {
            view = LocalView.current
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
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
    fun `a landscape phone goes full height too`() {
        show()
        compose.onNodeWithTag(HomeZoneTags.Search).performClick()
        keyboard(LandscapeKeyboardDp)
        compose.onNodeWithTag(HomeZoneTags.Current).assertDoesNotExist()
        compose.onNodeWithTag(OnboardingTags.Next).assertDoesNotExist()
        compose.onNodeWithTag(HomeZoneTags.result(ManyResults[0])).assertIsDisplayed()
        compose.onNodeWithTag(HomeZoneTags.result(ManyResults[1])).assertIsDisplayed()
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

    private fun golden(name: String, darkTheme: Boolean = false, fontScale: Float? = null, keyboardDp: Int = PhoneKeyboardDp) {
        show(darkTheme = darkTheme, fontScale = fontScale)
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

        val ManyResults: ImmutableList<Place> = persistentListOf(
            DemoData.LHR, DemoData.LGW, DemoData.LIS, DemoData.LAX, DemoData.SFO, DemoData.SIN, DemoData.SYD, DemoData.JFK,
        )

        fun searchState(query: String, results: ImmutableList<Place>) =
            onboardingState(OnboardingStep.HomeZone, query = query).copy(results = results, searchMatchingQuery = query)
    }
}
