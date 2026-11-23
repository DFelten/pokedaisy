package com.pokedaisy.app.companion.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.pokedaisy.app.companion.data.delete
import com.pokedaisy.app.companion.data.deleteRecursively
import com.pokedaisy.app.companion.data.isFile
import com.pokedaisy.app.companion.data.listFiles
import com.pokedaisy.app.companion.data.mkdirs
import com.pokedaisy.app.companion.data.readBytes
import com.pokedaisy.app.companion.data.readText
import com.pokedaisy.app.companion.data.writeBytes
import com.pokedaisy.app.companion.ui.decodeImage
import kotlin.concurrent.Volatile
import okio.Path

/** MY LOOK > APP BACKGROUND / COMPANION BACKGROUND, stored by [ordinal]. */
enum class LookBackground(val label: String) {
    /** The worn theme's picture when it has one, else the look's own (the theme's backdrop; in a game, the game's). */
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
 * SIDE BARS (the user's call, 2026-11-23: players didn't like the blurred game beside a DS game): what fills the screen
 * beside a game that doesn't (no EXPAND, or it's off), stored by [ordinal].
 */
enum class SideBars(val label: String) {
    /** The worn theme's bar pictures when it has some, else the app's own: a DS game's blurred picture, black elsewhere. */
    DEFAULT("DEFAULT"),
    BLACK("BLACK"),
    /** The player's LEFT BAR / RIGHT BAR pictures (MY LOOK). */
    PICTURES("MY PICTURES");

    companion object {
        fun of(i: Int): SideBars = entries.getOrNull(i) ?: DEFAULT
    }
}

/** TAB BAR > POSITION: where the companion's tab bar sits (the user's call, 2026-11-23), stored by [ordinal]. */
enum class TabBarPlace(val label: String) {
    BOTTOM("BOTTOM"), TOP("TOP"), LEFT("LEFT"), RIGHT("RIGHT");

    val side get() = this == LEFT || this == RIGHT

    companion object {
        fun of(i: Int): TabBarPlace = entries.getOrNull(i) ?: BOTTOM
    }
}

/** MENU BAR: where the library's GAMES / TOOLS / SETTINGS sit; AUTO = down the left of a wide screen, along the bottom of a
 * tall one. Stored by [ordinal]. */
enum class MenuBarPlace(val label: String) {
    AUTO("AUTO"), LEFT("LEFT"), RIGHT("RIGHT"), TOP("TOP"), BOTTOM("BOTTOM");

    /** Where it goes on a screen [wide] or not. */
    fun on(wide: Boolean): MenuBarPlace = if (this == AUTO) (if (wide) LEFT else BOTTOM) else this
    val side get() = this == LEFT || this == RIGHT

    companion object {
        fun of(i: Int): MenuBarPlace = entries.getOrNull(i) ?: AUTO
    }
}

/** A place a picture of the look goes: the player's own (MY LOOK) or a theme's, a file `<base>.<kind>` each. */
enum class LookSlot(val base: String, val label: String) {
    APP("app-background", "APP BACKGROUND"),
    COMPANION("companion-background", "COMPANION BACKGROUND"),
    LEFT_BAR("bar-left", "LEFT BAR"),
    RIGHT_BAR("bar-right", "RIGHT BAR");

    companion object {
        /** [name]'s slot and kind ("bar-left.gif"); null for anything else. */
        fun parse(name: String): Pair<LookSlot, PictureKind>? {
            val slot = entries.firstOrNull { name.startsWith(it.base + ".") } ?: return null
            val ext = name.substring(slot.base.length).lowercase().let { if (it == ".jpeg") ".jpg" else it }
            // Pictures kept before moving ones were taken: app-background.img, a still.
            if (ext == ".img") return slot to PictureKind.JPEG
            val kind = PictureKind.entries.firstOrNull { it.extension == ext } ?: return null
            return slot to kind
        }
    }
}

/** A picture of the look on disk: a still one, or a moving GIF / WebP ([animated], drawn by [AnimatedPictures]). */
data class LookPicture(val file: Path, val animated: Boolean, val generation: Int)

/**
 * What the player added to the app's look (SETTINGS > MY LOOK on the top screen, the user's calls, 2026-11-21 / 23): their
 * own fonts (TTF / OTF, worn through FONT TYPE), themes (made in the app or added from a file: [ThemeFiles]), pictures
 * behind the app and the companion (still or moving) and beside a game ([SideBars]). Kept under [dir] (the app's
 * `files/look/`): `fonts/`, `themes/`, and a [LookSlot] file each. Compose state, so both screens follow a change at
 * once. The platform reads a picked file ([LookLimits]) and scales a still picture down before handing the bytes here;
 * nothing here trusts a file's name.
 */
