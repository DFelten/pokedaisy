package com.pokedaisy.app.companion.data

import okio.Path

/**
 * Crystal's and Gold / Silver's TRAINER CARD (the CARD tab), drawn the way pret/pokecrystal's (and pret/pokegold's, the
 * same code but for the picture: see [Tables.rowMajor]) engine/menus/trainer_card.asm builds it: a
 * 20x18 tilemap with its attributes (`_CGB_TrainerCard`), the tiles each page loads and the badges as sprites. Two pages,
 * as in the game: the card's top half (name, ID, money, the player's picture) over STATUS (POKéDEX, PLAY TIME) or over
 * BADGES (the eight Johto leaders, a spinning badge on each one beaten). Its third page (Kanto's) is never reached in the
 * game - nothing jumps to it - so it isn't drawn here either.
 *
 * Nothing of the game is bundled: [extract] cuts the card's tiles, palettes and words out of the player's cart (by
 * [Tables], found in the card's own code: scripts/find_crystal_card.py) into rom-art ([PATH]), where [load] reads them.
 */
object Gen2TrainerCard {
    /** The card's pieces in rom-art: one file per [CardStyle], written by [Gen2Art.extractTo] for the cart that has a [Tables]. */
    fun path(style: CardStyle) = if (style == CardStyle.GOLD_SILVER) "gen2/gold_silver/trainer_card.bin" else "gen2/crystal/trainer_card.bin"

    /** The Game Boy cards' styles: [Gen2TrainerCard]'s, not [TrainerCardArt]'s. */
    val STYLES = setOf(CardStyle.CRYSTAL, CardStyle.GOLD_SILVER)

    /**
     * Where a Crystal cart keeps what its card draws (bank, address). English rev 0 / rev 1 / AU are pokecrystal.sym's;
     * German Kristall's are the same code's immediates (find_crystal_card.py). Gold / Silver's (pokegold.sym's, German
     * Goldene / Silberne's by the same script) differ in the picture: one blob with the frame (ChrisPicAndTrainerCardGFX,
     * [chris] = [kris], [frame] its last 6 tiles) put on the card row by row ([rowMajor]), with no corner tile of its own:
     * its border puts the picture's tile $04 there ([corner] is that tile), and $1C is a picture tile like the rest.
     */
    class Tables(
        val status: Pair<Int, Int>,       // CardStatusGFX: page 1 copies 86 tiles from here to tile $29 (6 of its own, then LeaderGFX's)
        val leaders: Pair<Int, Int>,      // LeaderGFX: page 2's 86 tiles from $29 (the faces, then BADGES)
        val badges: Pair<Int, Int>,       // BadgeGFX: 44 OBJ tiles from 0 (each badge's 4, then the spin's)
        val corner: Pair<Int, Int>,       // CardRightCornerGFX: tile $1C
        val chris: Pair<Int, Int>,        // ChrisCardPic, KrisCardPic: $23 tiles from 0, 5 x 7 tiles column by column
        val kris: Pair<Int, Int>,
        val frame: Pair<Int, Int>,        // TrainerCardGFX: tiles $23-$28 (border, corners, the divider, ID NO)
        val trainerPals: Pair<Int, Int>,  // TrainerPalettes: 2 colours a trainer class (CHRIS = 0)
        val badgePal: Pair<Int, Int>,     // PredefPals' PREDEFPAL_CGB_BADGE: the badges' OBJ palette
        val nameMoney: Pair<Int, Int>,    // TrainerCard_PrintTopHalfOfCard.Name_Money (at 2, 2)
        val nameX: Int,                   // where the player's name goes on row 2 (English 7, after NAME/; German 2: no label)
        val dexPlayTime: Pair<Int, Int>,  // .Dex_PlayTime (at 2, 10)
        val badgesText: Pair<Int, Int>,   // .Badges (at 10, 15 in Crystal; Gold at 12, 15 with two spaces less)
        val badgesX: Int = 10,
        val rowMajor: Boolean = false,
        val style: CardStyle = CardStyle.CRYSTAL,
    )

