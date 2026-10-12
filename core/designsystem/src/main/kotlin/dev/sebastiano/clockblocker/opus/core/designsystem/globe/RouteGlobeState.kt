package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** What the user picked on the globe by tapping it (or with a TalkBack action): a flight or an airport. */
@Immutable
sealed interface GlobeSelection {
    /** Leg [index] of the globe's legs. */
    data class Leg(val index: Int) : GlobeSelection

    /** Stop [index] of the route's distinct stops, in visit order (an airport shared by two legs counts once). */
    data class Stop(val index: Int) : GlobeSelection
}

/**
 * The camera of a [RouteGlobe] (where it looks from, how far it's zoomed) and what's picked on it. Hoist it to drive or
 * read the globe from outside: re-centre it from a button, follow it in a transition ([project]), show a callout for
 * [selection].
 *
 * The globe frames itself on its route (or active leg) whenever that framing changes, turning on `dataSpatial()`.
 * A drag, a fling or a zoom moves it away ([moved] turns true) until the next re-centre or re-framing.
 */
@Stable
class RouteGlobeState internal constructor(initial: GlobeView) {
    internal var latitude = Animatable(initial.latitude.toFloat())
        private set
    internal var longitude = Animatable(initial.longitude.toFloat())
        private set
    internal val zoomLevel = Animatable(1f)

    /** The view the globe frames itself on (its route, or the active leg); set by the globe. Read by no composition. */
    internal var framing: GlobeView? = null

    /**
     * The globe's first framing: it opens already looking at [target], with nothing to animate from. Called from
     * composition, so it only replaces camera values nothing has moved or drawn yet (idempotent afterwards).
     */
    internal fun frameAtOnce(target: GlobeView) {
        if (framing != null) return
        latitude = Animatable(target.latitude.toFloat())
        longitude = Animatable(target.longitude.toFloat())
        framing = target
    }

    /** Where a drag in progress has taken the view (ahead of the animatables, which snap a frame later). */
    private var dragView: GlobeView? = null

    /** The flight or airport picked on the globe, if any. */
    var selection: GlobeSelection? by mutableStateOf(null)

    /** True once the user has turned or zoomed the globe away from its framing (a re-centre makes sense). */
    var moved: Boolean by mutableStateOf(false)
        internal set

    /** Where the globe looks from now (read it in layout or draw while it moves). */
    val view: GlobeView get() = GlobeView(latitude.value.toDouble(), longitude.value.toDouble())

    /** How far it's zoomed in: 1 shows the whole Earth. Read it in layout or draw while it moves. */
    val zoom: Float get() = zoomLevel.value

    /**
     * Where [point] is drawn, in units of the globe's (unzoomed) radius from the centre, x right and y down; null when
     * it's on the far side. Reads the animated camera: call it in layout or draw.
     */
    fun project(point: GeoPoint): Offset? {
        val p = GlobeGeo.orthographic(point, view)
        if (!p.visible) return null
        return Offset(p.x.toFloat() * zoom, -p.y.toFloat() * zoom)
    }

    /** Back to the framing at zoom 1, on [spec] (null: at once). Clears [moved]. */
    suspend fun recentre(spec: AnimationSpec<Float>?) {
        val target = framing ?: return
        moved = false
        dragView = null
        lookAt(target, spec, zoomTo = 1f)
    }

    /** Turns the view by [degrees] east (negative: west), on [spec] (null: at once). For TalkBack's turn actions. */
    suspend fun turnBy(degrees: Float, spec: AnimationSpec<Float>?) {
        moved = true
        val to = longitude.value + degrees
        if (spec == null) longitude.snapTo(to) else longitude.animateTo(to, spec)
    }

    /** Tilts the view by [degrees] north (negative: south), on [spec] (null: at once). */
    suspend fun tiltBy(degrees: Float, spec: AnimationSpec<Float>?) {
        moved = true
        val to = (latitude.value + degrees).coerceIn(-GlobeGeo.MaxDragLatitude.toFloat(), GlobeGeo.MaxDragLatitude.toFloat())
        if (spec == null) latitude.snapTo(to) else latitude.animateTo(to, spec)
    }

    /** Zooms to [level] (clamped to 1…[MaxZoom]) on [spec] (null: at once). */
    suspend fun zoomTo(level: Float, spec: AnimationSpec<Float>?) {
        val to = level.coerceIn(1f, MaxZoom)
        if (to != 1f) moved = true
        if (spec == null) zoomLevel.snapTo(to) else zoomLevel.animateTo(to, spec)
    }

    internal suspend fun lookAt(target: GlobeView, spec: AnimationSpec<Float>?, zoomTo: Float? = null) = coroutineScope {
        val toLongitude = longitude.value + GlobeGeo.longitudeDelta(longitude.value.toDouble(), target.longitude).toFloat()
        val toLatitude = target.latitude.toFloat()
        if (spec == null) {
            latitude.snapTo(toLatitude)
            longitude.snapTo(toLongitude)
            zoomTo?.let { zoomLevel.snapTo(it) }
        } else {
            launch { latitude.animateTo(toLatitude, spec) }
            launch { longitude.animateTo(toLongitude, spec) }
            zoomTo?.let { launch { zoomLevel.animateTo(it, spec) } }
        }
    }

    /** A drag began: stop any coast or turn, so the globe is under the finger. */
    internal suspend fun stop() {
        latitude.stop()
        longitude.stop()
        dragView = null
    }

    /** The view [dx], [dy] (units of the on-screen radius, right and down positive) on from the drag so far. */
    internal fun dragTarget(dx: Float, dy: Float): GlobeView {
        moved = true
        val next = GlobeGeo.dragged(dragView ?: view, dx.toDouble(), dy.toDouble())
        dragView = next
        return next
    }

    internal suspend fun snapTo(view: GlobeView) {
        latitude.snapTo(view.latitude.toFloat())
        longitude.snapTo(view.longitude.toFloat())
    }

    internal fun zoomTarget(factor: Float): Float {
        val to = (zoomLevel.targetValue * factor).coerceIn(1f, MaxZoom)
        if (to != 1f) moved = true
        return to
    }

    /** A release with ([vx], [vy]) in radii per second: the globe coasts on [decay] in the same direction. */
    internal suspend fun fling(vx: Float, vy: Float, decay: DecayAnimationSpec<Float>) = coroutineScope {
        dragView = null
        latitude.updateBounds(-GlobeGeo.MaxDragLatitude.toFloat(), GlobeGeo.MaxDragLatitude.toFloat())
        launch { longitude.animateDecay(-Math.toDegrees(vx.toDouble()).toFloat(), decay) }
        launch { latitude.animateDecay(Math.toDegrees(vy.toDouble()).toFloat(), decay) }
    }

    internal fun endDrag() {
        dragView = null
    }

    companion object {
        /** The furthest a globe zooms in: a continent fills the view, before the halftone dots fall apart. */
        const val MaxZoom: Float = 3f
    }
}

/** A [RouteGlobeState] that starts looking at [initial] (the globe re-frames itself on its route at once). */
@Composable
fun rememberRouteGlobeState(initial: GlobeView = GlobeView(20.0, 0.0)): RouteGlobeState = remember { RouteGlobeState(initial) }
