package dev.sebastiano.clockblocker.opus.core.designsystem.component

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The ordering rules of [StatusBarIconsOwner], with a fake window value. */
class StatusBarIconsOwnerTest {
    private var lightStatusBars = true
    private val owner = StatusBarIconsOwner(read = { lightStatusBars }, write = { lightStatusBars = it })

    @Test
    fun `a claim sets the icons and gives the window's value back on release`() {
        val claim = owner.newClaim()

        claim.acquire(darkIcons = false)
        lightStatusBars shouldBe false

        claim.release()
        lightStatusBars shouldBe true
    }

    @Test
    fun `releasing an older claim leaves the newer one in charge`() {
        val outgoing = owner.newClaim().apply { acquire(darkIcons = false) }
        val incoming = owner.newClaim().apply { acquire(darkIcons = false) }

        outgoing.release()
        lightStatusBars shouldBe false

        incoming.release()
        lightStatusBars shouldBe true
    }

    @Test
    fun `releasing the newest claim hands the icons to the one before it`() {
        val first = owner.newClaim().apply { acquire(darkIcons = true) }
        val second = owner.newClaim().apply { acquire(darkIcons = false) }

        second.release()
        lightStatusBars shouldBe true

        first.update(darkIcons = false)
        lightStatusBars shouldBe false
    }

    @Test
    fun `an older claim's new ink waits until it is on top`() {
        val older = owner.newClaim().apply { acquire(darkIcons = false) }
        owner.newClaim().apply { acquire(darkIcons = true) }

        older.update(darkIcons = true)
        older.update(darkIcons = false)

        lightStatusBars shouldBe true
    }

    @Test
    fun `a theme switch while claimed becomes the value to go back to`() {
        val claim = owner.newClaim().apply { acquire(darkIcons = false) }

        lightStatusBars = true // enableEdgeToEdge after a theme switch
        claim.update(darkIcons = true)
        lightStatusBars = false // and switched to dark theme
        claim.release()

        lightStatusBars shouldBe false
    }

    @Test
    fun `the top claim takes the icons back after another writer changes them`() {
        val claim = owner.newClaim().apply { acquire(darkIcons = false) }

        lightStatusBars = true // the activity reapplies the theme's bars while the header is still composed
        claim.update(darkIcons = false)
        lightStatusBars shouldBe false

        claim.release()
        lightStatusBars shouldBe true
    }

    @Test
    fun `a theme switch that matches the claim still becomes the value to go back to`() {
        val claim = owner.newClaim().apply { acquire(darkIcons = false) }

        lightStatusBars = false // dark theme: the same light icons the night sky already asked for
        owner.baseChanged()
        claim.release()

        lightStatusBars shouldBe false
    }

    @Test
    fun `a theme switch keeps the top claim's icons`() {
        owner.newClaim().apply { acquire(darkIcons = false) }

        lightStatusBars = true
        owner.baseChanged()

        lightStatusBars shouldBe false
    }

    @Test
    fun `releasing twice or without acquiring does nothing`() {
        val idle = owner.newClaim()
        idle.release()
        lightStatusBars shouldBe true

        val claim = owner.newClaim().apply { acquire(darkIcons = false) }
        claim.release()
        lightStatusBars = false
        claim.release()
        lightStatusBars shouldBe false
    }
}
