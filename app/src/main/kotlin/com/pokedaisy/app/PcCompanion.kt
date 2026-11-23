package com.pokedaisy.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pokedaisy.app.companion.data.BATTLE_INPUT_ACTION_SELECT
import com.pokedaisy.app.companion.data.BATTLE_INPUT_BAG_OPEN
import com.pokedaisy.app.companion.data.BATTLE_INPUT_BUSY
import com.pokedaisy.app.companion.data.BATTLE_INPUT_MOVE_SELECT
import com.pokedaisy.app.companion.data.BATTLE_INPUT_NONE
import com.pokedaisy.app.companion.data.BATTLE_INPUT_PARTY_OPEN
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
import com.pokedaisy.app.companion.data.PcGuide
import com.pokedaisy.app.companion.data.PcTownMap
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.data.TYPE_NONE
import com.pokedaisy.app.companion.data.typeMatchups
import com.pokedaisy.app.companion.data.withMovesVs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.CRC32

/**
 * A PC fan game's companion data ([GameKind.ESSENTIALS]): the snapshot our preload sends from the game
 * (linux/pc_games/pokedaisy_companion.rb, through [PcCore.pcMailbox]) turned into the [SnapshotView] the real
 * companion draws - PARTY with its summaries, the bag, the MAP ([PcTownMap]: the game's own Town Map picture and
 * places) and the GUIDE's HERE ([PcGuide]: the map's wild Pokémon), every name and type the game's own. Party and item icons are
 * the game's PNGs, rebuilt into the art cache the companion already reads ([iconDir]): a party icon as the companion's
 * 32 x 64 two-frame sheet, a fusion's put together the way Infinite Fusion's createFusionIcon does (linux/pc_companion.c).
 *
 * Read [snapshot] off the UI thread: it decodes PNGs the first time it meets them. [GameKind.ESSENTIALS] must be the
 * [com.pokedaisy.app.companion.data.activeGame] while it runs ([PcGameTypes] backs the type lookups then).
 */
class PcCompanion(private val gameDir: File, filesDir: File) {
    /** Where [com.pokedaisy.app.companion.ui.GameArt] finds rom-art files ([com.pokedaisy.app.companion.data.RomArt.file]). */
    private val iconDir = File(filesDir, "rom-art/pc").apply { mkdirs() }
    /** The Town Map's picture and the player's mark, where [com.pokedaisy.app.companion.ui.regionMapBitmap] reads them. */
    private val mapDir = File(filesDir, "rom-art/regionmap/pc")
    private var lastTypes: String? = null
    private var lastTownMap: String? = null
    private var lastWild: String? = null

