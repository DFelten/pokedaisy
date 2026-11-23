package com.pokedaisy.app

import kotlin.test.*

class SettingsBackupTest {
    private val prefs = mapOf<String, Any?>(
        "dark_mode" to 1, "rewind" to true, "ff_max_speed" to 3f, "played_/sdcard/ROMs/fr.gba" to 1_700_000_000_000L,
        "app_theme" to 41, "romname_fr.gba" to "My \"Fire\" Red\nrun", "speed_1a2b3c4d" to 2,
        "ra_token" to "secret", "ss_password" to "hunter2", "steamgriddb_api_key" to "k", "setup_done" to true,
        "app_open_count" to 9, "weird" to setOf("a"),
    )
    private val files = mapOf(
        "controls.properties" to "A=BUTTON_A\n", "hotkeys.properties" to "ff=R2\n",
        "cheats/0123abcd.cheats" to "# codes\n", "../evil" to "x", "cheats/../../x.cheats" to "x",
    )

    @Test fun roundTripKeepsEveryTypeAndLeavesSecretsAndDeviceKeysOut() {
        val text = SettingsBackup.encode(prefs, files, "1.6.2", 42L)
        for (secret in listOf("secret", "hunter2", "ra_token", "steamgriddb_api_key", "setup_done", "app_open_count")) {
            assertFalse(secret in text, secret)
        }
        val back = SettingsBackup.decode(text)
        assertEquals(1, back.prefs["dark_mode"]); assertEquals(true, back.prefs["rewind"])
        assertEquals(3f, back.prefs["ff_max_speed"]); assertEquals(1_700_000_000_000L, back.prefs["played_/sdcard/ROMs/fr.gba"])
        assertEquals("My \"Fire\" Red\nrun", back.prefs["romname_fr.gba"]); assertEquals(2, back.prefs["speed_1a2b3c4d"])
        assertFalse("weird" in back.prefs)
        assertEquals(setOf("controls.properties", "hotkeys.properties", "cheats/0123abcd.cheats"), back.files.keys)
        assertEquals("1.6.2", back.appVersion); assertEquals(42L, back.exportedAt)
    }

    @Test fun aFileThatClaimsSecretsOrBadTypesIsReadWithoutThem() {
        val text = """{"format":"pokedaisy-settings","version":1,"prefs":{"ra_token":{"s":"x"},"rewind":{"b":"yes"},
            "dark_mode":{"i":2},"tips":{"b":false},"a":{"i":1,"s":"2"}},"files":{"/etc/passwd":"x","controls.properties":5}}"""
        val back = SettingsBackup.decode(text)
        assertEquals(mapOf<String, Any>("dark_mode" to 2, "tips" to false), back.prefs)
        assertTrue(back.files.isEmpty())
    }

    @Test fun notASettingsFileIsRefusedWithItsReason() {
        fun refusal(text: String) = assertFailsWith<SettingsBackup.NotSettings> { SettingsBackup.decode(text) }.refusal
        assertEquals(SettingsBackup.Refusal.NOT_SETTINGS, refusal("hello"))
        assertEquals(SettingsBackup.Refusal.NOT_SETTINGS, refusal("""{"format":"other","version":1}"""))
        assertEquals(SettingsBackup.Refusal.NEWER_APP, refusal("""{"format":"pokedaisy-settings","version":99}"""))
    }

    @Test fun aRestoreResetsWhatTheFileLeftAtItsDefaultAndKeepsTheDevicesOwn() {
        val backup = SettingsBackup.decode(SettingsBackup.encode(
            mapOf("rewind" to true, "saves_dir_override" to "/gone", "states_dir_override" to "/here"), emptyMap(), "1", 0))
        val edits = SettingsBackup.restoreEdits(setOf("rewind", "dark_mode", "setup_done", "ra_token"), backup) { it == "/here" }
        assertEquals(mapOf<String, Any?>("dark_mode" to null, "rewind" to true, "saves_dir_override" to null,
            "states_dir_override" to "/here"), edits)
    }

    @Test fun fileNameIsDated() {
        assertEquals("pokedaisy-settings-2026-01-09.json", SettingsBackup.fileName(2026, 1, 9))
    }
}
