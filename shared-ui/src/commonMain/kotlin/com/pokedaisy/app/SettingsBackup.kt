package com.pokedaisy.app

import kotlinx.serialization.json.*

/**
 * A settings file (Settings > APP > SETTINGS FILE, the first-time setup's first step): every preference the app keeps
 * plus the small text files that are settings too (GAME BUTTONS, HOTKEYS, each game's cheats), as one JSON file the
 * player keeps, to put them all back after a reinstall or on another device.
 *
 * Left out: sign-ins and keys ([SECRET_KEYS]: a file people pass around when asking for help must never carry them) and
 * what belongs to this device or this install ([DEVICE_KEYS], [DEVICE_PREFIXES]). Each value keeps its type, so a
 * restore writes back exactly what was there. Pure: the platform reads and writes its own storage ([read] / [write]
 * are its maps).
 */
object SettingsBackup {
    const val FORMAT = "pokedaisy-settings"
    const val VERSION = 1
    /** A settings file is a few hundred KB at most; anything past this isn't one. */
    const val MAX_BYTES = 8 * 1024 * 1024

    /** Never written to the file, never read from one. */
    val SECRET_KEYS = setOf(
        "ra_username", "ra_token", "ra_web_api_key", "ss_user", "ss_password", "steamgriddb_api_key",
    )

    /** This device's or this install's own: the setup's progress, counters, what was measured here, consent. */
    val DEVICE_KEYS = setOf(
        "setup_done", "setup_requested", "app_open_count", "new_feature_since", "light_companion_measured_v2",
        "fold_seen", "crash_reports_enabled", "crash_reports_asked",
    )
    private val DEVICE_PREFIXES = listOf("debug_")

    /** Text files kept beside the preferences, by their name in the file: GAME BUTTONS, HOTKEYS and each game's cheats. */
    const val CONTROLS_FILE = "controls.properties"
    const val HOTKEYS_FILE = "hotkeys.properties"
    private val CHEATS_FILE = Regex("cheats/[0-9a-f]{8}\\.cheats")

    /** The folder settings: a path that doesn't exist on this device is dropped on import (the default folder then). */
    val FOLDER_KEYS = setOf("saves_dir_override", "save_backups_dir", "states_dir_override")

    fun exportable(key: String): Boolean =
        key.isNotEmpty() && key.length <= 2048 && key !in SECRET_KEYS && key !in DEVICE_KEYS && DEVICE_PREFIXES.none(key::startsWith)

    fun fileAllowed(name: String): Boolean = name == CONTROLS_FILE || name == HOTKEYS_FILE || CHEATS_FILE.matches(name)

    data class Backup(
        /** Key -> Boolean / Int / Long / Float / String. */
        val prefs: Map<String, Any>,
        /** [fileAllowed] name -> its text. */
        val files: Map<String, String>,
        val appVersion: String?,
        val exportedAt: Long,
    )

    /** The file's text: the [prefs] worth keeping ([exportable]) and the [files] allowed. */
    fun encode(prefs: Map<String, Any?>, files: Map<String, String>, appVersion: String, exportedAt: Long): String {
        val values = buildJsonObject {
            for ((key, value) in prefs.toSortedMap()) {
                if (!exportable(key)) continue
                val typed = typed(value) ?: continue
                put(key, typed)
            }
        }
        val text = buildJsonObject {
            for ((name, body) in files.toSortedMap()) if (fileAllowed(name)) put(name, body)
        }
        return pretty.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("app", appVersion)
            put("exportedAt", exportedAt)
            put("prefs", values)
            put("files", text)
        })
    }

    /** Why a file can't be read as a settings file. */
    enum class Refusal { NOT_SETTINGS, NEWER_APP }
    class NotSettings(val refusal: Refusal) : IllegalArgumentException(refusal.name)

    /** Reads a settings file; throws [NotSettings] when it isn't one this app can read. */
    fun decode(text: String): Backup {
        if (text.length > MAX_BYTES) throw NotSettings(Refusal.NOT_SETTINGS)
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw NotSettings(Refusal.NOT_SETTINGS)
        if (root.str("format") != FORMAT) throw NotSettings(Refusal.NOT_SETTINGS)
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: 0
        if (version > VERSION) throw NotSettings(Refusal.NEWER_APP)
        if (version < 1) throw NotSettings(Refusal.NOT_SETTINGS)
        val prefs = LinkedHashMap<String, Any>()
        (root["prefs"] as? JsonObject)?.forEach { (key, element) ->
            if (!exportable(key)) return@forEach
            untyped(element)?.let { prefs[key] = it }
        }
        val files = LinkedHashMap<String, String>()
        (root["files"] as? JsonObject)?.forEach { (name, element) ->
            val body = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@forEach
            if (fileAllowed(name)) files[name] = body
        }
        return Backup(prefs, files, root.str("app"), (root["exportedAt"] as? JsonPrimitive)?.longOrNull ?: 0L)
    }

    /**
     * What a restore writes: every [exportable] key the device has now that the file doesn't is removed (back to its
     * default, as on the device the file came from), the file's keys are written, the device's own keys stay. A
     * [FOLDER_KEYS] path for which [folderExists] is false is dropped. Null = remove.
     */
    fun restoreEdits(current: Set<String>, backup: Backup, folderExists: (String) -> Boolean): Map<String, Any?> {
        val edits = LinkedHashMap<String, Any?>()
        for (key in current) if (exportable(key) && key !in backup.prefs) edits[key] = null
        for ((key, value) in backup.prefs) {
            edits[key] = if (key in FOLDER_KEYS && value is String && !folderExists(value)) null else value
        }
        return edits
    }

    /** A dated name for a new settings file. */
    fun fileName(year: Int, month: Int, day: Int): String =
        "pokedaisy-settings-$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}.json"

    private val pretty = Json { prettyPrint = true }

    private fun typed(value: Any?): JsonObject? = when (value) {
        is Boolean -> buildJsonObject { put("b", value) }
        is Int -> buildJsonObject { put("i", value) }
        is Long -> buildJsonObject { put("l", value) }
        is Float -> if (value.isFinite()) buildJsonObject { put("f", value) } else null
        is String -> buildJsonObject { put("s", value) }
        else -> null   // sets and anything else the store doesn't keep
    }

    private fun untyped(element: JsonElement): Any? {
        val o = element as? JsonObject ?: return null
        if (o.size != 1) return null
        val (type, raw) = o.entries.first()
        val p = raw as? JsonPrimitive ?: return null
        return when (type) {
            "b" -> p.booleanOrNull?.takeIf { !p.isString }
            "i" -> p.intOrNull?.takeIf { !p.isString }
            "l" -> p.longOrNull?.takeIf { !p.isString }
            "f" -> p.floatOrNull?.takeIf { !p.isString && it.isFinite() }
            "s" -> p.contentOrNull?.takeIf { p.isString }
            else -> null
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
