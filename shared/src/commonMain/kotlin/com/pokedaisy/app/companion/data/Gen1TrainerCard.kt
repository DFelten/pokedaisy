package com.pokedaisy.app.companion.data

import okio.Path

/**
 * Red's, Blue's and Yellow's TRAINER CARD (the START menu's player name, the CARD tab), drawn the way
 * pret/pokered's and pret/pokeyellow's engine/menus/start_sub_menus.asm (DrawTrainerInfo) and draw_badges.asm
 * (DrawBadges) build it: one 20x18 screen, the player's picture, NAME / MONEY / TIME and the eight gym leaders, a badge
 * in place of each one beaten. Its colours are SET_PAL_TRAINER_CARD's: four palettes (PalPacket_TrainerCard) and the
 * badges' blocks (BlkPacket_TrainerCard, read from the cart: Red / Blue's RAINBOWBADGE blocks are a row taller than
 * Yellow's), a block only for a badge the player has.
 *
 * Nothing of the game is bundled: [extract] cuts the card's tiles, palettes and words out of the player's cart (by
 * [Tables], read from DrawTrainerInfo's own code: scripts/find_gen1_card.py) into rom-art ([path]), where [load] reads them.
 */
object Gen1TrainerCard {
    /** The card's pieces in rom-art: one file per [CardStyle], written by [Gen1Art.extractTo] for the cart that has a [Tables]. */
    fun path(style: CardStyle) = "gen1/${style.name.lowercase()}/trainer_card.bin"

    /** Red's, Blue's and Yellow's styles: [Gen1TrainerCard]'s, not [TrainerCardArt]'s. */
    val STYLES = setOf(CardStyle.RED_BLUE, CardStyle.RED_BLUE_DE, CardStyle.YELLOW, CardStyle.YELLOW_DE)

    /** Where a cart keeps what its card draws (bank, address): DrawTrainerInfo's immediates (find_gen1_card.py). */
    class Tables(
        val pic: Pair<Int, Int>,           // RedPicFront: a compressed front pic (Gen1Pic)
        val textBox: Pair<Int, Int>,       // TrainerInfoTextBoxTileGraphics: 8 tiles at $77 (the boxes), its 9th at $D7
        val names: Pair<Int, Int>,         // BlankLeaderNames: $17 tiles at $60 (the names' blank tiles, then the circle, $76)
        val numbers: Pair<Int, Int>,       // BadgeNumbersTileGraphics: 8 tiles at $D8
        val faces: Pair<Int, Int>,         // GymLeaderFaceAndBadgeTileGraphics: 64 tiles at $20 (a face's 4, then its badge's)
        val boxTiles: Pair<Int, Int>,      // TextBoxGraphics: its 14th tile (the colon) at $D6
        val nameMoneyTime: Pair<Int, Int>, // TrainerInfo_NameMoneyTimeText (at 2, 2)
        val badgesText: Pair<Int, Int>,    // TrainerInfo_BadgesText (at 6, 9)
        val palettes: Pair<Int, Int>,      // SuperPalettes (Red / Blue: a Super Game Boy cart) or CGBBasePalettes (Yellow)
        val blocks: Pair<Int, Int>,        // BlkPacket_TrainerCard: the badges' colour blocks
        val font: Pair<Int, Int>,          // FontGraphics: 1bpp, tiles $80 on
        val style: CardStyle,
    )

    val YELLOW_TABLES = Tables(
        pic = 0x04 to 0x5A97, textBox = 0x3D to 0x5C24, names = 0x3D to 0x5CB4, numbers = 0x3D to 0x5E24,
        faces = 0x03 to 0x691B, boxTiles = 0x04 to 0x4E18, nameMoneyTime = 0x04 to 0x6136, badgesText = 0x04 to 0x6149,
        palettes = 0x1C to 0x6AF9, blocks = 0x1C to 0x66F1, font = 0x04 to 0x4600, style = CardStyle.YELLOW,
    )

