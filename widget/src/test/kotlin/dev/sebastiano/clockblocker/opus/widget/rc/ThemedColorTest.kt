package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.capture.createCreationDisplayInfo
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.ui.geometry.Size
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [themedColor]: one document, two looks. The player picks the light or the dark value from the theme it plays with
 * (what `RemoteViews` sets from the shade's night mode), so a notification follows the system theme without a new
 * capture or a re-post.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class ThemedColorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun capture(): ByteArray = runBlocking {
        captureSingleRemoteDocument(
            context = context,
            creationDisplayInfo = createCreationDisplayInfo(context, Size(SIZE.toFloat(), SIZE.toFloat())),
            profile = checkNotNull(widgetProfileFor(7)),
        ) {
            RemoteBox(RemoteModifier.fillMaxSize().background(themedColor(LIGHT, DARK))) {}
        }.bytes
    }

    private fun play(bytes: ByteArray, theme: Int): Int {
        val player = RemoteComposePlayer(context)
        player.setTheme(theme)
        player.setDocument(RemoteDocument(bytes))
        val spec = View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY)
        player.measure(spec, spec)
        player.layout(0, 0, SIZE, SIZE)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        player.draw(Canvas(bitmap))
        return bitmap.getPixel(SIZE / 2, SIZE / 2)
    }

    @Test
    fun `the document carries a themed colour`() {
        RemoteDocument(capture()).themedColors.shouldNotBeEmpty()
    }

    @Test
    fun `the same document draws the light value in a light theme and the dark one in a dark theme`() {
        val bytes = capture()
        play(bytes, RemoteComposePlayer.THEME_LIGHT) shouldBe LIGHT
        play(bytes, RemoteComposePlayer.THEME_DARK) shouldBe DARK
    }

    @Test
    fun `equal values stay a plain colour`() {
        val bytes = runBlocking {
            captureSingleRemoteDocument(
                context = context,
                creationDisplayInfo = createCreationDisplayInfo(context, Size(SIZE.toFloat(), SIZE.toFloat())),
                profile = checkNotNull(widgetProfileFor(7)),
            ) {
                RemoteBox(RemoteModifier.fillMaxSize().background(themedColor(LIGHT, LIGHT))) {}
            }.bytes
        }
        RemoteDocument(bytes).themedColors.isEmpty() shouldBe true
    }

    private companion object {
        const val SIZE = 40
        const val LIGHT = 0xFFFCF8FF.toInt()
        const val DARK = 0xFF131318.toInt()
    }
}
