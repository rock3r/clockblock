package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.notifications.receiver.AdviceActionReceiver
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class WidgetDoneIntentTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `widget Done is the notification Done broadcast for that advice`() {
        val intent = shadowOf(NotificationIntents.widgetDone(context, "trip-1", "advice-1")).savedIntent
        intent.component?.className shouldBe AdviceActionReceiver::class.java.name
        intent.action shouldBe NotificationIntents.adviceAction(context, AdviceAction.Done)
        intent.getStringExtra(NotificationIntents.EXTRA_TRIP_ID) shouldBe "trip-1"
        intent.getStringExtra(NotificationIntents.EXTRA_ADVICE_ID) shouldBe "advice-1"
    }

    @Test
    fun `each advice gets its own token so a later render never rewrites another's extras`() {
        // E.g. the picker previews (demo plan) are rendered right after the live widgets.
        val live = NotificationIntents.widgetDone(context, "trip-1", "advice-1")
        val demo = NotificationIntents.widgetDone(context, "demo-trip", "demo-advice")
        live shouldNotBe demo
        shadowOf(live).savedIntent.getStringExtra(NotificationIntents.EXTRA_ADVICE_ID) shouldBe "advice-1"
    }
}
