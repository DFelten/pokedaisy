package com.pokedaisy.app.companion.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.pokedaisy.app.companion.data.delete
import com.pokedaisy.app.companion.data.isFile
import com.pokedaisy.app.companion.data.listFiles
import com.pokedaisy.app.companion.data.mkdirs
import com.pokedaisy.app.companion.data.readBytes
import com.pokedaisy.app.companion.data.readText
import com.pokedaisy.app.companion.data.writeBytes
import com.pokedaisy.app.companion.ui.decodeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlin.concurrent.Volatile
import okio.Path

/** MY LOOK > APP BACKGROUND / COMPANION BACKGROUND, stored by [ordinal]. */
enum class LookBackground(val label: String) {
    /** The look's own (the theme's backdrop; in a game, the game's). */
    DEFAULT("DEFAULT"),
    /** The player's picture for this screen. */
    PICTURE("MY PICTURE"),
    /** The companion only: the app's picture. */
    SAME_AS_APP("SAME AS APP");

    companion object {
        fun of(i: Int): LookBackground = entries.getOrNull(i) ?: DEFAULT
    }
}

/**
 * What the player added to the app's look (SETTINGS > MY LOOK on the top screen, the user's call, 2026-11-21): their own
 * fonts (TTF / OTF, worn through FONT TYPE), themes (a small JSON file, [CustomThemes]) and pictures behind the app and
 * the companion. Kept under [dir] (the app's `files/look/`): `fonts/`, `themes/`, `app-background.img`,
 * `companion-background.img`. Compose state, so both screens follow a change at once. The platform reads a picked file
 * and scales a picture down before handing the bytes here; nothing here trusts a file's name.
 */
object CustomLook {
    /** Set by the app at start; null (previews, tests) = nothing added, nothing kept. */
    @Volatile var dir: Path? = null

    var fonts by mutableStateOf<List<CustomFont>>(emptyList())
        private set
    var themes by mutableStateOf<List<ThemeSpec>>(emptyList())
        private set

    /** Bumped when a picture changes, so [rememberLookPicture] reads it again. */
    var pictures by mutableIntStateOf(0)
        private set

    /** MY LOOK's two backgrounds as set; Compose state. */
    var appBackground by mutableStateOf(LookBackground.DEFAULT)
        private set
    var companionBackground by mutableStateOf(LookBackground.DEFAULT)
        private set

    /** Previews / screenshot tests: these pictures whatever is kept. */
    var previewApp: ImageBitmap? = null
    var previewCompanion: ImageBitmap? = null

    private val fontsDir get() = dir?.let { it / "fonts" }
    private val themesDir get() = dir?.let { it / "themes" }
    fun pictureFile(companion: Boolean): Path? = dir?.let { it / if (companion) "companion-background.img" else "app-background.img" }

