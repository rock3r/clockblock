# Opus Clockblock — Android Technology Research (as of 2026-10-04)

All versions below were checked live against `dl.google.com/android/maven2` (Google Maven),
`repo1.maven.org` (Maven Central), `services.gradle.org`, the official Android KB (`android docs`
CLI) and **actual library sources** (sources JARs of the published artifacts + a sparse clone of
`androidx/androidx` @ `androidx-main`, commit `11ece46a49d4`, 2026-10-02). API names in the code
snippets were verified against the **`1.0.0-alpha20` sources JARs** (the version we would ship),
and cross-checked with `androidx-main` `api/current.txt` / `restricted_current.txt`.

Legend: ✅ verified in source / metadata · ⚠️ caveat / risk · 🔒 `@RestrictTo(LIBRARY_GROUP)` API

---

## TL;DR recommendations

| Area | Recommendation |
|---|---|
| Build | AGP **9.4.1** (built-in Kotlin, new DSL), Gradle **9.8.0**, Kotlin **2.4.20**, KSP **2.3.12**, JDK toolchain **21** |
| SDK | `compileSdk = 37` (Android 17; 37.1/37.2 minor levels available), `targetSdk = 37`, **`minSdk = 29`** (forced by `remote-creation-compose` minSdk 29; also gives updatable tzdata APEX) |
| UI | Compose via **`compose-bom-alpha:2026.09.01`** (Compose 1.13.0-alpha03 + **material3 1.5.0-alpha29**, the only line where M3 Expressive APIs are public) — or stable BOM 2026.09.00 if you drop Expressive |
| Nav | **Navigation 3 `1.2.0`** + `lifecycle-viewmodel-navigation3 2.11.0` + `adaptive-navigation3 1.3.0` |
| DI | **Metro 1.4.5** (compiler plugin, no KSP, Kotlin 2.4.20 supported). Fallback: Hilt 2.60.1 |
| DB | **Room 3 (`androidx.room3`) 3.0.3** + `sqlite-bundled 2.7.1`; DataStore 1.2.1 for prefs |
| Widget | **Remote Compose** document → `RemoteViews(RemoteViews.DrawInstructions)` on API 36+ (checked via `DrawInstructions.getSupportedVersion() >= 6`), classic `RemoteViews` (bitmap dial + `TextClock`) fallback |
| Countdown | Android 16 **Live Update** (`ProgressStyle`, promoted ongoing) via `NotificationCompat` (core 1.19.1); Android 17 `MetricStyle` (platform only) |
| Tests | JVM: JUnit **6.1.3** (Jupiter) + Kotest **6.2.5** assertions/property + Turbine 1.2.1 + coroutines-test 1.11.0. Android-JVM: Robolectric **4.17** + Compose UI test + **Roborazzi 1.76.0** (JUnit4). Device: androidx.test + **UiAutomator 2.4.0** on **Gradle Managed Devices**; e2e smoke: **Maestro 2.11.0** and/or Android CLI **Journeys** |
| Airports | **OurAirports** (Public Domain) for airport list + **mwgg/Airports** (MIT, has `tz`) for IATA→IANA; **GeoNames cities15000** (CC-BY 4.0, has IANA tz) for city search. **Avoid OpenFlights** (repo is AGPL-3.0, data ODbL share-alike) |

---

## 1. Remote Compose (androidx.compose.remote) for App Widgets

### 1.1 Artifacts (all ✅ Google Maven, latest = `1.0.0-alpha20`, no stable yet)

Group `androidx.compose.remote` (group-index lists exactly these):

| Artifact | Purpose |
|---|---|
| `remote-core` | Wire format, operations, `CoreDocument` (entire API is 🔒 — `api/current.txt` is empty) |
| `remote-creation` (KMP; `-android`, `-jvm`) | Low-level writer: `RemoteComposeWriter`, `RemoteComposeContext`, `profile.RcPlatformProfiles` |
| `remote-creation-core` | DSL / modifiers for procedural creation |
| **`remote-creation-compose`** | **`@RemoteComposable` Compose API** (`RemoteBox/Column/Row/Text/Canvas/Image`, `RemoteModifier`, `RemoteFloat`…), `captureSingleRemoteDocument` / `captureRemoteDocument` |
| `remote-player-core`, `remote-player-view`, `remote-player-compose` | Embedded players (in-app rendering). For widgets the **platform** (launcher host) is the player |
| `remote-tooling-preview` | `RemoteContentPreview(modifier, profile, content)` — Studio/in-app preview of remote composables |
| `remote-testing` | test helpers |

(Also in androidx-main but **not yet published**: `compose/remote/foundation` — `RemoteBasicText`, `RemoteBasicIcon`… and `androidx.wear.compose.remote.material3` (`RemoteButton`) used by Wear Widgets.)

> [!WARNING]
> * `remote-creation-compose` AAR has **`minSdkVersion="29"`** (player-view is 26). Your app's minSdk must be ≥ 29.
> * All `remote-*` artifacts are version-locked to each other (`remote-core` declared as `[1.0.0-alpha20]` strict).
> * `glance-appwidget:1.3.0-alpha02` depends on `remote-creation/remote-core:1.0.0-alpha14`; mixing it with alpha20 will force-upgrade Glance's internal RC backend → binary-compat risk. **Don't combine Glance 1.3 alphas with direct remote-* alpha20** (use Glance 1.2.0 stable if you need Glance at all).
> * Compose deps of alpha20: runtime/ui/foundation **1.11.0**, `graphics-shapes 1.1.0` — compatible with Compose 1.12/1.13.

### 1.2 How the widget pipeline actually works (✅ from source)

1. Compose a `@RemoteComposable` tree and **capture** it into bytes:
   `androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument(context, creationDisplayInfo = createCreationDisplayInfo(context), profile = …, content = { … }): CapturedDocument` (suspend) → `CapturedDocument.bytes: ByteArray`, `.pendingIntents: IntObjectMap<PendingIntent>`.
   (`captureRemoteDocument(...)` returns a `Flow<ByteArray>` that re-emits on recomposition, de-duplicated.)
2. Wrap in the **platform** API (class added in **API 35**, enabled by feature flag `remote_document_support` — "Remote document support features in Q2 2025 release" = **Android 16 / API 36**):
   `RemoteViews(RemoteViews.DrawInstructions.Builder(listOf(bytes)).build())` → `AppWidgetManager.updateAppWidget(id, rv)`.