    /** German Yellow: English's code, its data a few bytes apart. */
    val YELLOW_DE_TABLES = Tables(
        pic = 0x04 to 0x5A9A, textBox = 0x3D to 0x5C1D, names = 0x3D to 0x5CAD, numbers = 0x3D to 0x5E1D,
        faces = 0x03 to 0x6910, boxTiles = 0x04 to 0x4E28, nameMoneyTime = 0x04 to 0x6139, badgesText = 0x04 to 0x614B,
        palettes = 0x1C to 0x6AD8, blocks = 0x1C to 0x66D0, font = 0x04 to 0x4600, style = CardStyle.YELLOW_DE,
    )

    /** Red and Blue (pokered.sym: the two builds keep the card at the same places). */
    val RED_BLUE_TABLES = Tables(
        pic = 0x04 to 0x6EDE, textBox = 0x0B to 0x7B98, names = 0x0B to 0x7C28, numbers = 0x0B to 0x7D98,
        faces = 0x03 to 0x6A9E, boxTiles = 0x04 to 0x6288, nameMoneyTime = 0x04 to 0x7584, badgesText = 0x04 to 0x7597,
        palettes = 0x1C to 0x6660, blocks = 0x1C to 0x6360, font = 0x04 to 0x5A80, style = CardStyle.RED_BLUE,
    )

    /** German Red and Blue (Rote / Blaue Edition: the same places in both). */
    val RED_BLUE_DE_TABLES = Tables(
        pic = 0x04 to 0x6EE1, textBox = 0x0B to 0x7B98, names = 0x0B to 0x7C28, numbers = 0x0B to 0x7D98,
        faces = 0x03 to 0x6A97, boxTiles = 0x04 to 0x6298, nameMoneyTime = 0x04 to 0x7587, badgesText = 0x04 to 0x7599,
        palettes = 0x1C to 0x663A, blocks = 0x1C to 0x633A, font = 0x04 to 0x5A80, style = CardStyle.RED_BLUE_DE,
    )

    // PalPacket_TrainerCard: PAL_MEWMON, PAL_BADGE, PAL_REDMON, PAL_YELLOWMON.
    private val PALETTE_IDS = intArrayOf(0x10, 0x22, 0x12, 0x18)

    /**
     * BadgeBlkDataLengths: how many of BlkPacket_TrainerCard's blocks each badge has (the RAINBOWBADGE three, for its
     * colours). SetPal_TrainerCard zeroes the blocks of a badge the player doesn't have, so its leader keeps palette 0.
     */
    private val BLOCKS_PER_BADGE = intArrayOf(1, 1, 1, 3, 1, 1, 1, 1)
    private const val BLOCK = 6

    private const val MAGIC = 0x50444331 // "PDC1"
    private const val TILE = 16

    /** Wiped by a new [extract] layout: a file of another layout is read as none (then rebuilt with the art). */
    private const val VERSION = 1

    private fun off(t: Pair<Int, Int>) = if (t.first == 0) t.second else t.first * 0x4000 + (t.second - 0x4000)

    /** The card's pieces from [rom] (a cart [t] fits), as [path] holds them; null if its picture doesn't decode. */
    fun extract(rom: ByteArray, t: Tables): ByteArray? {
        val pic = Gen1Pic.decompress(rom, off(t.pic)) ?: return null
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
        put(cut(t.font, 128 * 8))
        put(cut(t.textBox, 9 * TILE))
        put(cut(t.names, 0x17 * TILE))
        put(cut(t.numbers, 8 * TILE))
        put(cut(t.faces, 64 * TILE))
        put(cut(off(t.boxTiles).let { 0 to it + 13 * TILE }, TILE))
        for (id in PALETTE_IDS) put(cut(off(t.palettes).let { 0 to it + 8 * id }, 8))
        // ATTR_BLK: a header byte and the count, then 6 bytes a block (control, palettes, x1, y1, x2, y2).
        put(cut(off(t.blocks).let { 0 to it + 2 }, BLOCKS_PER_BADGE.sum() * BLOCK))
        // LoadUncompressedSpriteData: the picture bottom-aligned and centred in a 7x7-tile box, one byte a pixel.
        out.writeByte(pic.widthTiles)
        out.writeByte(pic.heightTiles)
        put(ByteArray(pic.pixels.size) { pic.pixels[it].toByte() })
        for (s in listOf(text(t.nameMoneyTime), text(t.badgesText))) {
            out.writeByte(s.size)
            put(s)
        }
        return out.readByteArray()
    }