    /** Reads what's kept (at start, before the fonts and the theme are put on). */
    fun load() {
        fonts = fontsDir?.listFiles().orEmpty().filter { it.isFile && FONT_EXTENSIONS.any { e -> it.name.endsWith(e, true) } }
            .mapNotNull { f -> runCatching { CustomFont(fontName(f.name), f, FontFileInfo.capHeight(f.readBytes())) }.getOrNull() }
            .sortedBy { it.label }
        themes = themesDir?.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".json") }
            .mapNotNull { f ->
                val id = f.name.removePrefix("theme-").removeSuffix(".json").toIntOrNull() ?: return@mapNotNull null
                runCatching { CustomThemes.parse(f.readText(), id) }.getOrNull()
            }.sortedBy { it.id }
        val store = ThemeStore.current
        appBackground = LookBackground.of(store?.appBackground ?: 0).takeIf { it != LookBackground.SAME_AS_APP } ?: LookBackground.DEFAULT
        companionBackground = LookBackground.of(store?.companionBackground ?: 0)
    }

    // ---- fonts ------------------------------------------------------------

    /** Adds the font file [fileName] ([bytes]); the same name again replaces it. Throws [LookImportError] with a reason. */
    fun addFont(fileName: String, bytes: ByteArray): CustomFont {
        val folder = fontsDir ?: throw LookImportError("Can't keep it here")
        if (bytes.size > MAX_FONT_BYTES) throw LookImportError("That font is too big (10 MB at most)")
        val ext = FONT_EXTENSIONS.firstOrNull { fileName.endsWith(it, true) } ?: FontFileInfo.extension(bytes)
            ?: throw LookImportError("Not a font file: TTF or OTF only")
        if (!FontFileInfo.isFont(bytes)) throw LookImportError("Not a font file: TTF or OTF only")
        val cap = FontFileInfo.capHeight(bytes)
        val base = safeBase(fileName.substringBeforeLast('.')).ifEmpty { "font" }
        folder.mkdirs()
        val file = folder / "$base$ext"
        file.writeBytes(bytes)
        val font = CustomFont(fontName(file.name), file, cap)
        fonts = (fonts.filter { it.file != file } + font).sortedBy { it.label }
        return font
    }

    fun removeFont(font: CustomFont) {
        AppFonts.forget(font)
        font.file.delete()
        fonts = fonts - font
    }

    // ---- themes -----------------------------------------------------------

    /** Adds a theme file's [text] ([CustomThemes.parse]) and returns it. Throws [LookImportError] with a reason. */
    fun addTheme(text: String): ThemeSpec {
        val folder = themesDir ?: throw LookImportError("Can't keep it here")
        if (text.length > MAX_THEME_CHARS) throw LookImportError("That file is too big for a theme")
        val id = (themes.maxOfOrNull { it.id } ?: (FIRST_CUSTOM_THEME_ID - 1)) + 1
        val parsed = CustomThemes.parse(text, id)
        // A name another theme has gets a number, so the pick-lists (keyed by name) tell them apart.
        val taken = (APP_THEMES + SEASONAL_THEMES + themes).map { it.label }.toSet()
        val label = generateSequence(2) { it + 1 }.map { "${parsed.label} $it" }.let { more ->
            if (parsed.label !in taken) parsed.label else more.first { it !in taken }
        }
        val spec = parsed.copy(label = label)
        folder.mkdirs()
        (folder / "theme-$id.json").writeBytes(CustomThemes.withName(text, label).encodeToByteArray())
        themes = themes + spec
        return spec
    }

    fun removeTheme(spec: ThemeSpec) {
        themesDir?.let { (it / "theme-${spec.id}.json").delete() }
        themes = themes.filter { it.id != spec.id }
        if (AppColors.chosen.id == spec.id) pickTheme(themeById(DAISY_THEME_ID))
    }

    // ---- pictures ---------------------------------------------------------

    /** Keeps [bytes] (a PNG / JPEG the platform already scaled down) as the app's or the companion's picture and wears it. */
    fun setPicture(companion: Boolean, bytes: ByteArray) {
        val file = pictureFile(companion) ?: throw LookImportError("Can't keep it here")
        if (decodeImage(bytes) == null) throw LookImportError("Not a picture: PNG, JPEG or WebP")
        dir?.mkdirs()
        file.writeBytes(bytes)
        pictures++
        setBackground(companion, LookBackground.PICTURE)
    }

    fun removePicture(companion: Boolean) {
        pictureFile(companion)?.delete()
        pictures++
        if (companion) {
            if (companionBackground == LookBackground.PICTURE) setBackground(true, LookBackground.DEFAULT)
        } else {
            if (appBackground == LookBackground.PICTURE) setBackground(false, LookBackground.DEFAULT)
            if (companionBackground == LookBackground.SAME_AS_APP) setBackground(true, LookBackground.DEFAULT)
        }
    }

    fun hasPicture(companion: Boolean): Boolean = pictureFile(companion)?.isFile == true

    fun setBackground(companion: Boolean, choice: LookBackground) {
        if (companion) {
            ThemeStore.current?.companionBackground = choice.ordinal
            companionBackground = choice
        } else {
            ThemeStore.current?.appBackground = choice.ordinal
            appBackground = choice
        }
    }

    /** The picture behind the top screen's pages, or null for the look's own backdrop. */
    val appPictureFile: Path? get() = pictureFile(false).takeIf { appBackground == LookBackground.PICTURE }

    /** The picture behind the companion, or null for the game's / the look's own. */
    val companionPictureFile: Path? get() = when (companionBackground) {
        LookBackground.DEFAULT -> null
        LookBackground.PICTURE -> pictureFile(true)
        LookBackground.SAME_AS_APP -> pictureFile(false)
    }

    /** A font's name in the pick-list: its file's name, in capitals, without its extension. */
    private fun fontName(file: String): String =
        file.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim().uppercase().take(24)

    private fun safeBase(name: String): String =
        name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.trim().take(48)

    private val FONT_EXTENSIONS = listOf(".ttf", ".otf")
    private const val MAX_FONT_BYTES = 10 * 1024 * 1024
    private const val MAX_THEME_CHARS = 16 * 1024

    /** Added themes' ids start here (built-in ones are under 100). */
    const val FIRST_CUSTOM_THEME_ID = 1000
}

