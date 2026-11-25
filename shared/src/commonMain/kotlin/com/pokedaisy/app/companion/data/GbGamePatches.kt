package com.pokedaisy.app.companion.data

/**
 * GAMEPLAY's ROM options on the Game Boy games (2026-11-18): INSTANT TEXT, REUSABLE TMS, FIELD POISON, EXP SHARE and LOW HP BEEP, each one small edit of
 * the cart's own code, found by its shape (pret's pokered / pokeyellow / pokegold / pokecrystal), every one unique in the cart
 * or none. Patched like the GBA's (`pk_cheats_patch`): only where the cart holds the shape, the cart's bytes kept for the
 * Poller's hash; addresses are [TAG] + the cart offset (bank * 0x4000 + the address in its bank).
 *
 * - INSTANT TEXT: PrintLetterDelay (its two flag checks first, `ld a, [nn]; bit n, a; ret nz` twice) returns at once: a `ret`
 *   on its first byte. Every letter goes down without a frame's wait; the A / B press still closes a page.
 * - REUSABLE TMS, Gen 1: ItemUseTMHM's end, `ld a, [wCurItem]; call IsItemHM; ret c; jp RemoveUsedItem`, then BootedUpTMText:
 *   the `ret c` made a `ret` (Japanese Green: its BootedUpTMText inline, not a `text_far`). Gen 2: TeachTMHM's `ld a, [wCurItem]; call IsHM; ret c; ld c, ..; callfar ChangeHappiness;
 *   call ConsumeTM; jr`: the `call ConsumeTM` three `nop`s (the happiness the move brings stays).
 * - FIELD POISON off: Gen 1's ApplyOutOfBattlePoisonDamage skips its damage (the day care's EXP and Pikachu's mood still run),
 *   Gen 2's DoPoisonStep takes its "no party" way out.
 * - EXP SHARE: Gen 1's DivideExpDataByNumMonsGainingExp counts wPartyGainExpFlags' bits, Gen 2's GiveExperiencePoints
 *   .EvenlyDivideExpAmongParticipants wBattleParticipantsNotFainted's (`ld a, [nn]; ld b, a; ... srl b; adc d; ... cp 2; ret c`),
 *   and divides the win by them; each Pokémon with its bit set (and HP) gets its share. The byte's address comes from that load
 *   ([Found.exp]: SPLIT sets every bit each frame, like Gen 3's gSentPokesToOpponent); FULL makes the `cp 2` a `ret`, no division.
 *   MODERN ([expModern], [expModernGen1]): FULL's `ret`, and the ones that didn't fight get half, quietly, with one line for them all.
 * - LOW HP BEEP off: the HUD's low-health check sets the alarm's bit (`ld hl, wLowHealthAlarm; set 7, [hl]`), Gen 1's
 *   DrawPlayerHUDAndHPBar.setLowHealthAlarm, Gen 2's CheckDanger.danger: the `set` two `nop`s. The music then never hears it.
 * - RUNNING SHOES: holding B while walking moves at the bike's speed ([run]). Red / Blue: .moveAhead2's bike check
 *   (`ld a, [wWalkBikeSurfState]; dec a; jr nz, .normalPlayerSpriteAdvancement`, before the ledge check and
 *   `call DoBikeSpeedup`) jumps to a trampoline; Yellow: DoBikeSpeedup's own first check (`...; dec a; ret nz`); Gold /
 *   Silver / Crystal: DoPlayerMovement's .BikeCheck (`ld a, [wPlayerState]; cp PLAYER_BIKE; ret z; cp PLAYER_SKATE; ret`).
 *   Each trampoline keeps the game's own answer for the bike (and Gen 2's skates), says "walking" for any other state
 *   (surfing above all), and says "bike" for walking only while the joypad byte (Gen 1's hJoyHeld from DoBikeSpeedup's
 *   Cycling Road check, Gen 2's hJoyDown from .GetDPad) has B. It goes at the very end of the hook's bank (bank 0 for
 *   Gen 1: its caller is home code), in the cart's padding, which its write checks is still there.
 *
 * Found in Red, Blue, Yellow (every language), Gold, Silver, Crystal (rev 0, rev 1, Australia) and the hacks on that code
 * (Brown, Yellow Legacy, Crystal Clear, Crystal Legacy), and in Japanese Green's English translation (no RUNNING SHOES or EXP SHARE
 * MODERN there: their shapes aren't in it); PureRed / PureBlue's text check is three places, so INSTANT TEXT
 * isn't offered there. Played headless (mgba_dump `rompatch`).
 */
object GbGamePatches {
    const val TAG = 0x10000000L

    class Found(val tms: List<ReusableTms.Site>, val text: InstantText.Patch?, val beep: InstantText.Patch?, val poison: InstantText.Patch? = null,
                val exp: ExpShare.Found? = null, val run: InstantText.Patch? = null)

