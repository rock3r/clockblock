package dev.sebastiano.clockblocker.opus

import dev.sebastiano.clockblocker.opus.core.data.datastore.StoreFiles
import dev.sebastiano.clockblocker.opus.core.notifications.SnoozeStore
import dev.sebastiano.clockblocker.opus.feature.plan.PreferencesCelebrationStore
import dev.sebastiano.clockblocker.opus.widget.WidgetUpdater
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.w3c.dom.Element
import java.io.File
import java.lang.reflect.Modifier
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Android backup and phone-to-phone transfer copy only what `data_extraction_rules.xml` lists. These tests tie
 * that list to the real store file names, so renaming or adding a store can't silently drop user data from
 * backups, and device-specific bookkeeping can't sneak in.
 */
class BackupRulesTest {

    private data class Rule(val domain: String, val path: String)

    private val rulesFile = File("src/main/res/xml/data_extraction_rules.xml")
    private val root: Element = parse(rulesFile)

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file).documentElement

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun section(name: String): Element = root.children(name).single()

    private fun Element.rules(tag: String): List<Rule> =
        children(tag).map { Rule(it.getAttribute("domain"), it.getAttribute("path")) }

    /** User data: every DataStore document, plus the celebrations that already played for those trips. */
    private val expectedIncludes: List<Rule> =
        StoreFiles.all.map { Rule("file", "${StoreFiles.Directory}/$it") } +
            Rule("sharedpref", "${PreferencesCelebrationStore.PREFS}.xml")

    @Test
    fun `StoreFiles lists every store it names`() {
        // A store added as a constant but forgotten in StoreFiles.all would otherwise miss the backup check below.
        val named = StoreFiles::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.isAccessible = true; it.get(null) as String }
            .filter { it.endsWith(".json") }
        StoreFiles.all shouldContainExactlyInAnyOrder named
    }

    @ParameterizedTest
    @ValueSource(strings = ["cloud-backup", "device-transfer"])
    fun `every user data store is included, and nothing else`(sectionName: String) {
        section(sectionName).rules("include") shouldContainExactlyInAnyOrder expectedIncludes
    }

    @ParameterizedTest
    @ValueSource(strings = ["cloud-backup", "device-transfer"])
    fun `device-specific bookkeeping stays on the device`(sectionName: String) {
        val included = section(sectionName).rules("include")
        included shouldNotContain Rule("sharedpref", "${SnoozeStore.PREFS}.xml")
        included shouldNotContain Rule("sharedpref", "${WidgetUpdater.PREFS}.xml")
        // An include list already leaves everything else out; excludes would only hide mistakes.
        section(sectionName).rules("exclude").shouldBeEmpty()
    }

    @Test
    fun `cloud backups are made only when they are end-to-end encrypted`() {
        section("cloud-backup").getAttribute("disableIfNoEncryptionCapabilities") shouldBe "true"
    }

    @Test
    fun `the manifest allows backup and points at the rules`() {
        val application = parse(File("src/main/AndroidManifest.xml")).children("application").single()
        val android = "http://schemas.android.com/apk/res/android"
        application.getAttributeNS(android, "allowBackup") shouldBe "true"
        application.getAttributeNS(android, "dataExtractionRules") shouldBe "@xml/data_extraction_rules"
    }
}