    /** The card's pieces, read back from [path]. */
    class Art internal constructor(
        val font: ByteArray, val textBox: ByteArray, val names: ByteArray, val numbers: ByteArray, val faces: ByteArray,
        val colon: ByteArray, val pals: Array<IntArray>, val blocks: ByteArray, val pic: IntArray,
        val nameMoneyTime: ByteArray, val badgesText: ByteArray, override val style: CardStyle,
    ) : CardArt

    fun parse(b: ByteArray, style: CardStyle): Art? = runCatching {
        if (b.size < 5 || ((b[0].u shl 24) or (b[1].u shl 16) or (b[2].u shl 8) or b[3].u) != MAGIC || b[4].u != VERSION) return null
        var o = 5
        fun take(n: Int) = b.copyOfRange(o, o + n).also { o += n }
        val font = take(128 * 8)
        val textBox = take(9 * TILE)
        val names = take(0x17 * TILE)
        val numbers = take(8 * TILE)
        val faces = take(64 * TILE)
        val colon = take(TILE)
        val pals = Array(4) { take(8).let { p -> IntArray(4) { rgb(p[2 * it].u or (p[2 * it + 1].u shl 8)) } } }
        val blocks = take(BLOCKS_PER_BADGE.sum() * BLOCK)
        val w = take(1)[0].u
        val h = take(1)[0].u
        val raw = take(w * 8 * h * 8)
        val pic = IntArray(56 * 56)
        val ox = ((8 - w) / 2) * 8
        val oy = (7 - h) * 8
        for (y in 0 until h * 8) for (x in 0 until w * 8) {
            val px = ox + x
            val py = oy + y
            if (px in 0 until 56 && py in 0 until 56) pic[py * 56 + px] = raw[y * w * 8 + x].u
        }
        fun str() = take(take(1)[0].u)
        Art(font, textBox, names, numbers, faces, colon, pals, blocks, pic, str(), str(), style)
    }.getOrNull()

    private val loaded = HashMap<CardStyle, Art>()
    private val loadedLock = kotlinx.atomicfu.locks.SynchronizedObject()

    /** [style]'s card art [Gen1Art] cut into rom-art [dir], or null before it has. */
    fun load(dir: Path, style: CardStyle): Art? {
        val f = dir / path(style)
        if (!f.isFile) return null
        return parse(f.readBytes(), style)?.also { kotlinx.atomicfu.locks.synchronized(loadedLock) { loaded[style] = it } }
    }

    /** The last [load]ed card of [style], so a screen can start drawing it without waiting. */
    fun cached(style: CardStyle): Art? = kotlinx.atomicfu.locks.synchronized(loadedLock) { loaded[style] }

    // ---- The save's side. ----

    /** The card's style for [cfg], or null for a game without a mapped card. */
    fun styleFor(cfg: Gen1Config): CardStyle? = when {
        cfg === GEN1_YELLOW -> CardStyle.YELLOW
        cfg === GEN1_YELLOW_DE -> CardStyle.YELLOW_DE
        cfg === GEN1_RED || cfg === GEN1_BLUE -> CardStyle.RED_BLUE
        cfg === GEN1_RED_DE || cfg === GEN1_BLUE_DE -> CardStyle.RED_BLUE_DE
        else -> null
    }

