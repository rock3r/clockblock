package dev.sebastiano.clockblocker.opus.widget.text

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.widget.rc.HostText
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "xxhdpi")
class TextFitTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

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

    @Test
    fun `fit keeps the largest size when the text fits`() {
        val fitted = TextFit.fit(context, "Sleep", widthDp = 200f, maxSp = 16, minSp = 12).shouldNotBeNull()
        fitted.sp shouldBe 16
        fitted.lines shouldBe 1
        fitted.heightDp shouldBeGreaterThan 0f
    }

    @Test
    fun `fit shrinks to the largest size that fits one line`() {
        val atMax = TextFit.measure(context, "See bright light", widthDp = 1000f, sp = 16, maxLines = 1)
        val width = atMax.widthDp * 0.9f
        val fitted = TextFit.fit(context, "See bright light", width, maxSp = 16, minSp = 10).shouldNotBeNull()
        fitted.sp shouldBeLessThan 16
        fitted.lines shouldBe 1
        TextFit.measure(context, "See bright light", width, fitted.sp + 1, maxLines = 1).fits shouldBe false
    }

    @Test
    fun `fit returns null rather than ellipsize or break a word`() {
        TextFit.fit(context, "See bright light", widthDp = 30f, maxSp = 16, minSp = 12, maxLines = 3).shouldBeNull()
        TextFit.fit(context, "Supercalifragilisticexpialidocious", widthDp = 80f, maxSp = 16, minSp = 12, maxLines = 4)
            .shouldBeNull()
    }

    @Test
    fun `fit wraps into more lines when allowed, and prefers fewer lines on request`() {
        val oneLine = TextFit.measure(context, "Your body is on Tokyo time", widthDp = 1000f, sp = 12, maxLines = 1)
        val width = oneLine.widthDp * 0.7f
        val wrapped = TextFit.fit(context, "Your body is on Tokyo time", width, maxSp = 12, minSp = 6, maxLines = 2)
            .shouldNotBeNull()
        wrapped.sp shouldBe 12
        wrapped.lines shouldBe 2
        val fewer = TextFit.fit(context, "Your body is on Tokyo time", width, maxSp = 12, minSp = 6, maxLines = 2, fewerLinesFirst = true)
            .shouldNotBeNull()
        fewer.lines shouldBe 1
        fewer.sp shouldBeLessThan 12
    }

    @Test
    fun `sp scale non-linearly with the font scale, like the text the player draws`() {
        val atOne = TextFit.measure(context, "Avoid light", widthDp = 1000f, sp = 13, semibold = true).widthDp
        RuntimeEnvironment.setFontScale(1.5f)
        val atOneAndAHalf = TextFit.measure(context, "Avoid light", widthDp = 1000f, sp = 13, semibold = true).widthDp
        // The platform's 1.5× curve takes 13 sp to 20 dp, not 19.5: a linear guess let a fitted label ellipsize.
        (atOneAndAHalf / atOne).toDouble() shouldBe (20.0 / 13.0 plusOrMinus 0.01)
    }

    @Test
    fun `text is measured at the bold text weight the player draws`() {
        val regular = TextFit.measure(context, "See bright light", widthDp = 1000f, sp = 13).widthDp
        val semibold = TextFit.measure(context, "See bright light", widthDp = 1000f, sp = 13, semibold = true).widthDp
        // Bold text adds 300 to every captured weight (RemoteText bakes it in): measure the same, wider glyphs.
        val bold = Configuration(context.resources.configuration).apply { fontWeightAdjustment = 300 }
        val boldContext = context.createConfigurationContext(bold)
        TextFit.weightAdjustment(boldContext) shouldBe 300
        TextFit.measure(boldContext, "See bright light", widthDp = 1000f, sp = 13).widthDp shouldBeGreaterThan regular
        TextFit.measure(boldContext, "See bright light", widthDp = 1000f, sp = 13, semibold = true).widthDp shouldBeGreaterThan
            semibold
        TextFit.weightAdjustment(context) shouldBe 0
    }
}
