package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SuiteTypeTest {
    @Test
    fun `landscape phones get a rail instead of a bottom bar`() {
        suiteTypeFor(NavigationSuiteType.ShortNavigationBarMedium, minWidthDp = 840, minHeightDp = 0, tabletop = false) shouldBe
            NavigationSuiteType.WideNavigationRailCollapsed
        suiteTypeFor(NavigationSuiteType.ShortNavigationBarMedium, minWidthDp = 600, minHeightDp = 0, tabletop = false) shouldBe
            NavigationSuiteType.WideNavigationRailCollapsed
    }

    @Test
    fun `portrait phones, tall windows and tabletop keep the default`() {
        suiteTypeFor(NavigationSuiteType.ShortNavigationBarCompact, minWidthDp = 0, minHeightDp = 480, tabletop = false) shouldBe
            NavigationSuiteType.ShortNavigationBarCompact
        suiteTypeFor(NavigationSuiteType.WideNavigationRailCollapsed, minWidthDp = 840, minHeightDp = 480, tabletop = false) shouldBe
            NavigationSuiteType.WideNavigationRailCollapsed
        suiteTypeFor(NavigationSuiteType.ShortNavigationBarCompact, minWidthDp = 840, minHeightDp = 0, tabletop = true) shouldBe
            NavigationSuiteType.ShortNavigationBarCompact
    }
}