object CustomLook {
    /** Set by the app at start; null (previews, tests) = nothing added, nothing kept. */
    @Volatile var dir: Path? = null

    var fonts by mutableStateOf<List<CustomFont>>(emptyList())
        private set
    var themes by mutableStateOf<List<ThemeSpec>>(emptyList())
        internal set

    /** Bumped when a picture changes, so a drawn one is read again. */
    var pictures by mutableIntStateOf(0)
        private set

    /** The player's own pictures, by slot (read at [load] and on every change: never from disk while drawing). */
    var own by mutableStateOf<Map<LookSlot, LookPicture>>(emptyMap())
        private set

    /** Each added theme's pictures and font file (its name in [fonts]' folder), by theme id. */
    var themePictures by mutableStateOf<Map<Int, Map<LookSlot, LookPicture>>>(emptyMap())
        internal set
    var themeFonts by mutableStateOf<Map<Int, String>>(emptyMap())
        internal set

    /** MY LOOK's two backgrounds as set; Compose state. */
    var appBackground by mutableStateOf(LookBackground.DEFAULT)
        private set
    var companionBackground by mutableStateOf(LookBackground.DEFAULT)
        private set
    /** SIDE BARS; Compose state. */
    var sideBars by mutableStateOf(SideBars.DEFAULT)
        private set
    /** TAB BAR > SHOW AS ICONS; Compose state. */
    var tabIcons by mutableStateOf(false)
        private set
    /** TAB BAR > POSITION / SIZE, MENU BAR / MENU BAR SIZE; Compose state. */
    var tabBarPlace by mutableStateOf(TabBarPlace.BOTTOM)
        private set
    var tabBarCompact by mutableStateOf(false)
        private set
    var menuBarPlace by mutableStateOf(MenuBarPlace.AUTO)
        private set
    var menuBarCompact by mutableStateOf(false)
        private set

    /** Previews / screenshot tests: these pictures whatever is kept. */
    var previewApp: ImageBitmap? = null
    var previewCompanion: ImageBitmap? = null
    var previewBars: Pair<ImageBitmap?, ImageBitmap?>? = null

    internal val fontsDir get() = dir?.let { it / "fonts" }
    internal val themesDir get() = dir?.let { it / "themes" }

    /** Reads what's kept (at start, before the fonts and the theme are put on). */
    fun load() {
        if (!com.pokedaisy.app.companion.FlaggedFeatures.modernLook) return
        fonts = fontsDir?.listFiles().orEmpty().filter { it.isFile && FONT_EXTENSIONS.any { e -> it.name.endsWith(e, true) } }
            .mapNotNull { f -> runCatching { CustomFont(fontName(f.name), f, FontFileInfo.capHeight(f.readBytes())) }.getOrNull() }
            .sortedBy { it.label }
        own = dir?.let(::picturesIn).orEmpty()
        ThemeFiles.load()
        val store = ThemeStore.current
        appBackground = LookBackground.of(store?.appBackground ?: 0).takeIf { it != LookBackground.SAME_AS_APP } ?: LookBackground.DEFAULT
        companionBackground = LookBackground.of(store?.companionBackground ?: 0)
        sideBars = SideBars.of(store?.sideBars ?: 0)
        tabIcons = store?.tabIcons ?: false
        tabBarPlace = TabBarPlace.of(store?.tabBarPlace ?: 0)
        tabBarCompact = store?.tabBarCompact ?: false
        menuBarPlace = MenuBarPlace.of(store?.menuBarPlace ?: 0)
        menuBarCompact = store?.menuBarCompact ?: false
    }

    fun changeTabBar(place: TabBarPlace = tabBarPlace, compact: Boolean = tabBarCompact) {
        ThemeStore.current?.tabBarPlace = place.ordinal
        ThemeStore.current?.tabBarCompact = compact
        tabBarPlace = place; tabBarCompact = compact
    }

    fun changeMenuBar(place: MenuBarPlace = menuBarPlace, compact: Boolean = menuBarCompact) {
        ThemeStore.current?.menuBarPlace = place.ordinal
        ThemeStore.current?.menuBarCompact = compact
        menuBarPlace = place; menuBarCompact = compact
    }

    /** The [LookSlot] pictures in [folder]. */
    internal fun picturesIn(folder: Path): Map<LookSlot, LookPicture> = folder.listFiles().mapNotNull { f ->
        val (slot, kind) = LookSlot.parse(f.name) ?: return@mapNotNull null
        if (!f.isFile) return@mapNotNull null
        slot to LookPicture(f, animated = (kind == PictureKind.GIF || kind == PictureKind.WEBP) && isMoving(f), pictures)
    }.toMap()

