package com.pokedaisy.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pokedaisy.app.companion.data.GENDER_SYMBOL_FEMALE
import com.pokedaisy.app.companion.data.GENDER_SYMBOL_MALE
import com.pokedaisy.app.companion.data.GENDER_SYMBOL_NONE
import com.pokedaisy.app.companion.data.GENDER_SYMBOL_UNKNOWN
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.ItemView
import com.pokedaisy.app.companion.data.LocationView
import com.pokedaisy.app.companion.data.MonStats
import com.pokedaisy.app.companion.data.MonView
import com.pokedaisy.app.companion.data.MoveView
import com.pokedaisy.app.companion.data.POCKET_BATTLE_ITEMS
import com.pokedaisy.app.companion.data.POCKET_BERRIES
import com.pokedaisy.app.companion.data.POCKET_ITEMS
import com.pokedaisy.app.companion.data.POCKET_KEY_ITEMS
import com.pokedaisy.app.companion.data.POCKET_MAIL
import com.pokedaisy.app.companion.data.POCKET_MEDICINE
import com.pokedaisy.app.companion.data.POCKET_POKE_BALLS
import com.pokedaisy.app.companion.data.POCKET_TM_HM
import com.pokedaisy.app.companion.data.PcGameTypes
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.data.TYPE_NONE
import com.pokedaisy.app.companion.data.typeMatchups
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.CRC32

/**
 * A PC fan game's companion data ([GameKind.ESSENTIALS]): the snapshot our preload sends from the game
 * (linux/pc_games/pokedaisy_companion.rb, through [PcCore.pcMailbox]) turned into the [SnapshotView] the real
 * companion draws - PARTY with its summaries and the bag, every name and type the game's own. Party and item icons are
 * the game's PNGs, rebuilt into the art cache the companion already reads ([iconDir]): a party icon as the companion's
 * 32 x 64 two-frame sheet, a fusion's put together the way Infinite Fusion's createFusionIcon does (linux/pc_companion.c).
 *
 * Read [snapshot] off the UI thread: it decodes PNGs the first time it meets them. [GameKind.ESSENTIALS] must be the
 * [com.pokedaisy.app.companion.data.activeGame] while it runs ([PcGameTypes] backs the type lookups then).
 */
class PcCompanion(private val gameDir: File, filesDir: File) {
    /** Where [com.pokedaisy.app.companion.ui.GameArt] finds rom-art files ([com.pokedaisy.app.companion.data.RomArt.file]). */
    private val iconDir = File(filesDir, "rom-art/pc").apply { mkdirs() }
    private var lastTypes: String? = null

    /** The bag's names by the [ItemView.itemId]s [snapshot] made up (the companion's item names lookup). */
    @Volatile var itemNames: Map<Int, String> = emptyMap()
        private set