3. The launcher's built-in Remote Compose player renders it; time/animation expressions are **evaluated in the host**, no app process needed.
4. Clickables: `RemoteModifier.clickable(pendingIntentAction { ctx -> PendingIntent… })` — bind the captured `pendingIntents` with `rv.setOnClickPendingIntent(key, pi)` (exactly what Glance's `DrawInstructionRemoteViews` does with its action map).

Evidence in androidx sources:
* `compose/remote/integration-tests/demos/src/main/java/androidx/compose/remote/integration/demos/widget/ListRCWidget.kt` — reference `AppWidgetProvider` (`@RequiresApi(BAKLAVA)`, `captureSingleRemoteDocument(..., profile = RcPlatformProfiles.WIDGETS_V6)` on `Dispatchers.Main`, `RemoteViews(DrawInstructions(bytes))`, `goAsync{}` helper; manifest receiver `tools:targetApi="36"`, plain `appwidget-provider` XML).
* `remote-creation-compose/.../widgets/RemoteComposeWidget.kt`, `RCWidget.kt` — a ready-made `AppWidgetProvider` base class with lambda click support, but 🔒. KDoc of `RcPlatformProfiles.WIDGETS_V6`: *"Profile for Glance Widgets for Platform 16. This will be moved to the glance module when creation APIs are public, before stable APIs."*
* `glance-appwidget:1.3.0-alpha02` → `androidx.glance.appwidget.remotecompose.*` (`GlanceRemoteComposeTranslator`, `DrawInstructionRemoteViews`), uses `RcPlatformProfiles.WIDGETS_V7`.
* Wear OS 7 "Wear Widgets" (`GlanceWearWidgetService`) are also built with Remote Compose (`RemoteText`, `RemoteColumn`, `RemoteBox`, `pendingIntentAction`, `rememberMutableRemoteInt`, `.rdp/.rc/.rs`) — sample: `github.com/android/wear-os-samples/tree/main/WearWidget`.

**Profiles / document versions** (✅): `RcPlatformProfiles.WIDGETS_V6` (doc API level 6), `WIDGETS_V7` (level 7, both 🔒), public `ANDROIDX7/8/9` and `ANDROIDX` (= `CoreDocument.DOCUMENT_API_LEVEL`, currently 8 — for embedded players, **not** for the platform host).
Platform: `RemoteViews.DrawInstructions.getSupportedVersion()` returns `CoreDocument.getDocumentApiLevel()` — AOSP `android16-release` = **6**, `android16-qpr2-release` = **8**. ⇒ choose profile at runtime: `>= 7 → WIDGETS_V7`, `>= 6 → WIDGETS_V6`, else fallback.

### 1.3 Glance's Remote Compose backend (✅ glance 1.3.0-alpha02 source)

* `androidx.glance.Backend { RemoteView, RemoteCompose }` (🔒). Glance switches to RC only when **SDK ≥ 36** AND (the tree `requiresRemoteCompose()` — currently only `LazyColumn` with non-normal `verticalScrollMode` — OR the host passes debug option `androidx.glance.appwidget.forceBackend = 1`).
* Glance's RC translation covers Box/Row/Column/Text/Image/Button/M3 buttons/LazyColumn — **no canvas drawing, no time expressions**. ⇒ Glance cannot draw our animated 24h dial. Use **direct remote-creation-compose** for the dial widget; Glance (stable **1.2.0**) is fine for simple list/text widgets.

### 1.4 Public vs restricted API surface (androidx-main `api/current.txt`)

Public & useful: `RemoteCanvas`, `RemoteDrawScope.{drawArc, drawCircle, drawLine, drawOval, drawRect, drawRoundRect, drawTextOnCircle, usePaint, width, height, center, size}`, `RemotePaint { color; style; strokeWidth; strokeCap; strokeJoin; textSize; typeface }`, `RemoteFloat` arithmetic (`plus/minus/times/div/rem/min` with `RemoteFloat`, comparisons → `RemoteBoolean`, `toRemoteString(DecimalFormat)`), `RemoteFloatOperations` (`sin, cos, floor, toRad, toDeg, lerp, clamp, abs, …`), `Float.rf`, `Int.rdp`, `Int.rsp`, `Color.rc`, `String.rs`, `RemoteText`, `RemoteBox/Column/Row/FitBox/CollapsibleColumn/StateLayout/Image`, `RemoteModifier.{fillMaxSize, size, padding, background, border, clip, clickable, rotate(RemoteFloat), graphicsLayer, alpha, animateEnterExit, animationSpec, sharedElement, semantics}`, `animateRemoteFloatAsState`, `RemoteTweenSpec(durationMillis, RemoteEasing).animate(target)`, `remoteSpring`, `rememberMutableRemoteFloat/Int/Boolean/String`, `valueChange(...)`, `hostAction(...)`, `combinedAction(...)`, `pendingIntentAction { ctx -> pi }`, `RemoteTimeDefaults.defaultTimeString()` (live HH:mm text), `RemoteColor.createThemedRemoteColor(...)` (light/dark), `RemoteContentPreview`.

🔒 (usable with `@file:Suppress("RestrictedApiAndroidX")`; compiles fine — Kotlin does not enforce `@RestrictTo`, only lint): **`RemoteDrawScope.remote.time.{Hour(), Minutes(), Seconds(), ContinuousSec(), UtcOffset(), DayOfWeek(), DayOfMonth()}`**, `RemoteDrawScope.{rotate, translate, scale, withTransform, drawText, drawAnchoredText, drawPath, clipRect, drawConditionally}`, `RcPlatformProfiles.WIDGETS_V6/V7`, `RemoteComposeWidget`, `RemoteFloat.rem(Float)/times(Float)/plus(Float)`.

Time variables semantics (✅ `remote-core/.../RemoteContext.java`): `FLOAT_TIME_IN_MIN` = minutes since **local** midnight **0..1439**; `FLOAT_TIME_IN_HR` = 0..23; `FLOAT_TIME_IN_SEC` = 0..3599; `FLOAT_CONTINUOUS_SEC` = 0..3600 looping every hour (smooth); `FLOAT_OFFSET_TO_UTC` = device offset to UTC in **seconds**. The official sample `RemoteCanvasAnimationSample` (`remote-creation-compose/samples/.../RemoteCanvasSample.kt`) uses `remote.time.ContinuousSec()` + `sin()` for host-driven animation.

> [!IMPORTANT]
> There is **no public time source** in alpha20/main other than `RemoteTimeDefaults.defaultTimeString()`. A clock-driven hand requires the 🔒 `remote.time.*` accessor (the same path the official samples use). Isolate it in one file so it's trivial to migrate when the API goes public.

### 1.5 Animation inside Remote Compose (no app process)

* **Clock-driven**: any expression built from `remote.time.*` is re-evaluated by the host player (minute granularity for `Minutes()`; `ContinuousSec()` is smooth per-frame — use sparingly for battery; hosts may throttle).
* **State transitions**: `animateRemoteFloatAsState(target, RemoteTweenSpec(...))`, `RemoteTweenSpec.animate()`, `remoteSpring()`, `RemoteModifier.animateEnterExit()/animationSpec()/sharedElement()`; `RemoteStateLayout(remoteBool) { … }` + `clickable(valueChange(state, newValue))` → in-widget interactivity without an app round-trip.
* **Named state** (`rememberNamedRemoteFloat("name", …)` / `RemoteFloat.createNamedRemoteFloat`) for host-updatable values.

### 1.6 Minimal working code — 24h dial with animated hand + text + fallback

`widget/src/main/res/xml/dial_widget_info.xml`
```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp" android:minHeight="110dp"
    android:targetCellWidth="2" android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_dial_legacy"
    android:initialKeyguardLayout="@layout/widget_dial_legacy"
    android:previewLayout="@layout/widget_dial_legacy"
    android:description="@string/widget_dial_description"
    android:widgetCategory="home_screen|keyguard"
    android:widgetFeatures="reconfigurable|configuration_optional" />
```

`AndroidManifest.xml` (widget module)
```xml
<receiver android:name=".DialWidgetProvider" android:exported="false"
          android:label="@string/widget_dial_label">
    <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
    <meta-data android:name="android.appwidget.provider" android:resource="@xml/dial_widget_info" />
</receiver>
```

`DialWidgetState.kt` (plain data computed by `:core:circadian`, persisted by the app)
```kotlin
package dev.sebastiano.clockblocker.opus.widget

data class DialWidgetState(
    val destinationZoneId: String,          // IANA id, e.g. "Asia/Tokyo"
    val destinationUtcOffsetMinutes: Int,   // offset of destination *now* (re-capture on DST change)
    val lightWindowStartMinute: Int,        // 0..1439, destination-local
    val lightWindowEndMinute: Int,
    val nextActionLabel: String,            // e.g. "Seek light · 14:30"
)
```

`DialDocument.kt` — the Remote Compose content (the only file touching 🔒 API)
```kotlin
@file:Suppress("RestrictedApiAndroidX")
package dev.sebastiano.clockblocker.opus.widget

import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.cos
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.compose.state.sin
import androidx.compose.remote.creation.compose.text.RemoteTimeDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI

@RemoteComposable
@Composable
fun DialWidgetContent(state: DialWidgetState) {
    RemoteBox(
        modifier = RemoteModifier.fillMaxSize().background(Color(0xFF101418).rc).padding(8.rdp),
        contentAlignment = RemoteAlignment.Center,
    ) {
        RemoteColumn(
            modifier = RemoteModifier.fillMaxSize(),
            verticalArrangement = RemoteArrangement.spacedBy(4.rdp),
            horizontalAlignment = RemoteAlignment.CenterHorizontally,
        ) {
            // weight() is the RemoteColumnScope extension
            RemoteCanvas(modifier = RemoteModifier.fillMaxWidth().weight(1f)) {
                val radius = width.min(height) / 2.rf - 6.rf

                // Dial ring
                drawCircle(
                    paint = RemotePaint {
                        color = Color(0xFF3A4450).rc; style = PaintingStyle.Stroke; strokeWidth = 3.rf
                    },
                    radius = radius,
                )

                // Static light-exposure window (degrees, 0° = 3 o'clock, clockwise — android.graphics.Canvas semantics)
                val startDeg = state.lightWindowStartMinute / 1440f * 360f - 90f
                val sweepDeg =
                    ((state.lightWindowEndMinute - state.lightWindowStartMinute + 1440) % 1440) / 1440f * 360f
                drawArc(
                    paint = RemotePaint {
                        color = Color(0xFFFFC94D).rc; style = PaintingStyle.Stroke
                        strokeWidth = 8.rf; strokeCap = StrokeCap.Round
                    },
                    startAngle = startDeg.rf,
                    sweepAngle = sweepDeg.rf,
                    useCenter = false,
                    topLeft = RemoteOffset(center.x - radius, center.y - radius),
                    size = RemoteSize(radius * 2.rf, radius * 2.rf),
                )

                // --- Clock-driven hand, re-evaluated by the launcher every minute (no app process) ---
                val localMin = remote.time.Minutes()                    // 🔒 0..1439 device-local
                val deviceOffsetMin = remote.time.UtcOffset() / 60.rf   // 🔒 seconds → minutes
                // +2880 keeps the operand positive for every offset combination (-14h..+14h)
                val destMin =
                    (localMin - deviceOffsetMin + state.destinationUtcOffsetMinutes.rf + 2880.rf) % 1440.rf
                val angle = destMin * (2f * PI.toFloat() / 1440f).rf     // midnight at top, clockwise
                val handLen = radius - 10.rf
                drawLine(
                    paint = RemotePaint {
                        color = Color.White.rc; strokeWidth = 5.rf; strokeCap = StrokeCap.Round
                    },
                    start = center,
                    end = RemoteOffset(center.x + sin(angle) * handLen, center.y - cos(angle) * handLen),
                )
                drawCircle(paint = RemotePaint { color = Color.White.rc }, radius = 5.rf)
            }

            // Live device-local clock text (public API, evaluated by the host)
            RemoteText(text = RemoteTimeDefaults.defaultTimeString(), color = Color(0xFFB0BEC5).rc, fontSize = 11.rsp)
            // Static label, refreshed by re-capturing at plan event boundaries
            RemoteText(text = state.nextActionLabel.rs, color = Color.White.rc, fontSize = 13.rsp, maxLines = 1)
        }
    }
}
```
> A *live destination-time* text can be drawn inside the canvas with 🔒 `drawText(text, x, y, paint)` using
> `floor(destMin / 60.rf).toRemoteString(java.text.DecimalFormat("00")) + ":".rs + (destMin % 60.rf).toRemoteString(java.text.DecimalFormat("00"))`
> (`floor` from `...compose.state`; `toRemoteString(DecimalFormat)` and `RemoteString.plus(RemoteString)` are public).

`DialWidgetProvider.kt` — wiring + API branching + fallback
```kotlin
@file:OptIn(ExperimentalRemoteCreationComposeApi::class, ExperimentalRemoteCreationApi::class)
@file:Suppress("RestrictedApiAndroidX")
package dev.sebastiano.clockblocker.opus.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.remote.creation.ExperimentalRemoteCreationApi
import androidx.compose.remote.creation.compose.ExperimentalRemoteCreationComposeApi
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.capture.createCreationDisplayInfo
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DialWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val state = WidgetStateRepository.load(context) // DataStore, written by the app
                ids.forEach { id -> manager.updateAppWidget(id, buildRemoteViews(context, state)) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun buildRemoteViews(context: Context, state: DialWidgetState): RemoteViews {
            val profile = remoteComposeProfileOrNull()
            return if (profile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                remoteComposeViews(context, state, profile)
            } else {
                LegacyDialRemoteViews.build(context, state) // API 29..35 or host without RC support
            }
        }

        /** Pick the widget profile the *platform* player understands. */
        private fun remoteComposeProfileOrNull(): Profile? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return null // RC host shipped in API 36
            val supported = RemoteViews.DrawInstructions.getSupportedVersion() // = CoreDocument API level
            return when {
                supported >= 7 -> RcPlatformProfiles.WIDGETS_V7
                supported >= 6 -> RcPlatformProfiles.WIDGETS_V6
                else -> null
            }
        }

        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        private suspend fun remoteComposeViews(context: Context, state: DialWidgetState, profile: Profile): RemoteViews {
            val app = context.applicationContext
            val doc = withContext(Dispatchers.Main) { // androidx demo captures on Main
                captureSingleRemoteDocument(
                    context = app,
                    creationDisplayInfo = createCreationDisplayInfo(app),
                    profile = profile,
                ) { DialWidgetContent(state) }
            }
            val rv = RemoteViews(RemoteViews.DrawInstructions.Builder(listOf(doc.bytes)).build())
            doc.pendingIntents.forEach { key, pi -> rv.setOnClickPendingIntent(key, pi) }
            return rv
        }
    }
}
```

`LegacyDialRemoteViews.kt` — fallback (API 29–35, or host without RC support)
```kotlin
package dev.sebastiano.clockblocker.opus.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin

object LegacyDialRemoteViews {
    fun build(context: Context, s: DialWidgetState, sizePx: Int = 360): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_dial_legacy).apply {
            setImageViewBitmap(R.id.dial, renderDial(s, sizePx))      // hand frozen at render time
            setTextViewText(R.id.next_action, s.nextActionLabel)
            // TextClock keeps ticking inside the launcher without app updates (setTimeZone is @RemotableViewMethod)
            setString(R.id.dest_clock, "setTimeZone", s.destinationZoneId)
        }

    private fun renderDial(s: DialWidgetState, size: Int): Bitmap = createBitmap(size, size).also { bmp ->
        val c = Canvas(bmp); val r = size / 2f - 8f; val cx = size / 2f; val cy = size / 2f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f; color = 0xFF3A4450.toInt()
        }
        c.drawCircle(cx, cy, r, ring)
        val arc = Paint(ring).apply { strokeWidth = 10f; color = 0xFFFFC94D.toInt(); strokeCap = Paint.Cap.ROUND }
        val start = s.lightWindowStartMinute / 1440f * 360f - 90f
        val sweep = ((s.lightWindowEndMinute - s.lightWindowStartMinute + 1440) % 1440) / 1440f * 360f
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), start, sweep, false, arc)
        val nowUtcMin = (Instant.now().epochSecond / 60 % 1440).toInt()
        val dest = ((nowUtcMin + s.destinationUtcOffsetMinutes) % 1440 + 1440) % 1440
        val a = dest / 1440.0 * 2 * Math.PI
        val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 6f; color = 0xFFFFFFFF.toInt(); strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(cx, cy, (cx + sin(a) * (r - 12)).toFloat(), (cy - cos(a) * (r - 12)).toFloat(), hand)
    }
}
```
`res/layout/widget_dial_legacy.xml`: `FrameLayout` with `ImageView @id/dial`, `TextClock @id/dest_clock` (`android:format24Hour="HH:mm"`, `android:format12Hour="h:mm a"`), `TextView @id/next_action`. On the legacy path refresh the bitmap every 15–30 min with an **inexact** alarm or WorkManager (`PeriodicWorkRequest`, min 15 min) and at every plan event boundary. (Alternative on API 31+: `AnalogClock` in RemoteViews with custom dial/hand icons + `android:timeZone` — but it is a 12 h clock.)

> [!TIP]
> In-app preview / screenshot tests of the widget content: wrap `DialWidgetContent` in `androidx.compose.remote.tooling.preview.RemoteContentPreview(profile = RcPlatformProfiles.WIDGETS_V6) { … }` in a normal `@Preview` or a Roborazzi test. Apps targeting Android 17 get a strictly enforced **bitmap memory limit in RemoteViews** — keep the legacy bitmap small.

### 1.7 Updating

* App writes the derived `DialWidgetState` (DataStore) whenever a plan changes → `AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, DialWidgetProvider::class.java))` → `updateAppWidget(id, buildRemoteViews(...))`.
* RC path: the hand moves by itself; re-capture only when **labels/arcs change** (event boundaries, DST change at destination, `ACTION_TIMEZONE_CHANGED`, `ACTION_TIME_CHANGED`, `ACTION_LOCALE_CHANGED`, configuration/dark-mode change).
* Schedule boundary updates with `AlarmManager.setWindow(...)` / `setAndAllowWhileIdle` (inexact is fine for widgets) or one-time WorkManager work.
* Generated widget-picker previews (API 35+): `AppWidgetManager.setWidgetPreview(provider, WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_KEYGUARD, remoteViews)`.

---

## 2. Lock-screen widgets & Live Updates

### 2.1 `widgetCategory` (✅ AOSP `core/res/res/values/attrs.xml`, `AppWidgetProviderInfo` reference)

| XML flag | Constant | Value | Since |
|---|---|---|---|
| `home_screen` | `WIDGET_CATEGORY_HOME_SCREEN` | 0x1 | 17 |
| `keyguard` | `WIDGET_CATEGORY_KEYGUARD` | 0x2 | 17 |
| `searchbox` | `WIDGET_CATEGORY_SEARCHBOX` | 0x4 | 21 |
| `not_keyguard` | `WIDGET_CATEGORY_NOT_KEYGUARD` | 0x8 | **36** — "should never be shown on the keyguard. Some keyguard style features may decide that `WIDGET_CATEGORY_KEYGUARD` isn't required to be added by an app to show on the feature when chosen by a user." |

`widgetFeatures`: `reconfigurable` (0x1), `hide_from_picker` (0x2), `configuration_optional` (0x4).
Other relevant attrs/fields: `android:initialKeyguardLayout`, `generatedPreviewCategories` (API 35).

**Where it shows today**: SystemUI's **communal hub / "Hub mode"** (lock-screen widgets on tablets / docked & charging devices) launches the launcher widget picker with `AppWidgetManager.EXTRA_CATEGORY_FILTER = WIDGET_CATEGORY_KEYGUARD | WIDGET_CATEGORY_HOME_SCREEN` (✅ `packages/SystemUI/src/com/android/systemui/communal/data/model/CommunalWidgetCategories.kt`, `CommunalEditModeViewModel.kt`). So home-screen widgets are eligible unless they opt out with `not_keyguard`. The public Glance/Views docs still say "for Android 5.0+ only `home_screen` is valid" — outdated. ⚠️ No public developer doc found for *phone* lock-screen widgets on Android 17; treat as device/OEM dependent.

**Recommendation**: `android:widgetCategory="home_screen|keyguard"`, provide `initialKeyguardLayout`, show no sensitive data (dial + next action only), read `AppWidgetManager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY)` to adapt density/contrast.

### 2.2 Live Updates / progress-centric notifications (✅ KB `develop/ui/views/notifications/live-update`, API refs)

* `Notification.ProgressStyle` — **API 36** (segments, points, tracker icon). AndroidX: `NotificationCompat.ProgressStyle` (✅ in `androidx.core:core:1.19.1`).
* `Notification.MetricStyle` — **API 37** (timers, stopwatch, travel; `addMetric`, `setCriticalMetric`) — platform only (not in core 1.19.1).
* Promotion requirements: Standard/BigText/Call/Progress/Metric style; manifest `android.permission.POST_PROMOTED_NOTIFICATIONS`; `NotificationCompat.Builder.setRequestPromotedOngoing(true)` (or `Notification.EXTRA_REQUEST_PROMOTED_ONGOING`); `setOngoing(true)`; `contentTitle` set; **no custom RemoteViews**; not group summary; not `setColorized(true)`; channel not `IMPORTANCE_MIN`.
* Checks: `NotificationManagerCompat.canPostPromotedNotifications()`, `NotificationCompat.hasPromotableCharacteristics(n)`, `Notification.FLAG_PROMOTED_ONGOING`; settings deep link `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS`.
* Status chip: `setShortCriticalText("14:30")` (< 7 chars shows fully, chip max 96dp) or `setWhen(t)` + `setUsesChronometer(true)` + `setChronometerCountDown(true)` for a ticking countdown.
* ⚠️ Policy: Live Updates must be **ongoing, user-initiated, time-sensitive**; "upcoming calendar events" are explicitly inappropriate. Use it only for an **active** protocol window (e.g. "Avoid light — 42 min left", "Sleep window") during the trip, with an Unpin action; use normal notifications for reminders.
* Android 17 adds a Live Update **semantic color API** (safety/danger/caution).
* Sample: `github.com/android/platform-samples/tree/main/samples/user-interface/live-updates`.

---

## 3. Material 3 Expressive in Compose

✅ Verified in sources: **`material3:1.4.0` (stable, in BOM 2026.09.00) has the Expressive APIs `internal`** (`internal fun MaterialExpressiveTheme`, `internal fun expressive()`, `internal fun FlexibleBottomAppBar`; `ButtonGroup`/`LoadingIndicator`/`FloatingToolbar` absent).
They are **public in `androidx.compose.material3:material3:1.5.0-alpha29`** (latest on Google Maven), which requires `foundation 1.13.0-alpha01` ⇒ use **`androidx.compose:compose-bom-alpha:2026.09.01`** (maps material3 1.5.0-alpha29, Compose UI/runtime/foundation 1.13.0-alpha03, adaptive 1.4.0-alpha02, adaptive-navigation3 1.4.0-alpha02).

| API (package `androidx.compose.material3`) | Status in 1.5.0-alpha29 |
|---|---|
| `MaterialExpressiveTheme(colorScheme, motionScheme, shapes, typography, content)` | public |
| `MotionScheme.expressive()` / `MotionScheme.standard()` | public |
| `ButtonGroup`, `ToggleButton`, `SplitButtonLayout` (+ `SplitButtonDefaults`) | public |
| `HorizontalFloatingToolbar` / `VerticalFloatingToolbar`, `FloatingActionButtonMenu` | public |
| `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator` | public |
| `LoadingIndicator`, `ContainedLoadingIndicator` | `@ExperimentalMaterial3ExpressiveApi` |
| `MaterialShapes` (sealed class with presets, `.toShape()`) | `@ExperimentalMaterial3ExpressiveApi` |
| `FlexibleBottomAppBar`, `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar` | public |
| `ShortNavigationBar`, `WideNavigationRail` | public (also in 1.4.0) |

(Opt-in status determined from annotations directly preceding each declaration; the compiler will flag any remaining opt-ins.)
Shape morphing: `androidx.graphics:graphics-shapes:1.1.0` (`RoundedPolygon`, `Morph`, `star()`, `circle()`), with M3 `MaterialShapes` presets.

Adaptive: `androidx.compose.material3.adaptive:adaptive / adaptive-layout / adaptive-navigation` **1.3.0** (1.4.0-alpha02), `adaptive-navigation3` **1.3.0** (`ListDetailSceneStrategy`, `rememberListDetailSceneStrategy`, `SupportingPaneSceneStrategy`, `rememberSupportingPaneSceneStrategy`, metadata `listPane()/detailPane()/extraPane()`), `androidx.compose.material3:material3-adaptive-navigation-suite` 1.4.0 / 1.5.0-alpha29 (`NavigationSuiteScaffold`, `NavigationSuiteScaffoldLayout`, `NavigationSuiteItem`, `rememberNavigationSuiteScaffoldState`). Window size: `currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)` (`androidx.window:window-core 1.5.1`).

> [!CAUTION]
> Choosing Expressive = shipping on Compose **alpha**. Mitigation: pin the alpha BOM, run screenshot tests on every bump. Alternative: stable BOM 2026.09.00 + material3 1.4.0 (`ShortNavigationBar`, `WideNavigationRail` available; no ButtonGroup/LoadingIndicator/MaterialShapes).

---

## 4. Core stack (✅ all from Maven metadata)

| Thing | Coordinates | Version (stable) | Notes |
|---|---|---|---|
| AGP | `com.android.application` / `.library` / `.test` | **9.4.1** (9.5.0-alpha08) | Max API 37; min Gradle **9.6.0**; JDK 17+. **Built-in Kotlin**: do NOT apply `org.jetbrains.kotlin.android` (fails: "no longer required for Kotlin support since AGP 9.0"); `kotlin-kapt` → KSP (or `com.android.legacy-kapt`). New DSL `compileSdk { version = release(37) }` (minor: `release(37) { minorApiLevel = 1 }`). AGP 10 will make the new Variant API mandatory |
| Gradle wrapper | — | **9.8.0** | |
| Kotlin | `org.jetbrains.kotlin.*` | **2.4.20** (2.5.0-Beta1) | K2. Plugins: `plugin.compose`, `plugin.serialization`, `jvm` for pure modules |
| KSP | `com.google.devtools.ksp` | **2.3.12** | KSP2, version-decoupled from Kotlin |
| Compose BOM | `androidx.compose:compose-bom` | **2026.09.00** (Compose 1.12.1, M3 1.4.0, adaptive 1.3.0) | `compose-bom-alpha` **2026.09.01** (1.13.0-alpha03, M3 1.5.0-alpha29) |
| activity-compose | `androidx.activity:activity-compose` | **1.13.0** (1.14.0-alpha03) | |
| Lifecycle | `androidx.lifecycle:lifecycle-runtime-compose`, `-viewmodel-compose`, `-viewmodel-navigation3` | **2.11.0** (2.12.0-alpha04) | |
| Navigation 3 | `androidx.navigation3:navigation3-runtime`, `navigation3-ui` | **1.2.0** (1.3.0-alpha01) | `NavDisplay`, `rememberNavBackStack`, `NavKey`, `entryProvider {}`/`entry<K>{}`, `rememberSaveableStateHolderNavEntryDecorator`, `rememberViewModelStoreNavEntryDecorator`, `DialogSceneStrategy`, `ResultEventBus`/`ResultEffect`, deep-link matchers |
| M3 adaptive nav3 | `androidx.compose.material3.adaptive:adaptive-navigation3` | **1.3.0** | |
| NavigationEvent | `androidx.navigationevent:navigationevent-compose` | 1.1.2 (1.2.0-rc01) | predictive back |
| DI (recommended) | plugin `dev.zacsweers.metro`; `dev.zacsweers.metro:metrox-android`, `metrox-viewmodel-compose` | **1.4.5** | Compiler plugin, compile-time graph validation, no KSP/kapt, KMP; compat table lists Kotlin 2.4.20 for Metro ≥1.2.0. Needs manual `WorkerFactory` for WorkManager |
| DI (alt.) | `com.google.dagger:hilt-android` + `hilt-compiler` (KSP), `androidx.hilt:hilt-lifecycle-viewmodel-compose` 1.4.0, `androidx.hilt:hilt-work` | **2.60.1** | Most mature, first-party integrations |
| DI (alt.) | `io.insert-koin:koin-bom` | 4.2.2 | runtime DI, no compile-time safety |
| Room 3 | `androidx.room3:room3-runtime`, `room3-compiler` (KSP), `room3-testing`, plugin `androidx.room3` | **3.0.3** (3.1.0-alpha01) | Kotlin/KSP-only, coroutines-first (DAOs `suspend`/`Flow`), KMP, `SQLiteDriver` only (`androidx.sqlite:sqlite-bundled 2.7.1` → `BundledSQLiteDriver`), migrations use `SQLiteConnection` |
| Room 2 (legacy) | `androidx.room:room-runtime` | 2.8.5 | |
| SQLDelight (alt.) | `app.cash.sqldelight` | 2.4.0 | |
| DataStore | `androidx.datastore:datastore-preferences` / `datastore` | **1.2.1** (1.3.0-alpha11) | |
| kotlinx-datetime | `org.jetbrains.kotlinx:kotlinx-datetime` | **0.8.0** | 0.7+ uses stdlib `kotlin.time.Instant`/`Clock` (`-0.6.x-compat` artifacts exist for migration) |
| kotlinx-serialization | `org.jetbrains.kotlinx:kotlinx-serialization-json` | **1.11.0** (1.12.0-RC) | |
| kotlinx-coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-core/-android/-test` | **1.11.0** | |
| WorkManager | `androidx.work:work-runtime-ktx`, `work-testing` | **2.12.0** | |
| core | `androidx.core:core-ktx` | **1.19.1** | `NotificationCompat.ProgressStyle`, promoted ongoing APIs |
| Splash | `androidx.core:core-splashscreen` | **1.2.0** | |
| Google Fonts | `androidx.compose.ui:ui-text-google-fonts` | 1.12.1 (via BOM) | needs `com_google_android_gms_fonts_certs` + GMS font provider; ⚠️ for F-Droid/de-Googled builds bundle fonts (`res/font`, e.g. variable Roboto Flex) instead |
| Glance | `androidx.glance:glance-appwidget`, `glance-material3`, `glance-appwidget-testing`, `glance-appwidget-preview` | **1.2.0** (1.3.0-alpha02) | |
| Remote Compose | `androidx.compose.remote:*` | **1.0.0-alpha20** | see §1 |
| graphics-shapes | `androidx.graphics:graphics-shapes` | 1.1.0 | |
| window-core | `androidx.window:window-core` | 1.5.1 (1.6.0-alpha05) | |
| desugaring | `com.android.tools:desugar_jdk_libs` | 2.1.5 | **not needed** with minSdk ≥ 26 for `java.time` |
| profileinstaller | `androidx.profileinstaller:profileinstaller` | 1.4.1 | |
| Baseline profile | plugin `androidx.baselineprofile`, `androidx.benchmark:benchmark-macro-junit4` | **1.5.0** | |
| MDC (XML themes only) | `com.google.android.material:material` | 1.14.0 | for `Theme.Material3.DayNight.NoActionBar` parent / splash |
| tracing | `androidx.tracing:tracing` | 2.0.3 | |

**SDK levels** (✅ SDK repository lists `platforms;android-37.0`, `37.1`, `37.2` (+ `37.2-beta*`), canary; API refs show `Notification.MetricStyle` "Added in API level 37"):
`compileSdk = 37`, `targetSdk = 37`, `minSdk = 29`. Rationale for 29: hard requirement of `remote-creation-compose`; Android 10+ receives **tzdata updates via the Mainline time-zone module** (Play system updates) → correct DST rules for a jet-lag app; `java.time` native, no desugaring.
Android 17 targetSdk changes worth noting: RemoteViews bitmap memory limit (widgets), static final fields unmodifiable via reflection, large-screen orientation/resizability opt-out removed (sw ≥ 600dp), local-network permission, Certificate Transparency on by default, lock-free `MessageQueue`.

### 4.1 Exact alarms guidance (✅ KB `develop/background-work/services/alarms`, `about/versions/14/changes/schedule-exact-alarms`)
* `USE_EXACT_ALARM` (install-time, non-revocable) is restricted by Google Play policy to apps whose **core function is an alarm clock or calendar** — a jet-lag planner should not rely on it (review risk).
* Use **`SCHEDULE_EXACT_ALARM`**: not pre-granted to fresh installs on Android 14+ (targetSdk ≥ 33). Always check `AlarmManager.canScheduleExactAlarms()`; request via `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` with an in-app rationale; listen to `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`; re-schedule on `BOOT_COMPLETED`, `TIMEZONE_CHANGED`, `TIME_SET`, `MY_PACKAGE_REPLACED`.
* Strategy: `setExactAndAllowWhileIdle()` (or `setAlarmClock()` for the wake/sleep anchors — visible in status bar, also exempt from Doze) when granted; graceful fallback to `setWindow(…, 10 min)` / `setAndAllowWhileIdle()`; WorkManager for non-time-critical work (plan regeneration, widget refresh). Notifications need `POST_NOTIFICATIONS` runtime permission (API 33+).

### 4.2 DI comparison

| | Metro 1.4.5 | Hilt 2.60.1 | Koin 4.2.2 |
|---|---|---|---|
| Mechanism | Kotlin compiler plugin (FIR + IR) | KSP codegen (Dagger) | runtime service locator |
| Build speed | fastest (no KSP round) | slowest | fast |
| Compile-time validation | yes (+ graph reports/HTML viewer) | yes | no (`verify()` tests) |
| KMP | yes | no | yes |
| Android glue | `metrox-android` (AppComponentFactory ctor injection), `metrox-viewmodel(-compose)`; Dagger interop artifact | first-party `hiltViewModel()`, `hilt-work`, testing rules | koin-androidx-compose |
| Risk | tied to Kotlin compiler versions (published compat table, best-effort N+.2) | historically lags AGP/Kotlin majors | runtime crashes |

**Pick Metro** for a modern OSS showcase; keep the graph small (repositories, `Clock`, alarm scheduler, dispatchers). The pure `:core:circadian` module stays DI-free (plain constructors).

---

## 5. Testing stack (✅ versions)

| Layer | Tools | Versions |
|---|---|---|
| Pure JVM (`:core:circadian`, `:core:model`) — TDD inner loop | **JUnit 6** (Jupiter) + **Kotest assertions + kotest-property** (property-based tests for phase-shift invariants: e.g. "plan never shifts > X h/day", "converges to destination phase"), **Turbine** for Flows, `kotlinx-coroutines-test` (`runTest`, virtual time), fake `kotlin.time.Clock` | junit-bom 6.1.3, kotest 6.2.5, turbine 1.2.1, coroutines-test 1.11.0; alt assertions AssertK 0.28.1 / Truth 1.4.5 |
| Android unit (JVM) | **Robolectric 4.17** (`android-all-instrumented` available up to `17-robolectric-…` = API 37) + `androidx.compose.ui:ui-test-junit4` + `ui-test-manifest`; ViewModels with Turbine; Room3 in-memory with `BundledSQLiteDriver`; `work-testing`; `glance-appwidget-testing` | Robolectric is **JUnit 4** — run via `junit-vintage-engine 6.1.3` alongside Jupiter, or apply `de.mannodermaus.android-junit5` **2.0.1** |
| Screenshot | **Roborazzi 1.76.0** (Robolectric Native Graphics; `recordRoborazziDebug` / `verifyRoborazziDebug` / `compareRoborazziDebug`; preview scanning support) — **recommended**. Alternative: **Compose Preview Screenshot Testing** `com.android.compose.screenshot` **0.0.1-alpha16** (AGP 9+, Kotlin 2.2.10+, JDK 17, `screenshotTest` source set, `android.experimental.enableScreenshotTest=true`, tasks `updateDebugScreenshotTest`/`validateDebugScreenshotTest`). Paparazzi 1.3.5 / 2.0.0-alpha05.1 — AGP 9/API 37 support uncertain → avoid | |
| Instrumented | `androidx.test:runner/core/rules` 1.7.0, `androidx.test.ext:junit` 1.3.0, `orchestrator` 1.6.1, `espresso-core` 3.7.0, Compose UI test, **UiAutomator 2.4.0** — new DSL `uiAutomator { startApp("pkg"); onElement { textAsString() == "Hello" }.click(); waitForStable() }` (`onElement / onElements / onElementOrNull`, `waitForAppToBeVisible`) — ideal for **widget / launcher / notification shade** e2e | |
| Devices in CI | **Gradle Managed Devices**: `android { testOptions { managedDevices { localDevices { create("pixel9api37") { device = "Pixel 9"; apiLevel = 37; systemImageSource = "aosp-atd" } } } } }` → `./gradlew pixel9api37DebugAndroidTest` (add an API 29 device for the widget fallback path) | |
| E2E / smoke | **Maestro 2.11.0** (YAML flows, black-box) for onboarding → create trip → plan → widget pin; **Android CLI Journeys** (agent-driven natural-language journeys with vision-based assertions, runnable from terminal and CI) as exploratory/regression layer; UiAutomator for deterministic widget checks | |
| Perf | Macrobenchmark + Baseline Profile (`androidx.baselineprofile` 1.5.0 in a `com.android.test` module, generated on GMD) | |

**Recommended TDD flow**: red/green in `:core:circadian` with JUnit 6 + Kotest (milliseconds); ViewModels with Turbine + fakes; UI with Robolectric Compose tests + committed Roborazzi goldens (including `RemoteContentPreview` of the widget); nightly GMD instrumented + Maestro flows.

---

## 6. Timezone & airport data

| Source | Content | License | Verdict |
|---|---|---|---|
| **OurAirports** `https://davidmegginson.github.io/ourairports-data/airports.csv` | ~80k airports, IATA/ICAO, type, lat/lon, municipality, country, `scheduled_service`; **no tz**; regenerated nightly | **Public Domain** | ✅ base list |
| **mwgg/Airports** `https://raw.githubusercontent.com/mwgg/Airports/master/airports.json` | 29,308 entries, **7,918 with IATA — all with IANA `tz`** | **MIT** | ✅ simplest IATA→IANA; attribute |
| OpenFlights `airports.dat` | 7,698 rows incl. Olson tz | GitHub repo **AGPL-3.0**; data under **ODbL** (attribution + share-alike) | ⚠️ avoid unless ODbL obligations accepted |
| **GeoNames** `https://download.geonames.org/export/dump/cities15000.zip` | cities ≥ 15k pop, alternate names, country, admin, **IANA timezone** column | **CC-BY 4.0** | ✅ city search; attribute "GeoNames" |
| timezone-boundary-builder | tz polygons (lat/lon→tz) | ODbL | only if arbitrary coordinates needed |

Pipeline: a Gradle task / script in `tools/` downloads OurAirports + mwgg, joins on IATA (fill missing tz via nearest GeoNames city in the same country), filters to `scheduled_service=yes` with IATA (~4–8k rows), writes a compact **pre-populated Room3 DB asset** (`createFromAsset`) with an FTS table for fuzzy search ("lon" → LHR/LGW/LCY/London). Unit-test that every tz id is in `ZoneId.getAvailableZoneIds()`. Show attributions in About.

java.time / tzdata: minSdk 29 ⇒ native `java.time`; never persist offsets — store **IANA zone ids** and compute offsets with `ZoneId.of(id).rules` / `kotlinx.datetime.TimeZone.of(id)` at use time; rules come from the device tzdata (Mainline-updatable since Android 10). Robolectric/JVM tests use the JDK's tzdata — pin the JDK via toolchains for reproducible tests.

---

## 7. Gradle setup

* **Version catalog** `gradle/libs.versions.toml` (draft below) + **convention plugins** in an included build `build-logic/convention`: `opus.android.application`, `opus.android.library`, `opus.android.library.compose`, `opus.android.feature`, `opus.jvm.library`, `opus.android.test`, `opus.metro`, `opus.room`, `opus.roborazzi`.
* Kotlin `jvmToolchain(21)`; with AGP built-in Kotlin configure via `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }` (no `kotlinOptions`, no `kotlin-android`).
* `gradle.properties`: `org.gradle.configuration-cache=true`, `org.gradle.caching=true`, `org.gradle.parallel=true`, `kotlin.code.style=official`.
* Modules:
  ```
  :app                         (application, Nav3 host, Metro graph, Expressive theme wiring)
  :core:model                  (kotlin-jvm, pure data types)
  :core:circadian              (kotlin-jvm, jet-lag algorithm; 100% unit-tested; no Android)
  :core:data                   (android lib: Room3, DataStore, airport DB asset, repositories)
  :core:designsystem           (Compose M3 Expressive theme, components, fonts, shapes)
  :core:notifications          (alarm scheduling, Live Updates, channels)
  :core:testing                (fakes, test dispatchers, Roborazzi rules)
  :feature:onboarding  :feature:trip  :feature:plan  :feature:settings
  :widget                      (Remote Compose + legacy RemoteViews widget)
  :baselineprofile             (com.android.test + androidx.baselineprofile)
  ```
* Release: `isMinifyEnabled = true`, `isShrinkResources = true`, R8 full mode (default), `proguard-android-optimize.txt`; consumer rules ship with kotlinx-serialization/Room3. Baseline profile from GMD + `profileinstaller`.
* Static analysis: Spotless 8.10.3 (ktfmt/ktlint), Detekt 1.23.8 (2.0.0-alpha.6 for K2-native) + `io.nlopez.compose.rules:detekt 0.6.7`, Kover 0.9.11, Android Lint with `warningsAsErrors` in CI (suppress `RestrictedApiAndroidX` only in `:widget`).

### 7.1 Draft `gradle/libs.versions.toml`

```toml
[versions]
# Build
agp = "9.4.1"
kotlin = "2.4.20"
ksp = "2.3.12"
# SDK (read by convention plugins)
compileSdk = "37"
targetSdk = "37"
minSdk = "29"
jvmToolchain = "21"

# Compose / UI
composeBom = "2026.09.00"            # stable (M3 1.4.0, Expressive APIs internal)
composeBomAlpha = "2026.09.01"       # Compose 1.13.0-alpha03 + material3 1.5.0-alpha29 (Expressive public) — RECOMMENDED here
activityCompose = "1.13.0"
lifecycle = "2.11.0"
navigation3 = "1.2.0"
material3AdaptiveNav3 = "1.3.0"      # the alpha BOM resolves 1.4.0-alpha02
navigationEvent = "1.1.2"
graphicsShapes = "1.1.0"
windowCore = "1.5.1"
coreKtx = "1.19.1"
coreSplashscreen = "1.2.0"
material = "1.14.0"                  # MDC XML themes only

# Widgets
remoteCompose = "1.0.0-alpha20"
glance = "1.2.0"

# Data
room3 = "3.0.3"
sqlite = "2.7.1"
datastore = "1.2.1"
work = "2.12.0"

# Kotlin libs
coroutines = "1.11.0"
serialization = "1.11.0"
datetime = "0.8.0"
collectionsImmutable = "0.5.2"

# DI
metro = "1.4.5"
hilt = "2.60.1"                      # alternative
androidxHilt = "1.4.0"               # alternative

# Perf
baselineProfile = "1.5.0"
benchmark = "1.5.0"
profileInstaller = "1.4.1"

# Testing
junit4 = "4.13.2"
junit6 = "6.1.3"
kotest = "6.2.5"
assertk = "0.28.1"
turbine = "1.2.1"
robolectric = "4.17"
roborazzi = "1.76.0"
composeScreenshot = "0.0.1-alpha16"
androidxTestCore = "1.7.0"
androidxTestRunner = "1.7.0"
androidxTestRules = "1.7.0"
androidxTestExtJunit = "1.3.0"
androidxTestOrchestrator = "1.6.1"
espresso = "3.7.0"
uiautomator = "2.4.0"
androidJunit5 = "2.0.1"
mockk = "1.14.11"

# Tooling
spotless = "8.10.3"
detekt = "1.23.8"
composeRulesDetekt = "0.6.7"
kover = "0.9.11"

[libraries]
# Compose (versions from BOM: use platform(libs.androidx.compose.bom.alpha))
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-bom-alpha = { module = "androidx.compose:compose-bom-alpha", version.ref = "composeBomAlpha" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-text-google-fonts = { module = "androidx.compose.ui:ui-text-google-fonts" }
androidx-compose-foundation = { module = "androidx.compose.foundation:foundation" }
androidx-compose-animation = { module = "androidx.compose.animation:animation" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-material3-adaptive-navigation-suite = { module = "androidx.compose.material3:material3-adaptive-navigation-suite" }
androidx-compose-material3-adaptive = { module = "androidx.compose.material3.adaptive:adaptive" }
androidx-compose-material3-adaptive-layout = { module = "androidx.compose.material3.adaptive:adaptive-layout" }
androidx-compose-material3-adaptive-navigation3 = { module = "androidx.compose.material3.adaptive:adaptive-navigation3", version.ref = "material3AdaptiveNav3" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
androidx-graphics-shapes = { module = "androidx.graphics:graphics-shapes", version.ref = "graphicsShapes" }
androidx-window-core = { module = "androidx.window:window-core", version.ref = "windowCore" }

androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "coreSplashscreen" }
google-material = { module = "com.google.android.material:material", version.ref = "material" }

androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
androidx-navigationevent-compose = { module = "androidx.navigationevent:navigationevent-compose", version.ref = "navigationEvent" }

# Widgets
androidx-compose-remote-creation-compose = { module = "androidx.compose.remote:remote-creation-compose", version.ref = "remoteCompose" }
androidx-compose-remote-creation = { module = "androidx.compose.remote:remote-creation", version.ref = "remoteCompose" }
androidx-compose-remote-core = { module = "androidx.compose.remote:remote-core", version.ref = "remoteCompose" }
androidx-compose-remote-player-view = { module = "androidx.compose.remote:remote-player-view", version.ref = "remoteCompose" }
androidx-compose-remote-tooling-preview = { module = "androidx.compose.remote:remote-tooling-preview", version.ref = "remoteCompose" }
androidx-compose-remote-testing = { module = "androidx.compose.remote:remote-testing", version.ref = "remoteCompose" }
androidx-glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
androidx-glance-material3 = { module = "androidx.glance:glance-material3", version.ref = "glance" }
androidx-glance-appwidget-testing = { module = "androidx.glance:glance-appwidget-testing", version.ref = "glance" }

# Data
androidx-room3-runtime = { module = "androidx.room3:room3-runtime", version.ref = "room3" }
androidx-room3-compiler = { module = "androidx.room3:room3-compiler", version.ref = "room3" }
androidx-room3-testing = { module = "androidx.room3:room3-testing", version.ref = "room3" }
androidx-sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-datastore = { module = "androidx.datastore:datastore", version.ref = "datastore" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-work-testing = { module = "androidx.work:work-testing", version.ref = "work" }

# Kotlin
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-serialization-core = { module = "org.jetbrains.kotlinx:kotlinx-serialization-core", version.ref = "serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "collectionsImmutable" }

# DI
metrox-android = { module = "dev.zacsweers.metro:metrox-android", version.ref = "metro" }
metrox-viewmodel-compose = { module = "dev.zacsweers.metro:metrox-viewmodel-compose", version.ref = "metro" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
androidx-hilt-lifecycle-viewmodel-compose = { module = "androidx.hilt:hilt-lifecycle-viewmodel-compose", version.ref = "androidxHilt" }

# Perf
androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileInstaller" }
androidx-benchmark-macro-junit4 = { module = "androidx.benchmark:benchmark-macro-junit4", version.ref = "benchmark" }

# Testing
junit4 = { module = "junit:junit", version.ref = "junit4" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junit6" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
junit-vintage-engine = { module = "org.junit.vintage:junit-vintage-engine" }
kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
kotest-property = { module = "io.kotest:kotest-property", version.ref = "kotest" }
assertk = { module = "com.willowtreeapps.assertk:assertk", version.ref = "assertk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
roborazzi-junit-rule = { module = "io.github.takahirom.roborazzi:roborazzi-junit-rule", version.ref = "roborazzi" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }
androidx-test-rules = { module = "androidx.test:rules", version.ref = "androidxTestRules" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExtJunit" }
androidx-test-orchestrator = { module = "androidx.test:orchestrator", version.ref = "androidxTestOrchestrator" }
androidx-test-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
androidx-test-uiautomator = { module = "androidx.test.uiautomator:uiautomator", version.ref = "uiautomator" }

# build-logic (convention plugins compile against these)
android-gradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
kotlin-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
compose-gradlePlugin = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { module = "com.google.devtools.ksp:symbol-processing-gradle-plugin", version.ref = "ksp" }
metro-gradlePlugin = { module = "dev.zacsweers.metro:gradle-plugin", version.ref = "metro" }
room3-gradlePlugin = { module = "androidx.room3:room3-gradle-plugin", version.ref = "room3" }
roborazzi-gradlePlugin = { module = "io.github.takahirom.roborazzi:roborazzi-gradle-plugin", version.ref = "roborazzi" }
compose-rules-detekt = { module = "io.nlopez.compose.rules:detekt", version.ref = "composeRulesDetekt" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
android-test = { id = "com.android.test", version.ref = "agp" }
# NOTE: no org.jetbrains.kotlin.android — AGP 9 built-in Kotlin
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
metro = { id = "dev.zacsweers.metro", version.ref = "metro" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room3 = { id = "androidx.room3", version.ref = "room3" }
baselineprofile = { id = "androidx.baselineprofile", version.ref = "baselineProfile" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
compose-screenshot = { id = "com.android.compose.screenshot", version.ref = "composeScreenshot" }
android-junit5 = { id = "de.mannodermaus.android-junit5", version.ref = "androidJunit5" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
```

`gradle/wrapper/gradle-wrapper.properties`: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.0-bin.zip`

All plugin markers verified on Maven Central/Google Maven: `com.google.devtools.ksp` 2.3.12, `dev.zacsweers.metro` 1.4.5, `io.github.takahirom.roborazzi` 1.76.0, `de.mannodermaus.android-junit5` 2.0.1, `org.jetbrains.kotlin.plugin.compose` 2.4.20, `androidx.room3` 3.0.3, `com.android.application`/`com.android.test` 9.4.1, `com.android.compose.screenshot` 0.0.1-alpha16, `androidx.baselineprofile` 1.5.0.

---

## 8. Open risks / re-verify during implementation

1. **Remote Compose is alpha and the widget path relies on 🔒 APIs** (`RcPlatformProfiles.WIDGETS_V6/V7`, `remote.time.*`). Suppress lint `RestrictedApiAndroidX` in `:widget` only. The androidx team states creation profiles will move into Glance before stable → expect a migration. Code snippet was written against verified signatures but has **not been compiled** — first task of the widget module should be a compile + Robolectric `RemoteContentPreview` smoke test.
2. Hosts: only launchers using the platform `AppWidgetHostView` with DrawInstructions support render RC (Pixel Launcher on 16+). Keep the legacy fallback; consider a user-visible "classic widget" toggle if an OEM host renders blank.
3. M3 Expressive requires the Compose **alpha** BOM.
4. Keyguard widget surfacing on **phones** is not publicly documented; hub mode (tablets/docked) is confirmed in SystemUI code.
5. Live Updates policy excludes "upcoming events" → only for an in-progress protocol window.
6. `USE_EXACT_ALARM` isn't appropriate for this category → `SCHEDULE_EXACT_ALARM` + inexact fallback.
7. Sources used for verification are cached locally (parent can inspect): sources JARs & androidx sparse clone in the research agent's local scratch space (not published).