    private const val ANY = -1
    private val TEXT = sig("fa????cb??c0fa????cb??c8")
    private val TM1 = sig("fa????cd????d8c3????17??????50")
    // Japanese Green (pret/pokegreen): the same end, BootedUpTMText's text inline after it (`text` = 00), not a `text_far`.
    private val TM1_INLINE = sig("fa????cd????d8c3????00")
    private val TM2 = sig("fa????cd????d80e??21????3e??cfcd????18")
    private val BEEP1 = sig("21????cb7e3600c8afea????c921????cbfec9")
    private val BEEP2 = sig("21????cbbe18??21????cbfec9")
    private val POISON1 = sig("fa????e603c2????ea????21????")
    private val POISON2 = sig("fa????a728??af0e??21????")
    private val EXP1 = sig("fa????47af0e081600afcb388a570d20f8fe02d8")
    private val EXP2 = sig("fa????470e061600afcb388a570d20f8fe02d8")
    // MODERN (Gen 2's GiveExperiencePoints, pokegold / pokecrystal): `ld a, [wCurPartyMon]; ld hl, wPartyMonNicknames;
    // call GetNickname; ld hl, Text_MonGainedExpPoint; call BattleTextbox; ld a, [wStringBuffer2 + 1]; ldh [hQuotient + 3], a;
    // ld a, [wStringBuffer2]; ldh [hQuotient + 2], a; pop bc; call AnimateExpBar`.
    private val EXP_TEXT = sig("fa????21????cd????21????cd????fa????e0??fa????e0??c1cd????")
    private const val EXP_TEXT_HOOK = 9
    // .next_mon's end: `... ld a, MON_SPECIES; call GetPartyParamLocation; ld b, h; ld c, l; jp .loop`, then .done: `jp ResetBattleParticipants`.
    private val EXP_DONE = sig("fa????47fa????3cb828??ea????3e00cd????444dc3????c3????")
    // ResetBattleParticipants: `xor a; ld [wBattleParticipantsNotFainted], a; ld [wBattleParticipantsIncludingFainted], a; ld a, [wCurBattleMon]; ld c, a; ld hl, ...`.
    private val EXP_RESET = sig("afea????ea????fa????4f21")
    // The held Exp. Share's pass: `ld a, 1; ld [wGivingExperienceToExpShareHolders], a; call GiveExperiencePoints; pop af;
    // ld [wBattleParticipantsNotFainted], a; ret`.
    private val EXP_HOLD = sig("3e01ea????cd????f1ea????c9")
    // GiveExperiencePoints' loop: `push bc; ld hl, wBattleParticipantsNotFainted; ld a, [wCurPartyMon]; ld c, a; ld b, CHECK_FLAG;
    // ld d, 0; predef SmallFarFlagAction; ld a, c; and a; pop bc; jp z, .next_mon`. MODERN calls its own check over the 18 bytes
    // from the `ld hl`.
    private val EXP_CHECK = sig("c521????fa????4f060216003e??cd????79a7c1ca????")
    private const val EXP_CHECK_LEN = 18
    // Its start after the link checks: `call .EvenlyDivideExpAmongParticipants; xor a; ld [wCurPartyMon], a`.
    private val EXP_START = sig("cd????afea????")
    // MODERN on Gen 1 (pokered / pokeyellow's GainExperience). The loop's flag check: `push hl; ld hl, wPartyGainExpFlags;
    // ld a, [wWhichPokemon]; ld c, a; ld b, FLAG_TEST; predef FlagActionPredef; ld a, c; and a; pop hl; jp z, .nextMon`.
    private val EXP1_CHECK = sig("e521????fa????4f06023e??cd????79a7e1ca????")
    private const val EXP1_CHECK_LEN = 16
    // Its start: `call DivideExpDataByNumMonsGainingExp; ld hl, wPartyMon1; xor a; ld [wWhichPokemon], a`.
    private val EXP1_START = sig("cd????21????afea????")
    // The trainer battle's boost: `ld a, [wIsInBattle]; dec a; call nz, BoostExp; inc hl` x3, then the EXP added from hQuotient.
    private val EXP1_BOOST = sig("fa????3dc4????23232346f0??ea????803246f0??")
    // The gained box: `ld a, [wWhichPokemon]; ld hl, wPartyMonNicks; call GetPartyMonName; ld hl, GainedText; call PrintText;
    // xor a; ld [wMonDataLocation], a`.
    private val EXP1_PRINT = sig("fa????21????cd????21????cd????afea????")
    // GainedText: `text_far _GainedText; text_asm; ld a, [wBoostExpByExpAll]; ld hl, WithExpAllText; and a; ret nz; ...`.
    private val EXP1_GAINED = sig("17??????08fa????21????a7c021????fa????a7c821????c9")
    // .done: `ld hl, wPartyGainExpFlags; xor a; ld [hl], a; ld a, [wPlayerMonNumber]; ld c, a; ld b, FLAG_SET; push bc; predef;
    // ld hl, wPartyFoughtCurrentEnemyFlags; xor a; ld [hl], a; pop bc; predef_jump`.
    private val EXP1_DONE = sig("21????af77fa????4f0601c53e??cd????21????af77c13e??c3????")
    // RUNNING SHOES. Red / Blue's .moveAhead2 check, and DoBikeSpeedup for hJoyHeld (`cp ROUTE_17; jr nz; ldh a, [hJoyHeld]`).
    private val RUN_RED = sig("fa????3d200afa????cb??2003cd????")
    private val RUN_RED_SPEEDUP = sig("fa????a7c0fa????fe1c2005f0??e670c0c3????")
    // Yellow's DoBikeSpeedup, its own check first and hJoyHeld in its Cycling Road check.
    private val RUN_YELLOW = sig("fa????3dc0fa????cb??c0fa????a7c0fa????fe1c2005f0??e670c0cd????c9")
    // Gen 2's .BikeCheck and .GetDPad (`ldh a, [hJoyDown]; ld [wCurInput], a`, then the downhill bike's own code).
    private val RUN_GS = sig("fa????fe01c8fe02c9")
    private val RUN_GS_DPAD = sig("f0??ea????21????cb??c84fe6f0c079f680ea????c9")