    // A WebP or GIF moves when its bytes say so; read once when it's listed, not while drawing.
    private fun isMoving(f: Path): Boolean = runCatching { PictureMotion.isAnimated(f.readBytes()) }.getOrDefault(false)

    // ---- fonts ------------------------------------------------------------

    /** Adds the font file [fileName] ([bytes]); the same name again replaces it. Throws [LookImportError] with a reason. */
    fun addFont(fileName: String, bytes: ByteArray): CustomFont {
        val folder = fontsDir ?: throw LookImportError("Can't keep it here")
        FontFileInfo.check(bytes)
        val base = safeBase(fileName.substringBeforeLast('.')).ifEmpty { "font" }
        if (fonts.size >= LookLimits.MAX_FONTS && fonts.none { it.file.name.substringBeforeLast('.') == base }) {
            throw LookImportError("You have {0} fonts already: remove one first", "${LookLimits.MAX_FONTS}")
        }
        // The kind by its bytes, never the name's extension.
        val ext = FontFileInfo.extension(bytes)!!
        val cap = FontFileInfo.capHeight(bytes)
        folder.mkdirs()
        FONT_EXTENSIONS.forEach { e -> if (e != ext) (folder / "$base$e").delete() }
        val file = folder / "$base$ext"
        file.writeBytes(bytes)
        val font = CustomFont(fontName(file.name), file, cap)
        fonts = (fonts.filter { it.file != file && it.label != font.label } + font).sortedBy { it.label }
        return font
    }

    fun removeFont(font: CustomFont) {
        AppFonts.forget(font)
        font.file.delete()
        fonts = fonts - font
    }

    /** The added font [fileName] names (a theme's), if it's still kept. */
    fun fontNamed(fileName: String?): CustomFont? = fileName?.let { n -> fonts.firstOrNull { it.file.name == n } }

    // ---- pictures ---------------------------------------------------------

    /**
     * Keeps [bytes] as the player's [slot] picture: a still PNG / JPEG the platform already scaled down, or a moving GIF /
     * WebP ([animated]) as it was picked. Throws [LookImportError] unless the bytes are such a picture.
     */
    fun setPicture(slot: LookSlot, bytes: ByteArray, animated: Boolean) {
        val folder = dir ?: throw LookImportError("Can't keep it here")
        val file = writePicture(folder, slot, bytes, animated)
        own = own + (slot to LookPicture(file, animated, ++pictures))
        when (slot) {
            LookSlot.APP -> setBackground(false, LookBackground.PICTURE)
            LookSlot.COMPANION -> setBackground(true, LookBackground.PICTURE)
            LookSlot.LEFT_BAR, LookSlot.RIGHT_BAR -> changeSideBars(SideBars.PICTURES)
        }
    }

    /** Checks [bytes] ([checkPicture]) and writes them into [folder] as [slot]'s one picture; returns the file. */
    internal fun writePicture(folder: Path, slot: LookSlot, bytes: ByteArray, animated: Boolean): Path {
        val kind = checkPicture(bytes, animated)
        folder.mkdirs()
        // One file a slot: any other kind kept before goes.
        folder.listFiles().filter { LookSlot.parse(it.name)?.first == slot }.forEach { it.delete() }
        val file = folder / "${slot.base}${kind.extension}"
        file.writeBytes(bytes)
        return file
    }

    /** [bytes]' kind if they're a picture the app keeps ([LookLimits]): a still one decodes, a moving one is a GIF / WebP. */
    fun checkPicture(bytes: ByteArray, animated: Boolean): PictureKind {
        val kind = PictureKind.of(bytes) ?: throw LookImportError("Not a picture: PNG, JPEG, WebP or GIF")
        if (animated) {
            if (kind != PictureKind.GIF && kind != PictureKind.WEBP) throw LookImportError("Not a picture: PNG, JPEG, WebP or GIF")
            LookLimits.checkSize(bytes.size.toLong(), LookLimits.ANIMATION_BYTES, "moving picture")
            val (w, h) = PictureMotion.size(bytes) ?: throw LookImportError("Not a picture: PNG, JPEG, WebP or GIF")
            LookLimits.checkPictureSize(w, h, animated = true)
        } else {
            LookLimits.checkSize(bytes.size.toLong(), LookLimits.PICTURE_BYTES, "picture")
            val image = decodeImage(bytes) ?: throw LookImportError("Not a picture: PNG, JPEG, WebP or GIF")
            LookLimits.checkPictureSize(image.width, image.height, animated = false)
        }
        return kind
    }