    val CRYSTAL_TABLES = Tables(
        status = 0x09 to 0x5523, leaders = 0x09 to 0x5583, badges = 0x09 to 0x6043, corner = 0x09 to 0x65C3,
        chris = 0x22 to 0x4365, kris = 0x22 to 0x4595, frame = 0x22 to 0x47C5,
        trainerPals = 0x02 to 0x70CE, badgePal = 0x02 to 0x5F16,
        nameMoney = 0x09 to 0x52EC, nameX = 7, dexPlayTime = 0x09 to 0x534C, badgesText = 0x09 to 0x535C,
    )

    /** German Kristall: English's code, its data 6 bytes on in bank 9 and 3 back in bank 0x22; no NAME/ label. */
    val CRYSTAL_DE_TABLES = Tables(
        status = 0x09 to 0x5529, leaders = 0x09 to 0x5589, badges = 0x09 to 0x6049, corner = 0x09 to 0x65C9,
        chris = 0x22 to 0x4362, kris = 0x22 to 0x4592, frame = 0x22 to 0x47C2,
        trainerPals = 0x02 to 0x70CE, badgePal = 0x02 to 0x5F16,
        nameMoney = 0x09 to 0x52F8, nameX = 2, dexPlayTime = 0x09 to 0x5352, badgesText = 0x09 to 0x5362,
    )

    /** Gold and Silver (pokegold.sym: the two are the same here). */
    val GOLD_SILVER_TABLES = Tables(
        status = 0x09 to 0x570F, leaders = 0x09 to 0x576F, badges = 0x09 to 0x622F, corner = 0x09 to 0x54BF,
        chris = 0x09 to 0x547F, kris = 0x09 to 0x547F, frame = 0x09 to 0x56AF,
        trainerPals = 0x02 to 0x753D, badgePal = 0x02 to 0x6385,
        nameMoney = 0x09 to 0x524A, nameX = 7, dexPlayTime = 0x09 to 0x52AA, badgesText = 0x09 to 0x52BA, badgesX = 12,
        rowMajor = true, style = CardStyle.GOLD_SILVER,
    )

    /** German Goldene / Silberne Edition: English's code, bank 9's data 5 bytes on (its words a few); no NAME/ label. */
    val GOLD_SILVER_DE_TABLES = Tables(
        status = 0x09 to 0x5714, leaders = 0x09 to 0x5774, badges = 0x09 to 0x6234, corner = 0x09 to 0x54C4,
        chris = 0x09 to 0x5484, kris = 0x09 to 0x5484, frame = 0x09 to 0x56B4,
        trainerPals = 0x02 to 0x753D, badgePal = 0x02 to 0x6385,
        nameMoney = 0x09 to 0x5253, nameX = 2, dexPlayTime = 0x09 to 0x52AD, badgesText = 0x09 to 0x52BD, badgesX = 10,
        rowMajor = true, style = CardStyle.GOLD_SILVER,
    )

    // _CGB_TrainerCard's BG palettes, by trainer class: CHRIS (0), FALKNER (KRIS's), BUGSY, WHITNEY, MORTY, CHUCK, JASMINE, PRYCE.
    private val PAL_CLASSES = intArrayOf(0, 1, 3, 2, 4, 7, 6, 5)

    private const val MAGIC = 0x43445032 // "PDC2"
    private const val TILE = 16
    private const val PAGE_TILES = 86

    /** Wiped by a new [extract] layout: a file of another layout is read as none (then rebuilt with the art). */
    private const val VERSION = 2

    private fun off(t: Pair<Int, Int>) = if (t.first == 0) t.second else t.first * 0x4000 + (t.second - 0x4000)