    fun snapshot(json: String): SnapshotView? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (o.has("error")) return null
        o.optJSONArray("typeTable")?.let { t ->
            val raw = t.toString()
            if (raw != lastTypes) {
                lastTypes = raw
                PcGameTypes.set((0 until t.length()).mapNotNull { i -> t.optJSONObject(i)?.let(::type) })
            }
        }
        val trainer = o.optJSONObject("trainer")
        val party = o.optJSONArray("party")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { mon(it, i) } } }
            .orEmpty()
        val names = HashMap<Int, String>()
        val items = o.optJSONArray("bag")?.let { bag(it, names) }.orEmpty()
        itemNames = names
        val map = o.optJSONObject("map")
        return SnapshotView(
            connected = true,
            game = GameKind.ESSENTIALS,
            frameCounter = o.optLong("frame"),
            party = party,
            items = items,
            location = LocationView(map?.optString("name").orEmpty(), null, 0, 0, 0, 0),
            x = map?.optInt("x") ?: 0,
            y = map?.optInt("y") ?: 0,
            money = trainer?.takeIf { it.has("money") }?.optLong("money"),
        )
    }

    private fun type(t: JSONObject) = PcGameTypes.Type(
        id = t.optInt("id"),
        name = t.optString("name"),
        weak = ints(t.optJSONArray("weak")),
        resist = ints(t.optJSONArray("resist")),
        immune = ints(t.optJSONArray("immune")),
    )

    private fun mon(m: JSONObject, slot: Int): MonView {
        val egg = m.optBoolean("egg")
        val typeIds = ints(m.optJSONArray("types")).distinct()
        val types = typeIds.mapNotNull { PcGameTypes.names[it] }
        val matchups = typeIds.takeIf { !egg && it.isNotEmpty() }?.let { typeMatchups(it[0], it.getOrElse(1) { TYPE_NONE }) }
        val hp = m.optInt("hp")
        val species = m.optString("species")
        return MonView(
            // The summary's lookups that key on it (dex, relearn) need our tables, which a PC game has none of: the slot.
            species = slot + 1,
            name = species.ifEmpty { m.optString("name") },
            level = m.optInt("level"),
            hp = hp,
            maxHp = m.optInt("totalhp"),
            status = if (hp == 0 && !egg) "FNT" else status(m.optString("status")),
            types = types,
            iconAsset = icon(m.optJSONObject("icon")),
            genderSymbol = when (m.optInt("gender", -1)) {
                0 -> GENDER_SYMBOL_MALE
                1 -> GENDER_SYMBOL_FEMALE
                2 -> GENDER_SYMBOL_NONE
                else -> GENDER_SYMBOL_UNKNOWN
            },
            isEgg = egg,
            moves = if (egg) emptyList() else m.optJSONArray("moves")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val mv = a.optJSONObject(i) ?: return@mapNotNull null
                    MoveView(
                        name = mv.optString("name"),
                        type = PcGameTypes.names[mv.optInt("type", -1)].orEmpty(),
                        pp = mv.optInt("pp"),
                        power = mv.optInt("power"),
                        id = i + 1,
                        maxPp = mv.optInt("total"),
                    )
                }
            }.orEmpty(),
            weaknesses = matchups?.weaknesses.orEmpty(),
            resistances = matchups?.resistances.orEmpty(),
            immunities = matchups?.immunities.orEmpty(),
            stats = if (egg) null else stats(m),
            abilityName = m.optString("ability").takeIf { it.isNotEmpty() && it != "null" },
            heldItem = if (m.isNull("item")) "" else m.optString("item"),
            nickname = m.optString("name").takeIf { it.isNotEmpty() },
            shiny = m.optBoolean("shiny"),
        )
    }

    /** IVs, EVs and the stats in the companion's order (HP, Atk, Def, Spe, SpA, SpD) from the game's (HP, Atk, Def, SpA,
     * SpD, Spe); null without all three. Essentials numbers natures as Gen 3 does (HARDY 0 .. QUIRKY 24). */
    private fun stats(m: JSONObject): MonStats? {
        fun ordered(key: String) = ints(m.optJSONArray(key)).takeIf { it.size == 6 }?.let { listOf(it[0], it[1], it[2], it[5], it[3], it[4]) }
        val ivs = ordered("ivs") ?: return null
        val evs = ordered("evs") ?: return null
        val stats = ordered("stats") ?: return null
        return MonStats(ivs, evs, m.optInt("natureId", -1).takeIf { it in 0..24 } ?: com.pokedaisy.app.companion.data.NO_NATURE, stats)
    }

    private fun status(s: String) = when (s.uppercase()) {
        "SLEEP" -> "SLP"
        "POISON" -> "PSN"
        "BURN" -> "BRN"
        "PARALYSIS" -> "PAR"
        "FROZEN" -> "FRZ"
        else -> ""
    }

    /** Essentials' pockets after its unused 0: Items, Medicine, Poke Balls, TMs & HMs, Berries, Mail, Battle Items, Key Items. */
    private fun bag(pockets: JSONArray, names: MutableMap<Int, String>): List<ItemView> {
        val kinds = intArrayOf(POCKET_ITEMS, POCKET_MEDICINE, POCKET_POKE_BALLS, POCKET_TM_HM, POCKET_BERRIES, POCKET_MAIL,
            POCKET_BATTLE_ITEMS, POCKET_KEY_ITEMS)
        val out = ArrayList<ItemView>()
        for (p in 0 until pockets.length()) {
            val pocket = pockets.optJSONObject(p) ?: continue
            val list = pocket.optJSONArray("items") ?: continue
            for (i in 0 until list.length()) {
                val it = list.optJSONObject(i) ?: continue
                // Ids of our own, a pocket's place apart: the companion keys rows, favourites and names by them.
                val id = (p + 1) * 1000 + i + 1
                val name = it.optString("name")
                names[id] = name
                out += ItemView(
                    itemId = id,
                    name = name,
                    quantity = it.optInt("qty"),
                    iconAsset = itemIcon(it.optString("icon").takeIf { s -> s.isNotEmpty() && s != "null" }),
                    pocket = kinds.getOrElse(p) { POCKET_ITEMS },
                    description = it.optString("desc").takeIf { s -> s != "null" }.orEmpty(),
                )
            }
        }
        return out
    }

    // ---- icons ---------------------------------------------------------------------------------------------------

    /** The party icon's rom-art path ("pc/m<crc>.png"), built once per file (or head + body + offset); null = none. */
    private fun icon(icon: JSONObject?): String? {
        icon ?: return null
        val file = icon.optString("file").takeIf { it.isNotEmpty() && it != "null" }
        val head = icon.optString("head").takeIf { it.isNotEmpty() && it != "null" }
        val body = icon.optString("body").takeIf { it.isNotEmpty() && it != "null" }
        val offset = icon.optInt("offset", 10)
        val key = file ?: if (head != null && body != null) "$head|$body|$offset" else return null
        val name = "m${crc(key)}.png"
        val out = File(iconDir, name)
        if (!out.isFile) {
            val sheet = load(file ?: head!!) ?: return null
            val px = IntArray(sheet.width * sheet.height).also { sheet.getPixels(it, 0, sheet.width, 0, 0, sheet.width, sheet.height) }
            val h = sheet.height
            if (file == null) {
                // The head's frames, then the body's rows from [offset] below the middle down, every pixel copied.
                load(body!!)?.takeIf { it.height == h && it.width >= sheet.width }?.let { b ->
                    val bpx = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                    for (y in h / 2 + offset * h / 64 until h) for (x in 0 until sheet.width) px[y * sheet.width + x] = bpx[y * b.width + x]
                }
            }
            // The companion's sheet: 32 x 32 frames stacked, the game's (square, side by side, drawn 2x) sampled down.
            if (h < ICON || sheet.width < h) return null
            val frames = (sheet.width / h).coerceIn(1, 2)
            val outPx = IntArray(ICON * ICON * 2)
            for (f in 0 until 2) {
                val fx = (f % frames) * h
                for (y in 0 until ICON) for (x in 0 until ICON) {
                    outPx[(f * ICON + y) * ICON + x] = px[(y * h / ICON) * sheet.width + fx + x * h / ICON]
                }
            }
            if (!write(out, Bitmap.createBitmap(outPx, ICON, ICON * 2, Bitmap.Config.ARGB_8888))) return null
        }
        return "pc/$name"
    }

    /** An item's icon as the game has it ("pc/i<crc>.png"); null = none. */
    private fun itemIcon(file: String?): String? {
        file ?: return null
        val name = "i${crc(file)}.png"
        val out = File(iconDir, name)
        if (!out.isFile && !write(out, load(file) ?: return null)) return null
        return "pc/$name"
    }

    /** A PNG from the game's folder; Essentials hands paths without their extension. */
    private fun load(rel: String): Bitmap? {
        val opts = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val f = File(gameDir, rel).takeIf { it.isFile } ?: File(gameDir, "$rel.png").takeIf { it.isFile } ?: return null
        return runCatching { BitmapFactory.decodeFile(f.path, opts) }.getOrNull()
    }

    private fun write(out: File, bitmap: Bitmap): Boolean = runCatching {
        val tmp = File(out.path + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(out)
    }.getOrDefault(false)

    private fun crc(s: String) = CRC32().apply { update(gameDir.path.toByteArray()); update(s.toByteArray()) }.value.toString(16)

    private fun ints(a: JSONArray?): List<Int> = a?.let { (0 until it.length()).mapNotNull { i -> it.optInt(i, Int.MIN_VALUE).takeIf { v -> v != Int.MIN_VALUE } } }.orEmpty()

    private companion object {
        /** The party icon as the companion's slots draw it (the games' are 64 x 64, drawn 2x). */
        const val ICON = 32
    }
}