/** Why an added font, theme or picture wasn't kept, in words for the player (English, a [com.pokedaisy.app.companion.i18n.tr] key). */
class LookImportError(val en: String, val arg: String? = null) : Exception(arg?.let { en.replace("{0}", it) } ?: en)

/**
 * The picture behind a screen ([CustomLook.appPictureFile] / [CustomLook.companionPictureFile]), decoded off the main
 * thread once per change; null while there's none (or it's still loading).
 */
@Composable
fun rememberLookPicture(companion: Boolean): ImageBitmap? {
    if (companion) CustomLook.previewCompanion?.let { return it } else CustomLook.previewApp?.let { return it }
    val file = if (companion) CustomLook.companionPictureFile else CustomLook.appPictureFile
    val generation = CustomLook.pictures
    val picture by produceState<ImageBitmap?>(file?.let { pictureCache[it] }?.takeIf { it.first == generation }?.second, file, generation) {
        if (file == null) { value = null; return@produceState }
        pictureCache[file]?.takeIf { it.first == generation }?.let { value = it.second; return@produceState }
        value = withContext(Dispatchers.IO) { runCatching { if (file.isFile) decodeImage(file.readBytes()) else null }.getOrNull() }
        value?.let { pictureCache[file] = generation to it }
    }
    return if (file == null) null else picture
}

// One decoded picture per file (two at most), kept while the process lives: a tab or a page doesn't decode it again.
private val pictureCache = com.pokedaisy.app.companion.data.LockedMap<Path, Pair<Int, ImageBitmap>>()

/**
 * A theme file (MY LOOK > ADD A THEME): a small JSON object, only [name] and [color] needed, the rest worked out from the
 * colour - docs/manual/screen.md's "Your own themes" has the format for players:
 * ```
 * { "name": "MIDNIGHT", "color": "#3D6FE0" }
 * ```
 * Optional: `tint` (0-1, how much the windows take the colour, 0.5), `darkColor` (values in DARK MODE), `page` and
 * `darkPage` (the backdrop's colour; true = a dark page with white text), `glows` (two to five colours drifting over it),
 * `gameTint` (over a game's backdrop; null = none).
 */