    /** What [rom] (a whole Game Boy cart) has; null for anything that isn't one. */
    fun find(rom: ByteArray): Found? {
        if (rom.size < 0x8000 || rom.size > 0x800000) return null
        fun one(s: IntArray): Int? = hits(rom, s).singleOrNull()
        // Each write checks the cart's bytes from its own address: the whole shape for the text, the `set 7, [hl]; ret` for the beep.
        val text = one(TEXT)?.let { at ->
            InstantText.Patch(listOf(InstantText.Write(TAG + at, rom.copyOfRange(at, at + TEXT.size), byteArrayOf(0xC9.toByte()))))
        }
        val tms = (one(TM1)?.let { at -> Triple(at, TM1, 6 to byteArrayOf(0xC9.toByte())) }
            ?: one(TM1_INLINE)?.let { at -> Triple(at, TM1_INLINE, 6 to byteArrayOf(0xC9.toByte())) }
            ?: one(TM2)?.let { at -> Triple(at, TM2, 15 to byteArrayOf(0, 0, 0)) })
            ?.let { (at, s, p) -> listOf(ReusableTms.Site(TAG + at, rom.copyOfRange(at, at + s.size), TAG + at + p.first, p.second)) }
            .orEmpty()
        val beep = (one(BEEP1)?.let { it + 16 } ?: one(BEEP2)?.let { it + 10 })?.let { at ->
            InstantText.Patch(listOf(InstantText.Write(TAG + at, rom.copyOfRange(at, at + 3), byteArrayOf(0, 0))))
        }
        // FIELD POISON off: Gen 1's `jp nz, .skipPoisonEffectAndSound` (every fourth step) made a `jp` - the day care's EXP and
        // Pikachu's mood before it still run; Gen 2's DoPoisonStep's `jr z, .no_faint` (no party) made a `jr`.
        val poison = one(POISON1)?.let { at -> Triple(at, POISON1, 5 to 0xC3) } ?: one(POISON2)?.let { at -> Triple(at, POISON2, 4 to 0x18) }
        val poisonPatch = poison?.let { (at, s, p) ->
            InstantText.Patch(listOf(InstantText.Write(TAG + at + p.first, rom.copyOfRange(at + p.first, at + s.size), byteArrayOf(p.second.toByte()))))
        }
        // EXP SHARE: the participants byte's load and the `cp 2; ret c` that skips the division for one Pokémon.
        val exp = (one(EXP1)?.let { it to EXP1 } ?: one(EXP2)?.let { it to EXP2 })?.let { (at, s) ->
            val flags = (rom[at + 1].toInt() and 0xFF) or ((rom[at + 2].toInt() and 0xFF) shl 8)
            val cp = at + s.size - 3
            val full = listOf(InstantText.Write(TAG + cp, rom.copyOfRange(cp, cp + 2), byteArrayOf(0xC9.toByte(), 0)))
            val modern = (if (s === EXP2) expModern(rom, at, flags) else expModernGen1(rom, at, flags))?.let { full + it }
            // C000-CFFF is plain WRAM; D000-DFFF read in WRAM bank 1 (a Color cart may page it), as the readers do.
            val addr = if (flags >= 0xD000) 0x10000L or flags.toLong() else flags.toLong()
            // Gen 1 (EXP1: DivideExpDataByNumMonsGainingExp) keeps wIsInBattle right before wPartyGainExpFlags in every
            // build of pokered's WRAM (GbExpShareGateTest checks it against each config); Gen 2's battle byte isn't next to
            // its participants', so the host hands it to [ExpShare.mark] from the game's config.
            val battle = if (s === EXP1) addr - 1 else 0L
            if (flags !in 0xC000..0xDFFF) null
            else ExpShare.Found(addr, full, width = 1, battle = battle, modern = modern)
        }
        return Found(tms, text, beep, poisonPatch, exp, runPatch(rom, ::one))
    }

