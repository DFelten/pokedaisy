package com.pokedaisy.app.companion.ui.theme

import androidx.compose.ui.graphics.Color
import com.pokedaisy.app.companion.data.delete
import com.pokedaisy.app.companion.data.isFile
import com.pokedaisy.app.companion.data.listFiles
import com.pokedaisy.app.companion.data.mkdirs
import com.pokedaisy.app.companion.data.readBytes
import com.pokedaisy.app.companion.data.readText
import com.pokedaisy.app.companion.data.writeBytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull

/**
 * A theme as the player made or added it: its colours (only [name] and [color] needed, the rest worked out from the
 * colour when not given) and its own font ([fontFile], a file in MY LOOK's fonts). Its pictures are files beside it
 * ([ThemeFiles]). The JSON is docs/manual/screen.md's "Your own themes" format:
 * ```
 * { "name": "MIDNIGHT", "color": "#3D6FE0" }
 * ```
 * Optional: `tint` (0-1, how much the windows take the colour, 0.5), `darkColor` (values in DARK MODE), `page` and
 * `darkPage` (the backdrop's colour; true = a dark page with white text), `glows` (two to five colours drifting over it),
 * `gameTint` (over a game's backdrop; null = none), `font` (a pack's font file's name).
 */
data class ThemeDraft(
    val name: String,
    val color: Color,
    val tint: Float = 0.5f,
    val darkPage: Boolean = false,
    val darkColor: Color? = null,
    val page: Color? = null,
    val glows: List<Color>? = null,
    val gameTint: Color? = null,
    /** `"gameTint": null`: nothing over a game's backdrop. */
    val noGameTint: Boolean = false,
    val fontFile: String? = null,
) {
    /** The theme this draft gives, as [id]. */
    fun spec(id: Int): ThemeSpec {
        val hue = hueOf(color)
        val page = page ?: if (darkPage) hslColor(hue, 0.35f, 0.12f) else hslColor(hue, 0.6f, 0.97f)
        val glows = glows?.take(5)?.takeIf { it.size >= 2 }
            ?: if (darkPage) listOf(hslColor(hue, 0.5f, 0.28f), hslColor(hue + 50f, 0.45f, 0.26f), hslColor(hue - 50f, 0.45f, 0.24f))
            else listOf(hslColor(hue, 0.85f, 0.86f), hslColor(hue + 40f, 0.85f, 0.88f), hslColor(hue - 40f, 0.8f, 0.87f))
        return ThemeSpec(
            id = id, label = name, hue = hue, wash = tint.coerceIn(0f, 1f),
            value = color, valueNight = darkColor ?: brighter(color),
            page = page, blobs = glows, darkPage = darkPage,
            gameWash = if (noGameTint) null else gameTint ?: hslColor(hue, 0.8f, 0.9f),
        )
    }

    /** A new [color]: what was worked out from the old one goes, so the whole theme follows. */
    fun recolored(c: Color) = copy(color = c, darkColor = null, page = null, glows = null, gameTint = null)

    fun toJson(withFont: String? = fontFile, fontKey: String = "fontFile"): String {
        val m = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        m["name"] = JsonPrimitive(name)
        m["color"] = JsonPrimitive(hex(color))
        if (tint != 0.5f) m["tint"] = JsonPrimitive(((tint * 100).toInt()) / 100f)
        if (darkPage) m["darkPage"] = JsonPrimitive(true)
        darkColor?.let { m["darkColor"] = JsonPrimitive(hex(it)) }
        page?.let { m["page"] = JsonPrimitive(hex(it)) }
        glows?.let { g -> m["glows"] = JsonArray(g.map { JsonPrimitive(hex(it)) }) }
        if (noGameTint) m["gameTint"] = JsonNull else gameTint?.let { m["gameTint"] = JsonPrimitive(hex(it)) }
        withFont?.let { m[fontKey] = JsonPrimitive(it) }
        return JsonObject(m).toString()
    }

    companion object {
        const val MAX_NAME = 20

        /** A theme file's draft; throws [LookImportError] with what's wrong. */
        fun of(text: String): ThemeDraft {
            if (text.length > LookLimits.THEME_CHARS) throw LookImportError("That file is too big for a theme")
            val root = runCatching { Json.parseToJsonElement(text) }.getOrElse { throw LookImportError("Not a theme file: it isn't JSON") }
                as? JsonObject ?: throw LookImportError("Not a theme file: it isn't JSON")
            val name = cleanName(root.string("name") ?: "") ?: throw LookImportError("The theme has no name")
            val color = root.color("color") ?: throw LookImportError("The theme has no color")
            return ThemeDraft(
                name = name, color = color,
                tint = root.number("tint")?.coerceIn(0f, 1f) ?: 0.5f,
                darkPage = root.bool("darkPage") ?: false,
                darkColor = root.color("darkColor"),
                page = root.color("page"),
                glows = (root["glows"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.let(CustomThemes::parseColor) }
                    ?.take(5)?.takeIf { it.size >= 2 },
                gameTint = if (root["gameTint"] is JsonNull) null else root.color("gameTint"),
                noGameTint = root["gameTint"] is JsonNull,
                fontFile = root.string("fontFile")?.takeIf { it.isNotBlank() && '/' !in it && '\\' !in it },
            )
        }

        /** A theme's name as kept: capitals, no '|', at most [MAX_NAME] letters; null when nothing's left. */
        fun cleanName(raw: String): String? =
            raw.filter { it != '|' && !it.isISOControl() }.trim().uppercase().take(MAX_NAME).trim().takeIf { it.isNotEmpty() }

        fun hex(c: Color): String {
            val v = (((c.alpha * 255).toInt() and 0xFF) shl 24) or (((c.red * 255).toInt() and 0xFF) shl 16) or
                (((c.green * 255).toInt() and 0xFF) shl 8) or ((c.blue * 255).toInt() and 0xFF)
            val rgb = (v and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
            return if (c.alpha >= 0.999f) "#$rgb" else "#" + ((v ushr 24) and 0xFF).toString(16).padStart(2, '0').uppercase() + rgb
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun JsonObject.number(key: String): Float? = (this[key] as? JsonPrimitive)?.floatOrNull
        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
        private fun JsonObject.color(key: String): Color? {
            val raw = string(key) ?: return null
            return CustomThemes.parseColor(raw) ?: throw LookImportError("\"{0}\" isn't a color like #3D6FE0", key)
        }

        /** A colour light enough for values on DARK MODE's slate windows. */
        private fun brighter(c: Color): Color {
            val l = (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue)
            return if (l >= 0.6f) c else hslColor(hueOf(c), 1f, 0.7f)
        }
    }
}

/** A theme file's text read the old way (MY LOOK's first themes; still what a bare .json is). */
object CustomThemes {
    fun parse(text: String, id: Int): ThemeSpec = ThemeDraft.of(text).spec(id)

    /** [text] with its name set to [name] (a second theme of one name gets a number). */
    fun withName(text: String, name: String): String = ThemeDraft.of(text).copy(name = name).toJson()

    /** "#RRGGBB" or "#AARRGGBB" (the # optional), else null. */
    fun parseColor(s: String): Color? {
        val hex = s.trim().removePrefix("#")
        if (hex.length != 6 && hex.length != 8 || hex.any { it.digitToIntOrNull(16) == null }) return null
        val v = hex.toLong(16)
        return if (hex.length == 6) Color(0xFF000000 or v) else Color(v)
    }
}

/**
 * A theme pack: a zip of [THEME_FILE] and, as it has them, its font ([FONT_FILES]) and its pictures ([LookSlot] files:
 * app-background.jpg, companion-background.gif, bar-left.png...). Only those names are taken; the platform unzips with
 * [LookLimits]' caps and hands the files over by name.
 */
object ThemePack {
    const val THEME_FILE = "theme.json"
    val FONT_FILES = listOf("font.ttf", "font.otf")

    fun allowedName(name: String): Boolean = name == THEME_FILE || name in FONT_FILES || LookSlot.parse(name) != null && !name.endsWith(".img")

    /** The most a pack's file may unpack to. */
    fun maxBytes(name: String): Int = when {
        name == THEME_FILE -> LookLimits.THEME_CHARS
        name in FONT_FILES -> LookLimits.FONT_BYTES
        else -> maxOf(LookLimits.PICTURE_BYTES, LookLimits.ANIMATION_BYTES)
    }
}

/**
 * The added themes on disk (`themes/theme-<id>.json`, its pictures in `themes/theme-<id>/`), made in the app (the
 * theme editor, [create] / [update]) or added from a file ([import]); [export] gives one back as a pack's files.
 */
object ThemeFiles {
    private val look get() = CustomLook

    private fun jsonFile(id: Int) = look.themesDir?.let { it / "theme-$id.json" }
    private fun picturesDir(id: Int) = look.themesDir?.let { it / "theme-$id" }

    fun load() {
        val folder = look.themesDir
        val drafts = folder?.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("theme-") && it.name.endsWith(".json") }
            .mapNotNull { f ->
                val id = f.name.removePrefix("theme-").removeSuffix(".json").toIntOrNull() ?: return@mapNotNull null
                runCatching { id to ThemeDraft.of(f.readText()) }.getOrNull()
            }.sortedBy { it.first }
        look.themes = drafts.map { (id, d) -> d.spec(id) }
        look.themeFonts = drafts.mapNotNull { (id, d) -> d.fontFile?.let { id to it } }.toMap()
        look.themePictures = drafts.associate { (id, _) -> id to (picturesDir(id)?.let(look::picturesIn).orEmpty()) }
    }

    fun draft(id: Int): ThemeDraft? = look.themeText(id)?.let { runCatching { ThemeDraft.of(it) }.getOrNull() }

    /** A name no theme has: [name], else "[name] 2", "[name] 3"... ([except] keeps its own). */
    fun freeName(name: String, except: Int? = null): String {
        val taken = (APP_THEMES + SEASONAL_THEMES + look.themes).filter { it.id != except }.map { it.label }.toSet()
        if (name !in taken) return name
        return generateSequence(2) { it + 1 }.map { n -> "${name.take(ThemeDraft.MAX_NAME - 1 - "$n".length)} $n" }.first { it !in taken }
    }

    private fun nextId() = (look.themes.maxOfOrNull { it.id } ?: (CustomLook.FIRST_CUSTOM_THEME_ID - 1)) + 1

    private fun checkRoom() {
        if (look.themesDir == null) throw LookImportError("Can't keep it here")
        if (look.themes.size >= LookLimits.MAX_THEMES) throw LookImportError("You have {0} themes already: remove one first", "${LookLimits.MAX_THEMES}")
    }

    /** CREATE A THEME: a new theme in [from]'s colours (the one worn), named "MY THEME" (numbered). */
    fun create(from: ThemeSpec): ThemeSpec {
        checkRoom()
        val color = from.value.takeIf { from.hue != null } ?: Color(0xFF3D6FE0)
        val draft = ThemeDraft(name = freeName("MY THEME"), color = color, tint = from.wash.takeIf { from.hue != null } ?: 0.5f, darkPage = from.darkPage)
        return save(nextId(), draft)
    }

    /** Saves [draft] as theme [id] (a new one or an edit) and wears it again if it's worn. */
    fun update(id: Int, draft: ThemeDraft): ThemeSpec = save(id, draft.copy(name = freeName(draft.name, except = id)))

    private fun save(id: Int, draft: ThemeDraft): ThemeSpec {
        val file = jsonFile(id) ?: throw LookImportError("Can't keep it here")
        file.parent?.mkdirs()
        file.writeBytes(draft.toJson().encodeToByteArray())
        val spec = draft.spec(id)
        look.themes = (look.themes.filter { it.id != id } + spec).sortedBy { it.id }
        look.themeFonts = if (draft.fontFile != null) look.themeFonts + (id to draft.fontFile) else look.themeFonts - id
        if (id !in look.themePictures) look.themePictures = look.themePictures + (id to emptyMap())
        if (AppColors.chosen.id == id) AppColors.applyTheme(spec)
        return spec
    }

    /** A theme's [slot] picture from [bytes] (checked like MY LOOK's own: [CustomLook.checkPicture]). */
    fun setPicture(id: Int, slot: LookSlot, bytes: ByteArray, animated: Boolean) {
        val folder = picturesDir(id) ?: throw LookImportError("Can't keep it here")
        val file = look.writePicture(folder, slot, bytes, animated)
        val pics = look.themePictures[id].orEmpty() + (slot to LookPicture(file, animated, look.bumpPictures()))
        look.themePictures = look.themePictures + (id to pics)
    }

    /** The player's own [slot] picture copied into theme [id]. */
    fun copyOwnPicture(id: Int, slot: LookSlot) {
        val own = look.own[slot] ?: return
        setPicture(id, slot, own.file.readBytes(), own.animated)
    }

    fun removePicture(id: Int, slot: LookSlot) {
        look.themePictures[id]?.get(slot)?.file?.delete()
        look.themePictures = look.themePictures + (id to (look.themePictures[id].orEmpty() - slot))
        look.bumpPictures()
    }

    /** A theme's font: one of MY LOOK's fonts (null = none). */
    fun setFont(id: Int, font: CustomFont?) {
        val d = draft(id) ?: return
        update(id, d.copy(fontFile = font?.file?.name))
    }

    /**
     * ADD A THEME: a theme pack's files ([ThemePack]) or a bare theme's JSON as [ThemePack.THEME_FILE]. Everything is
     * checked before anything is kept: the JSON, the font ([FontFileInfo.check]), every picture ([CustomLook.checkPicture]).
     */
    fun import(files: Map<String, ByteArray>): ThemeSpec {
        checkRoom()
        files.keys.firstOrNull { !ThemePack.allowedName(it) }?.let { throw LookImportError("A theme pack can't hold \"{0}\"", it.take(40)) }
        val json = files[ThemePack.THEME_FILE] ?: throw LookImportError("That theme pack has no theme.json")
        val parsed = ThemeDraft.of(json.decodeToString())
        val fontBytes = ThemePack.FONT_FILES.firstNotNullOfOrNull { files[it] }
        fontBytes?.let(FontFileInfo::check)
        val pictures = files.mapNotNull { (name, bytes) ->
            val (slot, _) = LookSlot.parse(name) ?: return@mapNotNull null
            val animated = PictureMotion.isAnimated(bytes)
            look.checkPicture(bytes, animated)
            Triple(slot, bytes, animated)
        }
        val name = freeName(parsed.name)
        val font = fontBytes?.let { look.addFont("$name${FontFileInfo.extension(it)}", it) }
        val id = nextId()
        val spec = save(id, parsed.copy(name = name, fontFile = font?.file?.name))
        for ((slot, bytes, animated) in pictures) setPicture(id, slot, bytes, animated)
        return spec
    }

    /** EXPORT: theme [id] as a pack's files, its font and pictures in. */
    fun export(id: Int): Map<String, ByteArray> {
        val d = draft(id) ?: throw LookImportError("That theme couldn't be read")
        val out = LinkedHashMap<String, ByteArray>()
        val font = look.fontNamed(d.fontFile)
        val fontName = font?.let { "font" + (FontFileInfo.extension(it.file.readBytes()) ?: ".ttf") }
        out[ThemePack.THEME_FILE] = d.toJson(withFont = fontName, fontKey = "font").encodeToByteArray()
        if (font != null && fontName != null) out[fontName] = font.file.readBytes()
        for ((slot, pic) in look.themePictures[id].orEmpty()) {
            val ext = pic.file.name.substring(slot.base.length)
            out[slot.base + ext] = pic.file.readBytes()
        }
        return out
    }

    /** A pack's file name for theme [spec]: its name, "MIDNIGHT theme.zip". */
    fun exportName(spec: ThemeSpec): String = spec.label.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim().ifEmpty { "MY THEME" } + " theme.zip"

    /** Does theme [id] carry anything past its colours. */
    fun hasFiles(id: Int): Boolean = id in look.themeFonts || look.themePictures[id].orEmpty().isNotEmpty()
}

/** Wears [spec] the way a pick does: its colours, and its font when it has one ([CustomLook.themeFonts]). */
internal fun wearThemeFont(spec: ThemeSpec) {
    CustomLook.fontNamed(CustomLook.themeFonts[spec.id])?.let { AppFonts.wear(it) }
}