object CustomThemes {
    fun parse(text: String, id: Int): ThemeSpec {
        val root = runCatching { Json.parseToJsonElement(text) }.getOrElse { throw LookImportError("Not a theme file: it isn't JSON") }
            as? JsonObject ?: throw LookImportError("Not a theme file: it isn't JSON")
        val name = root.string("name")?.filter { it != '|' }?.trim()?.uppercase()?.take(MAX_NAME)?.takeIf { it.isNotEmpty() }
            ?: throw LookImportError("The theme has no name")
        val color = root.color("color") ?: throw LookImportError("The theme has no color")
        val hue = hueOf(color)
        val tint = root.number("tint")?.coerceIn(0f, 1f) ?: 0.5f
        val darkPage = root.bool("darkPage") ?: false
        val page = root.color("page") ?: if (darkPage) hslColor(hue, 0.35f, 0.12f) else hslColor(hue, 0.6f, 0.97f)
        val glows = (root["glows"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.let(::parseColor) }
            ?.take(5)?.takeIf { it.size >= 2 }
            ?: if (darkPage) listOf(hslColor(hue, 0.5f, 0.28f), hslColor(hue + 50f, 0.45f, 0.26f), hslColor(hue - 50f, 0.45f, 0.24f))
            else listOf(hslColor(hue, 0.85f, 0.86f), hslColor(hue + 40f, 0.85f, 0.88f), hslColor(hue - 40f, 0.8f, 0.87f))
        val gameTint = if (root["gameTint"] is JsonNull) null else root.color("gameTint") ?: hslColor(hue, 0.8f, 0.9f)
        return ThemeSpec(
            id = id, label = name, hue = hue, wash = tint,
            value = color, valueNight = root.color("darkColor") ?: brighter(color),
            page = page, blobs = glows, darkPage = darkPage, gameWash = gameTint,
        )
    }

    /** [text] with its name set to [name] (a second theme of one name gets a number). */
    fun withName(text: String, name: String): String {
        val root = Json.parseToJsonElement(text) as JsonObject
        return JsonObject(root + ("name" to JsonPrimitive(name))).toString()
    }

    /** "#RRGGBB" or "#AARRGGBB" (the # optional), else null. */
    fun parseColor(s: String): Color? {
        val hex = s.trim().removePrefix("#")
        if (hex.length != 6 && hex.length != 8 || hex.any { it.digitToIntOrNull(16) == null }) return null
        val v = hex.toLong(16)
        return if (hex.length == 6) Color(0xFF000000 or v) else Color(v)
    }

    /** A colour light enough for values on DARK MODE's slate windows. */
    private fun brighter(c: Color): Color {
        val l = (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue)
        return if (l >= 0.6f) c else hslColor(hueOf(c), 1f, 0.7f)
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.number(key: String): Float? = (this[key] as? JsonPrimitive)?.floatOrNull
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.color(key: String): Color? {
        val raw = string(key) ?: return null
        return parseColor(raw) ?: throw LookImportError("\"{0}\" isn't a color like #3D6FE0", key)
    }

    private const val MAX_NAME = 20
}

/** What the app reads from a font file: is it one, and its capitals' height against its em (OS/2's sCapHeight). */
object FontFileInfo {
    fun isFont(b: ByteArray): Boolean = extension(b) != null && tables(b).containsKey("head")

    /** ".ttf" / ".otf" by the file's first bytes, else null (WOFF and collections aren't read). */
    fun extension(b: ByteArray): String? {
        if (b.size < 12) return null
        val tag = u32(b, 0)
        return when (tag) {
            0x00010000L, 0x74727565L -> ".ttf"   // 1.0, 'true'
            0x4F54544FL -> ".otf"                // 'OTTO'
            else -> null
        }
    }

    /** The capitals' height as a share of the em; 0.7 when the font doesn't say (OS/2 before version 2). */
    fun capHeight(b: ByteArray): Float = runCatching {
        val t = tables(b)
        val head = t["head"] ?: return@runCatching DEFAULT_CAP
        val unitsPerEm = u16(b, head + 18)
        val os2 = t["OS/2"] ?: return@runCatching DEFAULT_CAP
        if (u16(b, os2) < 2 || unitsPerEm <= 0) return@runCatching DEFAULT_CAP
        val cap = u16(b, os2 + 88)
        if (cap <= 0) DEFAULT_CAP else (cap.toFloat() / unitsPerEm).coerceIn(0.3f, 1f)
    }.getOrDefault(DEFAULT_CAP)

    private fun tables(b: ByteArray): Map<String, Int> {
        if (b.size < 12) return emptyMap()
        val n = u16(b, 4)
        val out = HashMap<String, Int>()
        for (i in 0 until n) {
            val at = 12 + i * 16
            if (at + 16 > b.size) break
            val tag = (0 until 4).map { (b[at + it].toInt() and 0xFF).toChar() }.joinToString("")
            val offset = u32(b, at + 8).toInt()
            if (offset in 0 until b.size) out[tag] = offset
        }
        return out
    }

    private fun u16(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    private const val DEFAULT_CAP = 0.7f
}