    /** The card's pieces from [rom] (a cart [t] fits), as [PATH] holds them. */
    fun extract(rom: ByteArray, t: Tables, font: Pair<Int, Int>): ByteArray {
        val out = okio.Buffer()
        fun put(b: ByteArray) = out.write(b)
        fun cut(at: Pair<Int, Int>, n: Int) = off(at).let { rom.copyOfRange(it, it + n) }
        fun text(at: Pair<Int, Int>): ByteArray {
            val o = off(at)
            var n = 0
            while (n < 40 && rom[o + n].toInt() and 0xFF != EOS) n++
            return rom.copyOfRange(o, o + n)
        }
        put(byteArrayOf((MAGIC ushr 24).toByte(), (MAGIC ushr 16).toByte(), (MAGIC ushr 8).toByte(), MAGIC.toByte(), VERSION.toByte()))
        put(cut(font, 128 * 8))
        put(cut(t.chris, 0x23 * TILE))
        put(cut(t.kris, 0x23 * TILE))
        put(cut(t.frame, 6 * TILE))
        put(cut(t.corner, TILE))
        put(cut(t.status, PAGE_TILES * TILE))
        put(cut(t.leaders, PAGE_TILES * TILE))
        put(cut(t.badges, 44 * TILE))
        // LoadPalette_White_Col1_Col2_Black: white, the class's two colours, black.
        val pals = off(t.trainerPals)
        for (c in PAL_CLASSES) {
            put(byteArrayOf(0xFF.toByte(), 0x7F))
            put(rom.copyOfRange(pals + 4 * c, pals + 4 * c + 4))
            put(byteArrayOf(0, 0))
        }
        put(cut(t.badgePal, 8))
        out.writeByte(t.nameX)
        out.writeByte(t.badgesX)
        out.writeByte(if (t.rowMajor) 1 else 0)
        for (s in listOf(text(t.nameMoney), text(t.dexPlayTime), text(t.badgesText))) {
            out.writeByte(s.size)
            put(s)
        }
        return out.readByteArray()
    }

    /** The card's pieces, read back from [PATH]. */
    class Art internal constructor(
        val font: ByteArray, val chris: ByteArray, val kris: ByteArray, val frame: ByteArray, val corner: ByteArray,
        val status: ByteArray, val leaders: ByteArray, val badges: ByteArray,
        val bgPals: Array<IntArray>, val badgePal: IntArray,
        val nameX: Int, val nameMoney: ByteArray, val dexPlayTime: ByteArray, val badgesText: ByteArray,
        val badgesX: Int, val rowMajor: Boolean, override val style: CardStyle,
    ) : CardArt

    fun parse(b: ByteArray, style: CardStyle = CardStyle.CRYSTAL): Art? = runCatching {
        if (b.size < 5 || ((b[0].u shl 24) or (b[1].u shl 16) or (b[2].u shl 8) or b[3].u) != MAGIC || b[4].u != VERSION) return null
        var o = 5
        fun take(n: Int) = b.copyOfRange(o, o + n).also { o += n }
        val font = take(128 * 8)
        val chris = take(0x23 * TILE)
        val kris = take(0x23 * TILE)
        val frame = take(6 * TILE)
        val corner = take(TILE)
        val status = take(PAGE_TILES * TILE)
        val leaders = take(PAGE_TILES * TILE)
        val badges = take(44 * TILE)
        fun pal(p: ByteArray) = IntArray(4) { rgb(p[2 * it].u or (p[2 * it + 1].u shl 8)) }
        val bgPals = Array(8) { pal(take(8)) }
        val badgePal = pal(take(8))
        val nameX = take(1)[0].u
        val badgesX = take(1)[0].u
        val rowMajor = take(1)[0].u != 0
        fun str() = take(take(1)[0].u)
        Art(font, chris, kris, frame, corner, status, leaders, badges, bgPals, badgePal, nameX, str(), str(), str(), badgesX, rowMajor, style)
    }.getOrNull()

    private val loaded = HashMap<CardStyle, Art>()
    private val loadedLock = kotlinx.atomicfu.locks.SynchronizedObject()

    /** [style]'s card art [Gen2Art] cut into rom-art [dir], or null before it has. */
    fun load(dir: Path, style: CardStyle): Art? {
        val f = dir / path(style)
        if (!f.isFile) return null
        return parse(f.readBytes(), style)?.also { kotlinx.atomicfu.locks.synchronized(loadedLock) { loaded[style] = it } }
    }

    /** The last [load]ed card of [style], so a screen can start drawing it without waiting. */
    fun cached(style: CardStyle): Art? = kotlinx.atomicfu.locks.synchronized(loadedLock) { loaded[style] }