    /**
     * RUNNING SHOES ([Found.run]): the hook and its trampoline, or null. The trampoline sits at the end of the hook's bank,
     * in a run of the cart's padding (one byte, 00 or FF, to the bank's end) longer than itself: its write expects that
     * padding, so a cart that uses the space is refused there.
     */
    private fun runPatch(rom: ByteArray, one: (IntArray) -> Int?): InstantText.Patch? {
        fun u8(i: Int) = rom[i].toInt() and 0xFF
        fun place(bank: Int, size: Int): Int? {
            val end = (bank + 1) * 0x4000
            if (end > rom.size) return null
            val fill = rom[end - 1]
            if (u8(end - 1) != 0 && u8(end - 1) != 0xFF) return null
            var start = end
            while (start > bank * 0x4000 && rom[start - 1] == fill) start--
            return if (end - start > size) end - size else null
        }
        // A cart offset's CPU address while its bank is mapped (bank 0 at 0000-3FFF, the others at 4000-7FFF).
        fun cpu(off: Int) = if (off < 0x4000) off else 0x4000 + off % 0x4000
        fun jp(to: Int) = byteArrayOf(0xC3.toByte(), (to and 0xFF).toByte(), (to shr 8).toByte())
        fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
        // A Game Boy cart's patch is written 4 bytes at most at a time (pk_cheats_patch), each piece checking its own bytes:
        // the trampoline first, then the jump to it, so the jump never lands on a half-written trampoline.
        fun pieces(off: Int, value: ByteArray) = (value.indices step 4).map { i ->
            val n = minOf(4, value.size - i)
            InstantText.Write(TAG + off + i, rom.copyOfRange(off + i, off + i + n), value.copyOfRange(i, i + n))
        }
        fun patch(hook: Int, hookSize: Int, trampoline: ByteArray, at: Int) = InstantText.Patch(
            pieces(at, trampoline) + pieces(hook, jp(cpu(at)) + ByteArray(hookSize - 3)))
        one(RUN_RED)?.let { hook ->
            if (hook >= 0x4000) return null
            val joy = one(RUN_RED_SPEEDUP)?.let { u8(it + 13) } ?: return null
            val at = place(0, 21) ?: return null
            // ld a, [wWalkBikeSurfState]; dec a; jr z, .bike; inc a; jr nz, .normal; ldh a, [hJoyHeld]; and B; jr z, .normal
            // .bike: jp the ledge check; .normal: jp .normalPlayerSpriteAdvancement
            val t = b(0xFA, u8(hook + 1), u8(hook + 2), 0x3D, 0x28, 9, 0x3C, 0x20, 9, 0xF0, joy, 0xE6, 0x02, 0x28, 3) +
                jp(cpu(hook + 6)) + jp(cpu(hook + 16))
            return patch(hook, 6, t, at)
        }
        one(RUN_YELLOW)?.let { hook ->
            if (hook >= 0x4000) return null
            val at = place(0, 16) ?: return null
            // ld a, [wWalkBikeSurfState]; dec a; jr z, .bike; inc a; ret nz; ldh a, [hJoyHeld]; and B; ret z
            // .bike: jp DoBikeSpeedup's ledge check
            val t = b(0xFA, u8(hook + 1), u8(hook + 2), 0x3D, 0x28, 7, 0x3C, 0xC0, 0xF0, u8(hook + 24), 0xE6, 0x02, 0xC8) +
                jp(cpu(hook + 5))
            return patch(hook, 5, t, at)
        }
        one(RUN_GS)?.let { hook ->
            val bank = hook / 0x4000
            val joy = hits(rom, RUN_GS_DPAD).singleOrNull { it / 0x4000 == bank }?.let { u8(it + 1) } ?: return null
            // Gen 2 carts keep 64 bytes or more of padding there; a hack with less is left alone (Prism: 27).
            val at = place(bank, 18)?.takeIf { (bank + 1) * 0x4000 - it >= 18 && run(rom, bank) >= 64 } ?: return null
            // ld a, [wPlayerState]; cp BIKE; ret z; cp SKATE; ret z; and a; ret nz; ldh a, [hJoyDown]; and B; xor B; ret
            val t = b(0xFA, u8(hook + 1), u8(hook + 2), 0xFE, 0x01, 0xC8, 0xFE, 0x02, 0xC8, 0xA7, 0xC0, 0xF0, joy, 0xE6, 0x02, 0xEE, 0x02, 0xC9)
            return patch(hook, 3, t, at)
        }
        return null
    }

    /** The padding run at the end of [bank] (one byte, 00 or FF): its length. */
    private fun run(rom: ByteArray, bank: Int): Int {
        val end = (bank + 1) * 0x4000
        if (end > rom.size) return 0
        val fill = rom[end - 1]
        var start = end
        while (start > bank * 0x4000 && rom[start - 1] == fill) start--
        return end - start
    }

    /** A Game Boy cart file: its header's logo check (0x104) and size. */
    fun find(path: okio.Path): Found? = runCatching {
        val bytes = okio.FileSystem.SYSTEM.read(path) { readByteArray() }
        if (!isGameBoyRom(bytes)) null else find(bytes)
    }.getOrNull()

    private fun isGameBoyRom(b: ByteArray) = b.size >= 0x150 && (b[0x104].toInt() and 0xFF) == 0xCE && (b[0x105].toInt() and 0xFF) == 0xED