    init {
        // Another game's map must not show before this one's first snapshot.
        PcTownMap.clear()
        PcGuide.clear()
    }

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
        val town = townMap(o.optJSONObject("townMap"))
        val place = town?.name ?: map?.optString("name").orEmpty()
        wild(o.optJSONArray("wild"), place)
        // The battle (pbStartBattle's own object): the battlers on each side as the battle has them, each move's verdict
        // on the other side, and a trainer's whole team for the FOE TEAM strip.
        val battle = o.optJSONObject("battle")
        val player = battlers(battle?.optJSONArray("player"), slotBase = 0)
        val foes = battlers(battle?.optJSONArray("foes"), slotBase = FOE_SLOTS)
        val foeParty = battle?.optJSONArray("foeParty")?.let { a ->
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { mon(it, FOE_SLOTS + i) } }
        }.orEmpty()
        return SnapshotView(
            connected = true,
            game = GameKind.ESSENTIALS,
            frameCounter = o.optLong("frame"),
            party = party,
            items = items,
            // The Town Map's name for the place (as the GBA games' map sections: a town's, not its buildings'), else the
            // game's own map name.
            location = LocationView(place, null, 0, 0, 0, 0),
            regionMapSectionId = town?.section ?: 0,
            regionMapTile = town?.tile,
            x = map?.optInt("x") ?: 0,
            y = map?.optInt("y") ?: 0,
            money = trainer?.takeIf { it.has("money") }?.optLong("money"),
            inBattle = battle != null,
            isDoubleBattle = battle?.optBoolean("double") == true,
            isTrainerBattle = battle?.let { it.optBoolean("trainer") },
            battlePlayer = player.map { it.first.withMovesVs(foes.map { f -> f.first }) },
            battleOpponent = foes.map { it.first.withMovesVs(player.map { p -> p.first }) },
            playerBattlers = player.map { it.second },
            enemyBattlers = foes.map { it.second },
            enemyParty = foeParty,
            enemyActive = foes.firstOrNull()?.second?.takeIf { foeParty.isNotEmpty() } ?: -1,
            // Which of the game's menus waits (the preload's hooks): the companion's battle buttons follow it.
            // (No menu named - JSON null reads "null" - is the battle between menus: messages, animations.)
            battleInputState = if (battle == null) BATTLE_INPUT_NONE else when (battle.optString("menu")) {
                "command" -> BATTLE_INPUT_ACTION_SELECT
                "fight" -> BATTLE_INPUT_MOVE_SELECT
                "party" -> BATTLE_INPUT_PARTY_OPEN
                "bag" -> BATTLE_INPUT_BAG_OPEN
                else -> BATTLE_INPUT_BUSY
            },
            battleActiveBattler = battle?.optInt("menuBattler") ?: 0,
        )
    }

    /** The GUIDE's HERE for the map's wild Pokémon ([PcGuide]), rebuilt when they or the place change; none without
     * the game's encounters (it has no `$PokemonEncounters`). */
    private fun wild(a: JSONArray?, place: String) {
        val key = a?.let { "$place|$it" }
        if (key == lastWild) return
        lastWild = key
        PcGuide.set(a?.let {
            PcGuide.of(place, (0 until it.length()).mapNotNull { i ->
                val k = it.optJSONObject(i) ?: return@mapNotNull null
                val slots = k.optJSONArray("slots") ?: return@mapNotNull null
                PcGuide.Kind(k.optString("kind"), (0 until slots.length()).mapNotNull { j ->
                    val s = slots.optJSONObject(j) ?: return@mapNotNull null
                    PcGuide.Slot(
                        name = s.optString("name"),
                        min = s.optInt("min"),
                        max = s.optInt("max"),
                        percent = s.optInt("percent"),
                        seen = if (s.isNull("seen")) null else s.optBoolean("seen"),
                        owned = if (s.isNull("owned")) null else s.optBoolean("owned"),
                        icon = icon(s.optJSONObject("icon")),
                    )
                })
            })
        })
    }

    /** Where the player is on the Town Map: the place's section ([PcTownMap.build]'s), its name (null where the map
     * names none) and the tile. */
    private class TownSpot(val section: Int, val name: String?, val tile: Pair<Int, Int>)

    /** [PcTownMap]'s model for the player's region, rebuilt when the region, its shown places, its picture or the mark
     * change; then where the player is on it. Null without a position (the model stays: the last region's map). */
    private fun townMap(t: JSONObject?): TownSpot? {
        t ?: return null
        val key = listOf(t.optInt("region"), t.optString("picture"), t.optJSONArray("hidden")?.toString(), t.optString("head"),
            t.optJSONArray("points")?.length()).joinToString("|")
        if (key != lastTownMap) {
            lastTownMap = key
            val points = t.optJSONArray("points")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val p = a.optJSONArray(i) ?: return@mapNotNull null
                    PcTownMap.Point(p.optInt(0, -1), p.optInt(1, -1), text(p, 2).orEmpty(), text(p, 3))
                }
            }.orEmpty()
            PcTownMap.head = mapArt(t.optString("head"), "h", pictures = false)
            PcTownMap.set(mapArt(t.optString("picture"), "t", pictures = true)?.let {
                PcTownMap.build(it, t.optInt("tile", 16), t.optInt("centre"), points, ints(t.optJSONArray("hidden")).toSet())
            })
        }
        val model = PcTownMap.model ?: return null
        val x = t.optInt("x")
        val y = t.optInt("y")
        val section = PcTownMap.sectionAt(model, x, y)
        return TownSpot(section, model.sections[section]?.name?.takeIf { it.isNotEmpty() }, x to y)
    }

    /** A Town Map picture ([pictures]: a name under Graphics/Pictures, or v21's Graphics/UI/Town Map) or the player's
     * mark (a path from the game's folder), copied as it is into [mapDir]; its regionmap asset ("pc/t<crc>"), null = none. */
    private fun mapArt(rel: String, prefix: String, pictures: Boolean): String? {
        if (rel.isEmpty() || rel == "null") return null
        val bases = if (pictures) listOf("Graphics/Pictures/$rel", "Graphics/UI/Town Map/$rel") else listOf(rel)
        val file = bases.flatMap { listOf(it, "$it.png") }.firstNotNullOfOrNull { gameFile(it) } ?: return null
        val name = "$prefix${crc("${file.path}|${file.length()}|${file.lastModified()}")}"
        val out = File(mapDir, "$name.png")
        if (!out.isFile) runCatching {
            mapDir.mkdirs()
            val tmp = File(out.path + ".tmp")
            file.copyTo(tmp, overwrite = true)
            tmp.renameTo(out)
        }
        return if (out.isFile) "pc/$name" else null
    }

    private fun text(a: JSONArray, i: Int): String? = if (a.isNull(i)) null else a.optString(i).takeIf { it.isNotEmpty() }

    /** A side's battlers ([mon], then the battle's stats, stages, item and ability) with each one's party index. */
    private fun battlers(a: JSONArray?, slotBase: Int): List<Pair<MonView, Int>> = a?.let {
        (0 until it.length()).mapNotNull { i ->
            val b = it.optJSONObject(i) ?: return@mapNotNull null
            val partyIndex = b.optInt("partyIndex", -1)
            val view = mon(b, slotBase + partyIndex.coerceAtLeast(0)).copy(
                battleStats = ints(b.optJSONArray("battleStats")).takeIf { s -> s.size == 5 },
                statStages = ints(b.optJSONArray("stages")).takeIf { s -> s.size == 7 },
            )
            view to partyIndex
        }
    }.orEmpty()

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
            // The key the battle's switch goes by ([PcBattleInput.switchTo]): the party slot, + 1 (0 = unknown).
            personality = (slot + 1).toLong(),
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
                    // The pocket's kind where the preload names it (the Reborn engine's order is its own), else Essentials'.
                    pocket = pocketKind(pocket.optString("kind")) ?: kinds.getOrElse(p) { POCKET_ITEMS },
                    description = it.optString("desc").takeIf { s -> s != "null" }.orEmpty(),
                )
            }
        }
        return out
    }

    private fun pocketKind(kind: String): Int? = when (kind) {
        "items" -> POCKET_ITEMS
        "medicine" -> POCKET_MEDICINE
        "balls" -> POCKET_POKE_BALLS
        "tms" -> POCKET_TM_HM
        "berries" -> POCKET_BERRIES
        "mail" -> POCKET_MAIL
        "battle" -> POCKET_BATTLE_ITEMS
        "key" -> POCKET_KEY_ITEMS
        else -> null
    }

    // ---- icons ---------------------------------------------------------------------------------------------------

    /** The party icon's rom-art path ("pc/m<crc>.png"), built once per file (or head + body + offset); null = none. */
    private fun icon(icon: JSONObject?): String? {
        icon ?: return null
        val file = icon.optString("file").takeIf { it.isNotEmpty() && it != "null" }
        val head = icon.optString("head").takeIf { it.isNotEmpty() && it != "null" }
        val body = icon.optString("body").takeIf { it.isNotEmpty() && it != "null" }
        val offset = icon.optInt("offset", 10)
        // A row of a bigger sheet (the Reborn engine's: a 128 x 64 row a form, shiny ones to the right).
        val cell = if (icon.has("w") && icon.has("h")) intArrayOf(icon.optInt("x"), icon.optInt("y"), icon.optInt("w"), icon.optInt("h")) else null
        val key = (file?.let { f -> cell?.let { "$f|${it.joinToString(",")}" } ?: f })
            ?: if (head != null && body != null) "$head|$body|$offset" else return null
        val name = "m${crc(key)}.png"
        val out = File(iconDir, name)
        if (!out.isFile) {
            val sheet = (load(file ?: head!!) ?: return null).let { s -> cell?.let { crop(s, it) } ?: s }
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

    /** [cell] (x, y, w, h) of [sheet]; a row past its end is its first (their pbPokemonIconBitmap's rule). */
    private fun crop(sheet: Bitmap, cell: IntArray): Bitmap? {
        val (x, y0, w, h) = cell.toList()
        val y = if (y0 + h > sheet.height) 0 else y0
        if (w <= 0 || h <= 0 || x + w > sheet.width || y + h > sheet.height) return if (x == 0) sheet else null
        return runCatching { Bitmap.createBitmap(sheet, x, y, w, h) }.getOrNull()
    }

    /** A PNG from the game's folder; Essentials hands paths without their extension. */
    private fun load(rel: String): Bitmap? {
        val opts = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val f = gameFile(rel) ?: gameFile("$rel.png") ?: return null
        return runCatching { BitmapFactory.decodeFile(f.path, opts) }.getOrNull()
    }

    /** [rel] in the game's folder, its name matched in any case where it isn't there as given: the Reborn engine's
     * scripts ask for POTION where the file is potion.png, which their own mkxp-z build forgives. */
    internal fun gameFile(rel: String): File? {
        File(gameDir, rel).takeIf { it.isFile }?.let { return it }
        val parent = File(gameDir, rel).parentFile ?: return null
        val names = folderNames.getOrPut(parent.path) { parent.list()?.associateBy { it.lowercase() }.orEmpty() }
        return names[File(rel).name.lowercase()]?.let { File(parent, it) }?.takeIf { it.isFile }
    }

    private val folderNames = HashMap<String, Map<String, String>>()

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

        /** Where a foe's [MonView.species] starts: the companion matches battlers to the party by it ([mon]'s slot + 1). */
        const val FOE_SLOTS = 100
    }
}