    // ---- The save's side: the card's numbers (TrainerCard_PrintTopHalfOfCard, TrainerCard_Page1_PrintDexCaught_GameTime). ----

    /**
     * Where a game keeps the card's own numbers (bank 1): wPlayerID (a big-endian word, then wPlayerName, 11 bytes),
     * wStatusFlags (bit 0, STATUSFLAGS_POKEDEX_F: has the POKéDEX) and wJohtoBadges (bit i = badge i + 1).
     */
    class Ram(val playerId: Int, val statusFlags: Int, val johtoBadges: Int, val style: CardStyle)

    /** Crystal and German Kristall (pokecrystal.sym). */
    val CRYSTAL_RAM = Ram(playerId = 0xD47B, statusFlags = 0xD84C, johtoBadges = 0xD857, style = CardStyle.CRYSTAL)
    /** Gold / Silver, English and German (pokegold.sym; the German carts keep English's RAM). */
    val GOLD_SILVER_RAM = Ram(playerId = 0xD1A1, statusFlags = 0xD571, johtoBadges = 0xD57C, style = CardStyle.GOLD_SILVER)

    /** The card's RAM for [cfg], or null for a game without a mapped card. */
    fun ramFor(cfg: Gen2Config): Ram? = when {
        cfg === GEN2_CRYSTAL || cfg === GEN2_CRYSTAL_DE -> CRYSTAL_RAM
        cfg === GEN2_GOLD || cfg === GEN2_SILVER || cfg === GEN2_GOLD_DE || cfg === GEN2_SILVER_DE -> GOLD_SILVER_RAM
        else -> null
    }

    private const val NAME_LEN = 11

    /** What [cfg]'s card shows, or null before a save is up (no player name yet) or for a game without a card. */
    fun read(r: MemoryReader, cfg: Gen2Config): TrainerCardInfo? = runCatching {
        val ram = ramFor(cfg) ?: return null
        val head = r.readCoreMemory(wramx(1, ram.playerId), 2 + NAME_LEN)
        val name = (0 until NAME_LEN).map { head[2 + it].u }.takeWhile { it != EOS }
        // The title screen / a new game before naming: no name, or WRAM that isn't one.
        if (name.isEmpty() || name.size > 10 || name.any { it < 0x7F }) return null
        val t = r.readCoreMemory(cfg.gameTime, 3)
        val m = r.readCoreMemory(cfg.money, 3)
        val dex = r.readCoreMemory(cfg.dexCaught, 32)
        val hasDex = r.readCoreMemory(wramx(1, ram.statusFlags), 1)[0].u and 1 != 0
        TrainerCardInfo(
            style = ram.style,
            name = name,
            female = cfg.playerGender >= 0 && r.readCoreMemory(cfg.playerGender, 1)[0].u and 1 != 0,
            trainerId = (head[0].u shl 8) or head[1].u,
            hours = (t[0].u shl 8) or t[1].u,
            minutes = t[2].u,
            money = ((m[0].u.toLong() shl 16) or (m[1].u.toLong() shl 8) or m[2].u.toLong()),
            // CountSetBits into a byte (wNumSetBits): 251 at most.
            dexCaught = if (hasDex) dex.sumOf { b: Byte -> b.u.countOneBits() } and 0xFF else null,
            badges = r.readCoreMemory(wramx(1, ram.johtoBadges), 1)[0].u,
            stars = 0,
            hofDebut = 0,
        )
    }.getOrNull()

    // ---- Drawing. ----

    const val WIDTH = 160
    const val HEIGHT = 144
    private const val W = 20
    private const val H = 18
    private const val EOS = 0x50
    private const val NEXT = 0x4E
    private const val POKE = 0x54          // "#": PlacePOKe prints POKé
    private val POKE_TEXT = intArrayOf(0x8F, 0x8E, 0x8A, 0xEA)
    private const val SPACE = 0x7F
    private const val ZERO = 0xF6
    private const val YEN = 0xF0
    private const val SMALL_COLON = 0x2E