    private fun hits(rom: ByteArray, s: IntArray): List<Int> {
        val out = ArrayList<Int>()
        var i = 0
        while (i + s.size <= rom.size) {
            var ok = true
            for (k in s.indices) if (s[k] != ANY && (rom[i + k].toInt() and 0xFF) != s[k]) { ok = false; break }
            if (ok) out += i
            i++
        }
        return out
    }

    private fun sig(hex: String) = IntArray(hex.length / 2) { i ->
        val p = hex.substring(i * 2, i * 2 + 2)
        if (p == "??") ANY else p.toInt(16)
    }

    /**
     * EXP SHARE's MODERN on Gen 2 ([ExpShare.Found.modern], FULL's `ret` added by the caller): as Gen 6 pays a win. The ones
     * that fought are paid first, each with the game's box, level-ups and new moves; then one line for the rest; then the rest,
     * half each, with their level-ups and moves but no "gained N EXP. Points!" box. Who fought is
     * wBattleParticipantsIncludingFainted, which the game sets as each one goes out and EXP SHARE never touches (SPLIT's
     * every-frame mark is on wBattleParticipantsNotFainted, the byte the loop pays). The pass is kept in
     * wGivingExperienceToExpShareHolders, which only the battle's win code writes (0, then 1 for a held Exp. Share's pass) and
     * nothing reads: 2 = the rest's pass.
     *  - The loop's NotFainted check (`ld hl, wBattleParticipantsNotFainted; ... predef SmallFarFlagAction; ld a, c; and a`)
     *    calls T0: marked and, on the first pass, a fighter; on the rest's pass, not one; the holders' pass as the game.
     *  - The gained box's `ld hl, Text_MonGainedExpPoint; call BattleTextbox` calls T1: on the rest's pass wStringBuffer2's EXP
     *    is halved (the game reads it back right after) and no box shows, else the game's box. The EXP bar only moves for the
     *    one out (AnimateExpBar).
     *  - .done's `jp ResetBattleParticipants` goes by T2: after the first pass, with anyone marked who didn't fight, it shows
     *    the line and runs GiveExperiencePoints' loop again as the rest's pass; after that one it puts the byte back to 0.
     * All in GiveExperiencePoints' bank (Gen 2's battle bank), clear of the other trampolines, in the cart's padding; the
     * line in the cart's language (English, German; others get the English).
     */
    private fun expModern(rom: ByteArray, divide: Int, notFainted: Int): List<InstantText.Write>? {
        fun u8(i: Int) = rom[i].toInt() and 0xFF
        fun u16(i: Int) = u8(i) or (u8(i + 1) shl 8)
        fun cpu(off: Int) = if (off < 0x4000) off else 0x4000 + off % 0x4000
        fun lohi(v: Int) = listOf(v and 0xFF, v shr 8)
        val bank = divide / 0x4000
        fun inBank(s: IntArray) = hits(rom, s).filter { it / 0x4000 == bank }
        val text = inBank(EXP_TEXT).singleOrNull() ?: return null
        val done = inBank(EXP_DONE).singleOrNull() ?: return null
        val reset = inBank(EXP_RESET).singleOrNull { u16(it + 2) == notFainted } ?: return null
        val hold = inBank(EXP_HOLD).singleOrNull { u16(it + 10) == notFainted } ?: return null
        val curMon = u16(text + 1)
        val loopCheck = inBank(EXP_CHECK).singleOrNull { u16(it + 2) == notFainted && u16(it + 5) == curMon } ?: return null
        // GiveExperiencePoints' `call .EvenlyDivideExpAmongParticipants; xor a; ld [wCurPartyMon], a`: the rest's pass starts
        // there (FULL's `ret` makes the division a no-op).
        val restart = inBank(EXP_START).singleOrNull { u16(it + 1) == cpu(divide) && u16(it + 5) == curMon } ?: return null
        // The loop runs before .EvenlyDivideExpAmongParticipants, which follows .done.
        if (!(restart < loopCheck && loopCheck < text && text < done && done < divide && divide - restart < 0x400)) return null
        val gained = u16(text + EXP_TEXT_HOOK + 1)
        val textbox = u16(text + EXP_TEXT_HOOK + 4)
        val buffer2 = u16(text + 21)
        val including = u16(reset + 5)
        val pass = u16(hold + 3)
        val resetFn = u16(done + EXP_DONE.size - 2)
        if (u16(text + 16) != buffer2 + 1 || listOf(curMon, buffer2, including, pass).any { it !in 0xC000..0xDFFF }) return null
        if (cpu(reset) != resetFn) return null
        val line = when (u8(0x142).toChar()) { 'D' -> EXP_LINE_DE; else -> EXP_LINE_EN }
        val size = T0_SIZE + T1_SIZE + T2_SIZE + line.size
        val end = (bank + 1) * 0x4000
        // Clear of NO HMS NEEDED's and RUNNING SHOES' trampolines, should one be in this bank (they take its last ~100 bytes).
        val at = end - 160 - size
        if (end > rom.size || run(rom, bank) < size + 160) return null
        val t0 = at; val t1 = t0 + T0_SIZE; val t2 = t1 + T1_SIZE; val msg = t2 + T2_SIZE
        val t = ArrayList<Int>()
        fun rel(from: Int, to: Int) { t[from] = to - (from + 1) }
        // T0: ld a, [wCurPartyMon]; inc a; ld c, a; xor a; scf; .shift: rla; dec c; jr nz, .shift; ld b, a (the mon's bit)
        t += listOf(0xFA) + lohi(curMon) + listOf(0x3C, 0x4F, 0xAF, 0x37, 0x17, 0x0D, 0x20, 0xFC, 0x47)
        // ld a, [NotFainted]; and b; ret z; ld a, [pass]; and a; jr z, .first; dec a; jr z, .yes
        t += listOf(0xFA) + lohi(notFainted) + listOf(0xA0, 0xC8, 0xFA) + lohi(pass) + listOf(0xA7, 0x28, 0)
        val first = t.size - 1
        t += listOf(0x3D, 0x28, 0)
        val yes1 = t.size - 1
        // the rest's pass: ld a, [IncludingFainted]; and b; jr z, .yes; xor a; ret
        t += listOf(0xFA) + lohi(including) + listOf(0xA0, 0x28, 0)
        val yes2 = t.size - 1
        t += listOf(0xAF, 0xC9)
        // .first: ld a, [IncludingFainted]; and b; ret; .yes: ld a, 1; and a; ret
        rel(first, t.size)
        t += listOf(0xFA) + lohi(including) + listOf(0xA0, 0xC9)
        rel(yes1, t.size); rel(yes2, t.size)
        t += listOf(0x3E, 0x01, 0xA7, 0xC9)
        check(t.size == T0_SIZE)
        // T1: ld a, [pass]; cp 2; jr z, .half; ld hl, Text_MonGainedExpPoint; jp BattleTextbox
        t += listOf(0xFA) + lohi(pass) + listOf(0xFE, 0x02, 0x28, 0)
        val half = t.size - 1
        t += listOf(0x21) + lohi(gained) + listOf(0xC3) + lohi(textbox)
        // .half: ld hl, wStringBuffer2; srl [hl]; inc hl; rr [hl]; ret (the EXP, big-endian, halved; the game reads it back next)
        rel(half, t.size)
        t += listOf(0x21) + lohi(buffer2) + listOf(0xCB, 0x3E, 0x23, 0xCB, 0x1E, 0xC9)
        check(t.size == T0_SIZE + T1_SIZE)
        // T2: ld a, [pass]; and a; jr nz, .after; ld a, [IncludingFainted]; cpl; ld b, a; ld a, [NotFainted]; and b; jr z, .reset
        t += listOf(0xFA) + lohi(pass) + listOf(0xA7, 0x20, 0)
        val after = t.size - 1
        t += listOf(0xFA) + lohi(including) + listOf(0x2F, 0x47, 0xFA) + lohi(notFainted) + listOf(0xA0, 0x28, 0)
        val reset1 = t.size - 1
        // ld hl, the line; call BattleTextbox; ld a, 2; ld [pass], a; jp the loop again
        t += listOf(0x21) + lohi(cpu(msg)) + listOf(0xCD) + lohi(textbox) + listOf(0x3E, 0x02, 0xEA) + lohi(pass) + listOf(0xC3) + lohi(cpu(restart))
        // .after: cp 2; jr nz, .reset; xor a; ld [pass], a; .reset: jp ResetBattleParticipants
        rel(after, t.size)
        t += listOf(0xFE, 0x02, 0x20, 0)
        val reset2 = t.size - 1
        t += listOf(0xAF, 0xEA) + lohi(pass)
        rel(reset1, t.size); rel(reset2, t.size)
        t += listOf(0xC3) + lohi(resetFn)
        check(t.size == T0_SIZE + T1_SIZE + T2_SIZE)
        val all = ByteArray(t.size + line.size) { if (it < t.size) t[it].toByte() else line[it - t.size] }
        fun pieces(off: Int, value: ByteArray) = (value.indices step 4).map { i ->
            val n = minOf(4, value.size - i)
            InstantText.Write(TAG + off + i, rom.copyOfRange(off + i, off + i + n), value.copyOfRange(i, i + n))
        }
        fun call(to: Int, len: Int) = ByteArray(len).also { it[0] = 0xCD.toByte(); it[1] = (cpu(to) and 0xFF).toByte(); it[2] = (cpu(to) shr 8).toByte() }
        val hook0 = loopCheck + 1
        val hook1 = text + EXP_TEXT_HOOK
        val hook2 = done + EXP_DONE.size - 3
        // The trampolines and the line first, then the jumps to them.
        return pieces(at, all) + pieces(hook0, call(t0, EXP_CHECK_LEN)) + pieces(hook1, call(t1, 6)) +
            pieces(hook2, byteArrayOf(0xC3.toByte(), (cpu(t2) and 0xFF).toByte(), (cpu(t2) shr 8).toByte()))
    }

