package dev.sebastiano.clockblocker.opus.widget.text

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.widget.rc.HostText
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "xxhdpi")
class TextFitTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `short labels keep the default 1x1 size`() {
        TextFit.smallLabelSp(context, "Sleep") shouldBe 11
        TextFit.smallLabelSp(context, "Nap if you're tired") shouldBe 11
    }

    @Test
    fun `a single long word shrinks instead of breaking mid-word`() {
        TextFit.smallLabelSp(context, "Desynchronisation") shouldBeLessThan 11
    }

    @Test
    fun `never below the minimum size`() {
        TextFit.smallLabelSp(context, "Supercalifragilisticexpialidocious") shouldBe 8
    }

    @Test
    fun `short countdowns keep the default size, long ones never grow`() {
        TextFit.smallCountdownSp(context, HostText.countdownWidest(42, compact = true)) shouldBe 12
        TextFit.smallCountdownSp(context, HostText.countdownWidest(23 * 60 + 5, compact = true)) shouldBeLessThanOrEqual
            TextFit.smallCountdownSp(context, HostText.countdownWidest(65, compact = true))
    }

    @Test
    fun `a larger font scale never picks a larger size`() {
        val atDefault = TextFit.smallLabelSp(context, "Clockblocked")
        RuntimeEnvironment.setFontScale(1.3f)
        TextFit.smallLabelSp(context, "Clockblocked") shouldBeLessThanOrEqual atDefault
        TextFit.smallLabelSp(context, "Supercalifragilistic") shouldBe 8
    }

    @Test
    fun `dial readouts follow font scale only up to the cap`() {
        TextFit.dialSp(context, 20) shouldBe 20
        RuntimeEnvironment.setFontScale(1.3f)
        TextFit.dialSp(context, 20) shouldBeLessThan 20
    }

    @Test
    fun `the widest countdown assumes two minute digits and keeps the hours`() {
        HostText.countdownWidest(5) shouldBe "59m"
        HostText.countdownWidest(61) shouldBe "1h 59m"
        HostText.countdownWidest(61, compact = true) shouldBe "1h59m"
        HostText.countdownWidest(-3) shouldBe "59m"
    }
}
