package dev.sebastiano.clockblocker.opus.feature.settings

import com.mikepenz.aboutlibraries.Libs

/**
 * A slice of the licence data the AboutLibraries plugin generates for `:app` (`res/raw/aboutlibraries.json`): a few
 * libraries plus our own entries (the app, vendored code, a font, a data source), with their licence texts. Fixed, so
 * the licences screen's tests and goldens don't change with every dependency bump.
 */
internal val FixtureLibraries: Libs by lazy {
    val json = checkNotNull(FixtureMarker::class.java.getResource("/licenses_fixture.json")) { "licenses_fixture.json missing" }.readText()
    Libs.Builder().withJson(json).build()
}

private object FixtureMarker
