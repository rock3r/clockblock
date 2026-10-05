package dev.sebastiano.clockblocker.opus.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Every destination of the app. Back stacks hold these keys and are saved across configuration changes and
 * process death (see [AppRouteSavedStateConfiguration]).
 */
@Serializable
sealed interface AppRoute : NavKey

/** First-run (or replayed) onboarding. Full screen, no navigation suite. */
@Serializable
data object OnboardingRoute : AppRoute

/** The plan relevant right now (in progress, or the next one). Root of the "Now" destination. */
@Serializable
data object NowRoute : AppRoute

/** All trips. Root of the "Trips" destination; the list pane on large screens. */
@Serializable
data object TripsRoute : AppRoute

/** The plan of one trip. The detail pane next to [TripsRoute] on large screens. */
@Serializable
data class PlanRoute(val tripId: String) : AppRoute

/**
 * The trip editor, always full screen. [tripId] null creates a new trip; [returnOfTripId] prefills the return
 * trip of that outbound trip.
 */
@Serializable
data class TripEditorRoute(val tripId: String? = null, val returnOfTripId: String? = null) : AppRoute

/** Settings. Root of the "Settings" destination. */
@Serializable
data object SettingsRoute : AppRoute

@Serializable
data object AboutRoute : AppRoute

@Serializable
data object LicensesRoute : AppRoute

/**
 * Closed polymorphism for [NavKey]: back stacks are (de)serialized with compile-time serializers instead of the
 * reflective default, so they survive R8 without keep rules.
 */
val AppRouteSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(OnboardingRoute::class)
            subclass(NowRoute::class)
            subclass(TripsRoute::class)
            subclass(PlanRoute::class)
            subclass(TripEditorRoute::class)
            subclass(SettingsRoute::class)
            subclass(AboutRoute::class)
            subclass(LicensesRoute::class)
        }
    }
}