    /** wPlayerName sits this far before wPlayerMoney in every cart here (Yellow D157 / D346, Red / Blue D158 / D347, the German ones +5). */
    private const val NAME_BEFORE_MONEY = 0x1EF
    private const val NAME_LEN = 11

    /** What [cfg]'s card shows, or null before a save is up (no player name yet) or for a game without a card. */
    fun read(r: MemoryReader, cfg: Gen1Config): TrainerCardInfo? = runCatching {
        val style = styleFor(cfg) ?: return null
        val raw = r.readCoreMemory(cfg.money - NAME_BEFORE_MONEY, NAME_LEN)
        val name = (0 until NAME_LEN).map { raw[it].u }.takeWhile { it != EOS }
        // The title screen / a new game before naming: no name, or WRAM that isn't one.
        if (name.isEmpty() || name.size > 10 || name.any { it < 0x7F }) return null
        val t = r.readCoreMemory(cfg.playTime, 3)
        val m = r.readCoreMemory(cfg.money, 3)
        TrainerCardInfo(
            style = style,
            name = name,
            female = false,
            trainerId = 0,
            hours = t[0].u,
            minutes = t[2].u,
            // BCD as the card prints it: a digit a nibble.
            money = m.fold(0L) { acc, b -> acc * 100 + (b.u shr 4) * 10 + (b.u and 0xF) },
            dexCaught = null,
            badges = r.readCoreMemory(cfg.badges, 1)[0].u,
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
    private const val SPACE = 0x7F
    private const val ZERO = 0xF6
    private const val YEN = 0xF0
    private const val COLON = 0xD6
    private const val LINE = 0xD7

    /** The card's screen, 160x144, as the game shows it. */
    fun render(art: Art, card: TrainerCardInfo): RomArt.Image {
        val map = IntArray(W * H) { SPACE }
        fun put(x: Int, y: Int, t: Int) { if (x in 0 until W && y in 0 until H) map[y * W + x] = t }
        fun string(x0: Int, y0: Int, s: List<Int>) {
            var x = x0
            var line = y0
            for (c in s) when (c) {
                EOS -> return
                NEXT -> { line += 2; x = x0 }
                else -> put(x++, line, c)
            }
        }
        // TrainerInfo_DrawTextBox: height 6, its rows [next] apart.
        fun box(x0: Int, y0: Int, width: Int) {
            put(x0, y0, 0x79); for (x in 1..width) put(x0 + x, y0, 0x7A); put(x0 + width + 1, y0, 0x7B)
            for (y in 1..6) { put(x0, y0 + y, 0x7C); put(x0 + width + 1, y0 + y, 0x78) }
            put(x0, y0 + 7, 0x7D); for (x in 1..width) put(x0 + x, y0 + 7, 0x77); put(x0 + width + 1, y0 + 7, 0x7E)
        }

        // The picture (DisplayPicCenteredOrUpperRight at 15, 1, then the tiles copied down by 7): columns 1-4 of its box.
        for (x in 0 until 4) for (y in 0 until 7) put(15 + x, 1 + y, 0x100 + (x + 1) * 7 + y)
        box(0, 0, 18)
        box(1, 10, 16)
        for (y in 10 until 18) { put(0, y, LINE); put(19, y, LINE) }
        string(6, 9, art.badgesText.map { it.u })
        string(2, 2, art.nameMoneyTime.map { it.u })
        string(7, 2, card.name)
        // PrintBCDNumber: ¥ right before the first digit that isn't 0, left-aligned (all 0: ¥0).
        val money = card.money.coerceIn(0, 999_999).toString()
        put(8, 4, YEN)
        money.forEachIndexed { i, ch -> put(9 + i, 4, ZERO + (ch - '0')) }
        // PrintNumber: the hours left-aligned (a byte), the colon right after them, the minutes with a leading zero.
        val hours = card.hours.coerceIn(0, 255).toString()
        hours.forEachIndexed { i, ch -> put(9 + i, 6, ZERO + (ch - '0')) }
        put(9 + hours.length, 6, COLON)
        (card.minutes % 100).toString().padStart(2, '0').forEachIndexed { i, ch -> put(10 + hours.length + i, 6, ZERO + (ch - '0')) }
        // DrawBadges: each leader's number, then the face or (beaten) its badge; the blank name tiles over a face.
        var nameTile = 0x60
        for (i in 0 until 8) {
            val x = 2 + 4 * (i % 4)
            val y = 11 + 3 * (i / 4)
            val owned = card.badges shr i and 1 != 0
            put(x, y, 0xD8 + i)
            if (!owned) { put(x + 1, y, nameTile); put(x + 2, y, nameTile + 1) }
            nameTile += 2
            val face = 0x20 + 8 * i + if (owned) 4 else 0
            put(x + 1, y + 1, face); put(x + 2, y + 1, face + 1)
            put(x + 1, y + 2, face + 2); put(x + 2, y + 2, face + 3)
        }

        // The SGB's ATTR_BLK, which Yellow's Color code follows too: a block's border cells (control bit 1) take its border
        // palette, the cells inside it (bit 0) its inside one.
        val attr = IntArray(W * H)
        var block = 0
        for (i in 0 until 8) for (n in 0 until BLOCKS_PER_BADGE[i]) {
            val o = BLOCK * block++
            if (card.badges shr i and 1 == 0) continue
            val ctrl = art.blocks[o].u
            val inside = art.blocks[o + 1].u and 3
            val border = art.blocks[o + 1].u shr 2 and 3
            val x1 = art.blocks[o + 2].u; val y1 = art.blocks[o + 3].u
            val x2 = art.blocks[o + 4].u; val y2 = art.blocks[o + 5].u
            for (y in y1..y2) for (x in x1..x2) {
                if (x !in 0 until W || y !in 0 until H) continue
                val edge = x == x1 || x == x2 || y == y1 || y == y2
                if (edge && ctrl and 2 != 0) attr[y * W + x] = border
                else if (!edge && ctrl and 1 != 0) attr[y * W + x] = inside
            }
        }

        val img = RomArt.Image(WIDTH, HEIGHT)
        for (ty in 0 until H) for (tx in 0 until W) {
            val t = map[ty * W + tx]
            val pal = art.pals[attr[ty * W + tx]]
            for (py in 0 until 8) for (px in 0 until 8) {
                val c = when (t) {
                    in 0x100..0x130 -> {
                        val n = t - 0x100
                        art.pic[((n % 7) * 8 + py) * 56 + (n / 7) * 8 + px]
                    }
                    in 0x20..0x5F -> px2(art.faces, (t - 0x20) * TILE, px, py)
                    in 0x60..0x76 -> px2(art.names, (t - 0x60) * TILE, px, py)
                    in 0x77..0x7E -> px2(art.textBox, (t - 0x77) * TILE, px, py)
                    COLON -> px2(art.colon, 0, px, py)
                    LINE -> px2(art.textBox, 8 * TILE, px, py)
                    in 0xD8..0xDF -> px2(art.numbers, (t - 0xD8) * TILE, px, py)
                    in 0x80..0xFF -> if (art.font[(t - 0x80) * 8 + py].u shr (7 - px) and 1 != 0) 3 else 0
                    else -> 0
                }
                img.argb[(ty * 8 + py) * WIDTH + tx * 8 + px] = pal[c]
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

    /** RGB555 as ARGB, the way mGBA shows it (the Super Game Boy's and the Color's alike). */
    private fun rgb(c: Int): Int {
        fun ch(v: Int) = ((v and 31) shl 3) or ((v and 31) shr 2)
        return (0xFF shl 24) or (ch(c) shl 16) or (ch(c shr 5) shl 8) or ch(c shr 10)
    }
}
