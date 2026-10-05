package dev.sebastiano.clockblocker.opus.navigation

/**
 * The independent back stacks of the app. [Now], [Trips] and [Settings] appear in the navigation suite;
 * [Onboarding] is a full-screen flow shown before the user has a profile.
 */
enum class TopLevelDestination(val root: AppRoute) {
    Onboarding(OnboardingRoute),
    Now(NowRoute),
    Trips(TripsRoute),
    Settings(SettingsRoute),
    ;

    companion object {
        /** Destinations shown in the navigation bar / rail, in display order. */
        val NavigationSuite: List<TopLevelDestination> = listOf(Now, Trips, Settings)
    }
}