    fun removePicture(slot: LookSlot) {
        own[slot]?.file?.delete()
        own = own - slot
        pictures++
        when (slot) {
            LookSlot.COMPANION -> if (companionBackground == LookBackground.PICTURE) setBackground(true, LookBackground.DEFAULT)
            LookSlot.APP -> {
                if (appBackground == LookBackground.PICTURE) setBackground(false, LookBackground.DEFAULT)
                if (companionBackground == LookBackground.SAME_AS_APP) setBackground(true, LookBackground.DEFAULT)
            }
            LookSlot.LEFT_BAR, LookSlot.RIGHT_BAR ->
                if (sideBars == SideBars.PICTURES && LookSlot.LEFT_BAR !in own && LookSlot.RIGHT_BAR !in own) changeSideBars(SideBars.DEFAULT)
        }
    }

    fun hasPicture(slot: LookSlot): Boolean = slot in own
    fun hasPicture(companion: Boolean): Boolean = hasPicture(if (companion) LookSlot.COMPANION else LookSlot.APP)

    fun setBackground(companion: Boolean, choice: LookBackground) {
        if (companion) {
            ThemeStore.current?.companionBackground = choice.ordinal
            companionBackground = choice
        } else {
            ThemeStore.current?.appBackground = choice.ordinal
            appBackground = choice
        }
    }

    fun changeSideBars(choice: SideBars) {
        ThemeStore.current?.sideBars = choice.ordinal
        sideBars = choice
    }

    fun changeTabIcons(on: Boolean) {
        ThemeStore.current?.tabIcons = on
        tabIcons = on
    }

    /** The worn theme's picture for [slot], if it's an added theme that has one. */
    private fun themePicture(spec: ThemeSpec, slot: LookSlot): LookPicture? = themePictures[spec.id]?.get(slot)

    /** The picture behind the top screen's pages, or null for the look's own backdrop. */
    val appPicture: LookPicture? get() = when (appBackground) {
        LookBackground.PICTURE -> own[LookSlot.APP]
        else -> themePicture(AppColors.theme, LookSlot.APP)
    }

    /** The picture behind the companion, or null for the game's / the look's own. */
    val companionPicture: LookPicture? get() = when (companionBackground) {
        LookBackground.DEFAULT -> themePicture(AppColors.theme, LookSlot.COMPANION)
        LookBackground.PICTURE -> own[LookSlot.COMPANION]
        LookBackground.SAME_AS_APP -> own[LookSlot.APP]
    }

    /**
     * What's beside a game that doesn't fill the screen: null = the app's own ([SideBars.DEFAULT] with no theme
     * pictures: a DS game's blurred picture, else black), [Bars.black], or pictures (one side's for both when only one
     * is set).
     */
    val bars: Bars? get() {
        val pics = when (sideBars) {
            SideBars.BLACK -> return Bars(null, null, black = true)
            SideBars.PICTURES -> own[LookSlot.LEFT_BAR] to own[LookSlot.RIGHT_BAR]
            SideBars.DEFAULT -> themePicture(AppColors.theme, LookSlot.LEFT_BAR) to themePicture(AppColors.theme, LookSlot.RIGHT_BAR)
        }
        val (l, r) = pics
        if (l == null && r == null) return if (sideBars == SideBars.PICTURES) Bars(null, null, black = true) else null
        return Bars(l ?: r, r ?: l, black = false)
    }

    /** [bars]' answer: the two sides' pictures (left / top, right / bottom), or plain black. */
    data class Bars(val first: LookPicture?, val second: LookPicture?, val black: Boolean)

    /** A font's name in the pick-list: its file's name, in capitals, without its extension. */
    internal fun fontName(file: String): String =
        file.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim().uppercase().take(24)

    internal fun safeBase(name: String): String =
        name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.trim().take(48)

    internal fun bumpPictures(): Int = ++pictures

    private val FONT_EXTENSIONS = listOf(".ttf", ".otf")

    /** Added themes' ids start here (built-in ones are under 100). */
    const val FIRST_CUSTOM_THEME_ID = 1000

    /** A kept theme file's text (for the editor), or null. */
    internal fun themeText(id: Int): String? = themesDir?.let { it / "theme-$id.json" }?.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    /** Removes an added theme with its pictures (its font stays among the fonts: FONT TYPE may wear it). */
    fun removeTheme(spec: ThemeSpec) {
        themesDir?.let { (it / "theme-${spec.id}.json").delete(); (it / "theme-${spec.id}").deleteRecursively() }
        themes = themes.filter { it.id != spec.id }
        themePictures = themePictures - spec.id
        themeFonts = themeFonts - spec.id
        if (AppColors.chosen.id == spec.id) pickTheme(themeById(DAISY_THEME_ID))
    }
}

/** Why an added font, theme or picture wasn't kept, in words for the player (English, a [com.pokedaisy.app.companion.i18n.tr] key). */
class LookImportError(val en: String, val arg: String? = null) : Exception(arg?.let { en.replace("{0}", it) } ?: en)