    /**
     * MODERN on Gen 1 ([Found.exp]'s other half, pokered / pokeyellow's GainExperience): FULL's `ret` (each one marked gets the
     * whole win), then the ones that fought get their boxes and level-ups as the game gives them, then one line for the rest,
     * then theirs. [gain] is wPartyGainExpFlags, which SPLIT marks every frame; the ones that fought are
     * wPartyFoughtCurrentEnemyFlags' bits (set with the gain bit when a Pokémon goes out, cleared at .done). The pass lives in
     * wBoostExpByExpAll: 0 the game's first pass (FaintEnemyPokemon zeroes it), 1 the EXP.ALL item's own pass (the game's, kept),
     * 2 the rest's (its "with EXP.ALL," is only read by the box, which the rest's pass doesn't show).
     *  - The loop's flag check calls T0: marked and, on the first pass, fought; on the rest's pass, not; the item's pass as the game.
     *  - `call nz, BoostExp` calls TH: the trainer boost as before, then on the rest's pass hQuotient's EXP halved (stat EXP stays).
     *  - `ld hl, GainedText; call PrintText` calls T1: no box on the rest's pass.
     *  - .done's `ld hl, wPartyGainExpFlags` jumps to T2: after the first pass, with anyone marked who didn't fight, the line and
     *    the loop again as the rest's pass; after that one the byte back to 0; then .done as the game.
     * All in GainExperience's bank, clear of the other trampolines, in the cart's padding; the line in the cart's language (German
     * where the gained box says "erhält", else English).
     */
    private fun expModernGen1(rom: ByteArray, divide: Int, gain: Int): List<InstantText.Write>? {
        fun u8(i: Int) = rom[i].toInt() and 0xFF
        fun u16(i: Int) = u8(i) or (u8(i + 1) shl 8)
        fun cpu(off: Int) = if (off < 0x4000) off else 0x4000 + off % 0x4000
        fun lohi(v: Int) = listOf(v and 0xFF, v shr 8)
        val bank = divide / 0x4000
        fun inBank(s: IntArray) = hits(rom, s).filter { it / 0x4000 == bank }
        val restart = inBank(EXP1_START).singleOrNull { u16(it + 1) == cpu(divide) } ?: return null
        val curMon = u16(restart + 8)
        val loopCheck = inBank(EXP1_CHECK).singleOrNull { u16(it + 2) == gain && u16(it + 5) == curMon } ?: return null
        val boost = inBank(EXP1_BOOST).singleOrNull() ?: return null
        val print = inBank(EXP1_PRINT).singleOrNull { u16(it + 1) == curMon } ?: return null
        val gained = inBank(EXP1_GAINED).singleOrNull { cpu(it) == u16(print + 10) } ?: return null
        val done = inBank(EXP1_DONE).singleOrNull { u16(it + 1) == gain } ?: return null
        if (!(restart < loopCheck && loopCheck < boost && boost < print && print < done && done < divide && divide - restart < 0x400)) return null
        val boostFn = u16(boost + 5)
        val printText = u16(print + 13)
        val q3 = u8(boost + 12)
        val q2 = u8(boost + 20)
        val pass = u16(gained + 6)
        val fought = u16(done + 18)
        if (q2 != q3 - 1 || listOf(curMon, pass, fought).any { it !in 0xC000..0xDFFF }) return null
        // The gained box's own words: _GainedText is `text_ram wNameBuffer` (01 nn nn), then `text " ..."`.
        val far = u8(gained + 3) * 0x4000 + (u16(gained + 1) - 0x4000)
        val german = far in 0 until rom.size - 8 && (4..7).map { u8(far + it) } == listOf(0x7F, 0xA4, 0xB1, 0xA7)
        val line = if (german) EXP_LINE_DE else EXP_LINE_EN
        val size = T0_SIZE + G1_TH_SIZE + G1_T1_SIZE + G1_T2_SIZE + line.size
        val end = (bank + 1) * 0x4000
        val at = end - 160 - size
        if (end > rom.size || run(rom, bank) < size + 160) return null
        val t0 = at; val th = t0 + T0_SIZE; val t1 = th + G1_TH_SIZE; val t2 = t1 + G1_T1_SIZE; val msg = t2 + G1_T2_SIZE
        val t = ArrayList<Int>()
        fun rel(from: Int, to: Int) { t[from] = to - (from + 1) }
        // T0: the mon's bit in b, then ld a, [gain]; and b; ret z; by the pass: 0 fought, 1 the game's, 2 not fought.
        t += listOf(0xFA) + lohi(curMon) + listOf(0x3C, 0x4F, 0xAF, 0x37, 0x17, 0x0D, 0x20, 0xFC, 0x47)
        t += listOf(0xFA) + lohi(gain) + listOf(0xA0, 0xC8, 0xFA) + lohi(pass) + listOf(0xA7, 0x28, 0)
        val first = t.size - 1
        t += listOf(0x3D, 0x28, 0)
        val yes1 = t.size - 1
        t += listOf(0xFA) + lohi(fought) + listOf(0xA0, 0x28, 0)
        val yes2 = t.size - 1
        t += listOf(0xAF, 0xC9)
        rel(first, t.size)
        t += listOf(0xFA) + lohi(fought) + listOf(0xA0, 0xC9)
        rel(yes1, t.size); rel(yes2, t.size)
        t += listOf(0x3E, 0x01, 0xA7, 0xC9)
        check(t.size == T0_SIZE)
        // TH: call nz, BoostExp; ld a, [pass]; cp 2; ret nz; hQuotient + 2 / + 3 (big-endian) halved
        t += listOf(0xC4) + lohi(boostFn) + listOf(0xFA) + lohi(pass) + listOf(0xFE, 0x02, 0xC0)
        t += listOf(0xF0, q2, 0xCB, 0x3F, 0xE0, q2, 0xF0, q3, 0x1F, 0xE0, q3, 0xC9)
        check(t.size == T0_SIZE + G1_TH_SIZE)
        // T1: ld a, [pass]; cp 2; ret z; ld hl, GainedText; jp PrintText
        t += listOf(0xFA) + lohi(pass) + listOf(0xFE, 0x02, 0xC8, 0x21) + lohi(cpu(gained)) + listOf(0xC3) + lohi(printText)
        check(t.size == T0_SIZE + G1_TH_SIZE + G1_T1_SIZE)
        // T2: ld a, [pass]; and a; jr nz, .after; ld a, [fought]; cpl; ld b, a; ld a, [gain]; and b; jr z, .out
        t += listOf(0xFA) + lohi(pass) + listOf(0xA7, 0x20, 0)
        val after = t.size - 1
        t += listOf(0xFA) + lohi(fought) + listOf(0x2F, 0x47, 0xFA) + lohi(gain) + listOf(0xA0, 0x28, 0)
        val out1 = t.size - 1
        // ld hl, the line; call PrintText; ld a, 2; ld [pass], a; jp the loop's start
        t += listOf(0x21) + lohi(cpu(msg)) + listOf(0xCD) + lohi(printText) + listOf(0x3E, 0x02, 0xEA) + lohi(pass) + listOf(0xC3) + lohi(cpu(restart + 3))
        // .after: cp 2; jr nz, .out; xor a; ld [pass], a; .out: ld hl, wPartyGainExpFlags; jp .done + 3
        rel(after, t.size)
        t += listOf(0xFE, 0x02, 0x20, 0)
        val out2 = t.size - 1
        t += listOf(0xAF, 0xEA) + lohi(pass)
        rel(out1, t.size); rel(out2, t.size)
        t += listOf(0x21) + lohi(gain) + listOf(0xC3) + lohi(cpu(done + 3))
        check(t.size == T0_SIZE + G1_TH_SIZE + G1_T1_SIZE + G1_T2_SIZE)
        val all = ByteArray(t.size + line.size) { if (it < t.size) t[it].toByte() else line[it - t.size] }
        fun pieces(off: Int, value: ByteArray) = (value.indices step 4).map { i ->
            val n = minOf(4, value.size - i)
            InstantText.Write(TAG + off + i, rom.copyOfRange(off + i, off + i + n), value.copyOfRange(i, i + n))
        }
        fun jump(op: Int, to: Int, len: Int) = ByteArray(len).also { it[0] = op.toByte(); it[1] = (cpu(to) and 0xFF).toByte(); it[2] = (cpu(to) shr 8).toByte() }
        return pieces(at, all) + pieces(loopCheck + 1, jump(0xCD, t0, EXP1_CHECK_LEN)) + pieces(boost + 4, jump(0xCD, th, 3)) +
            pieces(print + 9, jump(0xCD, t1, 6)) + pieces(done, jump(0xC3, t2, 3))
    }
    private const val G1_TH_SIZE = 21
    private const val G1_T1_SIZE = 12
    private const val G1_T2_SIZE = 45

    /** A text for BattleTextbox in Gen 2's charmap: `text`, the lines (`line`, then `cont`), `prompt`. */
    private fun gen2Text(vararg lines: String): ByteArray {
        val out = ArrayList<Int>()
        out += 0x00
        lines.forEachIndexed { i, l ->
            if (i == 1) out += 0x4F else if (i > 1) out += 0x55
            for (c in l) out += when (c) {
                in 'A'..'Z' -> 0x80 + (c - 'A'); in 'a'..'z' -> 0xA0 + (c - 'a')
                ' ' -> 0x7F; '!' -> 0xE7; '.' -> 0xE8; 'ä' -> 0xC3; 'ö' -> 0xC4; 'ü' -> 0xC5
                else -> error("no Gen 2 glyph for $c")
            }
        }
        out += 0x58
        return ByteArray(out.size) { out[it].toByte() }
    }
    private const val T0_SIZE = 43
    private const val T1_SIZE = 22
    private const val T2_SIZE = 42
    private val EXP_LINE_EN = gen2Text("The others gained", "EXP. Points!")
    private val EXP_LINE_DE = gen2Text("Die anderen", "erhalten auch EP!")
}