    /** The badges' frames, 8 each (TrainerCard_JohtoBadgesOAM): the badge, then its spin; RISINGBADGE mirrors its face on the second cycle. */
    private val BADGE_Y = intArrayOf(0x68, 0x68, 0x68, 0x68, 0x80, 0x80, 0x80, 0x80)
    private val BADGE_X = intArrayOf(0x18, 0x38, 0x58, 0x78, 0x38, 0x18, 0x58, 0x78)

    private fun badgeTile(badge: Int, phase: Int): Int {
        val face = 4 * badge
        return when (phase % 4) {
            0 -> if (badge == 7 && phase >= 4) face or 0x80 else face
            1 -> 0x20
            2 -> 0x24
            else -> 0x20 or 0x80
        }
    }

    /**
     * The card's screen, 160x144: page 1 (STATUS) or [badgesPage]. [phase] is the game's 64-frame cycle in eighths
     * (0-7): the badges spin a step each eighth (TrainerCard_Page2_3_AnimateBadges) and the play time's colon shows in
     * its second half (TrainerCard_Page1_PrintGameTime flips it every 32 frames).
     */
    fun render(art: Art, card: TrainerCardInfo, badgesPage: Boolean, phase: Int = 0): RomArt.Image {
        val map = IntArray(W * H) { SPACE }
        fun put(x: Int, y: Int, t: Int) { if (x in 0 until W && y in 0 until H) map[y * W + x] = t }
        fun border(top: Int, rows: Int) {
            var y = top
            for (x in 0 until W) put(x, y, 0x23)
            y++
            put(0, y, 0x23); put(18, y, if (art.rowMajor) 0x04 else 0x1C); put(19, y, 0x23)
            repeat(rows) { y++; put(0, y, 0x23); put(19, y, 0x23) }
            y++
            put(0, y, 0x23); put(1, y, 0x24); put(19, y, 0x23)
            y++
            for (x in 0 until W) put(x, y, 0x23)
        }
        fun string(x0: Int, y0: Int, s: List<Int>) {
            var x = x0
            var line = y0
            for (c in s) when (c) {
                EOS -> return
                NEXT -> { line += 2; x = x0 }
                POKE -> POKE_TEXT.forEach { put(x++, line, it) }
                else -> put(x++, line, c)
            }
        }
        fun digits(x: Int, y: Int, value: Long, n: Int, zeros: Boolean, money: Boolean = false) {
            val s = value.coerceIn(0, "9".repeat(n).toLong()).toString().let { if (zeros) it.padStart(n, '0') else it }
            // Money: the ¥ right before the first digit, the field a place wider for it (PrintNum's money flag).
            val end = x + n - 1 + if (money) 1 else 0
            s.forEachIndexed { i, ch -> put(end - (s.length - 1 - i), y, ZERO + (ch - '0')) }
            if (money) put(end - s.length, y, YEN)
        }

        // TrainerCard_PrintTopHalfOfCard.
        border(0, 5)
        string(2, 2, art.nameMoney.map { it.u })
        put(2, 4, 0x27); put(3, 4, 0x28)
        string(art.nameX, 2, card.name)
        digits(5, 4, card.trainerId.toLong(), 5, zeros = true)
        digits(7, 6, card.money, 6, zeros = false, money = true)
        for (x in 1..12) put(x, 3, 0x25)
        put(13, 3, 0x26)
        // Crystal's PlaceGraphic goes column by column; Gold's own loop row by row.
        for (x in 0 until 5) for (y in 0 until 7) put(14 + x, 1 + y, if (art.rowMajor) y * 5 + x else x * 7 + y)
        // The bottom half (TrainerCard_InitBorder over row 8 on).
        border(8, 6)
        if (!badgesPage) {
            string(2, 10, art.dexPlayTime.map { it.u })
            string(art.badgesX, 15, art.badgesText.map { it.u })
            card.dexCaught?.let { digits(15, 10, it.toLong(), 3, zeros = false) }
            digits(11, 12, card.hours.toLong(), 4, zeros = false)
            if (phase >= 4) put(15, 12, SMALL_COLON)
            digits(16, 12, card.minutes.toLong(), 2, zeros = true)
            for (i in 0 until 5) put(2 + i, 8, 0x29 + i)
            if (card.dexCaught == null) for (y in 9..10) for (x in 1..17) put(x, y, SPACE)
        } else {
            for (i in 0 until 5) put(2 + i, 8, 0x79 + i)
            for (face in 0 until 8) {
                val x = 2 + 4 * (face % 4)
                val y = 10 + 3 * (face / 4)
                var t = (if (face < 4) 0x29 else 0x51) + 10 * (face % 4)
                for (dx in 0 until 4) put(x + dx, y, t++)
                for (dy in 1..2) for (dx in 1..3) put(x + dx, y + dy, t++)
            }
        }

        // _CGB_TrainerCard's attributes: the border in the other gender's palette, the picture in the player's (but its
        // top-right corner tile), the leaders in theirs; CLAIR in KRIS's (the border's for CHRIS).
        val attr = IntArray(W * H) { if (card.female) 0 else 1 }
        fun box(x0: Int, y0: Int, w: Int, h: Int, p: Int) { for (y in y0 until y0 + h) for (x in x0 until x0 + w) attr[y * W + x] = p }
        box(14, 1, 5, 7, if (card.female) 1 else 0)
        attr[1 * W + 18] = if (card.female) 0 else 1
        box(2, 11, 4, 2, 1); box(6, 11, 4, 2, 2); box(10, 11, 4, 2, 3); box(14, 11, 4, 2, 4)
        box(2, 14, 4, 2, 5); box(6, 14, 4, 2, 6); box(10, 14, 4, 2, 7)
        if (card.female) box(14, 14, 4, 2, 1)

        val pic = if (card.female) art.kris else art.chris
        val page = if (badgesPage) art.leaders else art.status
        val img = RomArt.Image(WIDTH, HEIGHT)
        for (ty in 0 until H) for (tx in 0 until W) {
            val t = map[ty * W + tx]
            val pal = art.bgPals[attr[ty * W + tx]]
            for (py in 0 until 8) for (px in 0 until 8) {
                val c = when (t) {
                    0x1C -> if (art.rowMajor) px2(pic, t * TILE, px, py) else px2(art.corner, 0, px, py)
                    in 0x00..0x22 -> px2(pic, t * TILE, px, py)
                    in 0x23..0x28 -> px2(art.frame, (t - 0x23) * TILE, px, py)
                    in 0x29..0x7E -> px2(page, (t - 0x29) * TILE, px, py)
                    SPACE -> 0
                    else -> if (art.font[(t - 0x80) * 8 + py].u shr (7 - px) and 1 != 0) 3 else 0
                }
                img.argb[(ty * 8 + py) * WIDTH + tx * 8 + px] = pal[c]
            }
        }
        // The badges: a 16x16 OBJ each (colour 0 see-through), mirrored halves when the tile's top bit says so.
        if (badgesPage) for (b in 0 until 8) if (card.badges shr b and 1 != 0) {
            val tile = badgeTile(b, phase)
            val flip = tile and 0x80 != 0
            val base = tile and 0x7F
            val x0 = BADGE_X[b] - 8
            val y0 = BADGE_Y[b] - 16
            for (y in 0 until 16) for (x in 0 until 16) {
                val sx = if (flip) 15 - x else x
                val c = px2(art.badges, (base + (y / 8) * 2 + sx / 8) * TILE, sx % 8, y % 8)
                if (c != 0) img.argb[(y0 + y) * WIDTH + x0 + x] = art.badgePal[c]
            }
        }
        return img
    }

    private val Byte.u get() = toInt() and 0xFF

    private fun px2(tiles: ByteArray, o: Int, x: Int, y: Int): Int {
        val lo = tiles[o + 2 * y].u shr (7 - x) and 1
        val hi = tiles[o + 2 * y + 1].u shr (7 - x) and 1
        return lo or (hi shl 1)
    }

    /** GBC RGB555 as ARGB, the way mGBA shows it (Gen2Art's). */
    private fun rgb(c: Int): Int {
        fun ch(v: Int) = ((v and 31) shl 3) or ((v and 31) shr 2)
        return (0xFF shl 24) or (ch(c) shl 16) or (ch(c shr 5) shl 8) or ch(c shr 10)
    }
}
