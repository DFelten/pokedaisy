@ NO HMS NEEDED (companion/data/HmFree.kt): ScrCmd_checkpartymove's replacement, put in a run of the ROM's padding (0xFF)
@ and reached from the original's first 8 bytes (`ldr r3, [pc]; bx r3`, then this one's address). The game's own
@ version answers the first Pokémon (not an egg) that knows the move, else PARTY_SIZE; this one does the same, but with
@ nobody knowing it, for an HM whose item is in the bag, it answers the first Pokémon that isn't an egg, so every field
@ move script (whose badge check comes first) goes on with that one. Anything else (Emerald's secret bases ask for
@ MOVE_SECRET_POWER, a TM) keeps the game's rule.
@
@ Registers: r4 = &gPlayerParty[i], r5 = the answer (6 = none yet), r6 = i, r7 = the move.
@ gSpecialVar_Result is gSpecialVar_0x8004 + 0x10 in every build this goes on (HmFree.kt checks it).
@ The calls are far from the padding (more than BL's 4 MB), so each goes through r3 and `bx r3`.
@ The words are filled in per ROM (HmFree.kt): gPlayerParty, gSpecialVar_0x8004, ScriptReadHalfword, GetMonData,
@ MonKnowsMove, CheckBagHasItem (each with its Thumb bit). The HM items are ITEM_HM01 (339) on, in Hms' order.
@
@ Assemble: clang --target=thumbv4t-none-eabi -mcpu=arm7tdmi -c hm_free.s -o t.o (the bytes are HmFree.CHECK_CODE:
@ xcrun llvm-objdump -s -j .text t.o).
    .syntax unified
    .thumb
    .text
start:
    push {r4, r5, r6, r7, lr}
    ldr r3, ReadHalfword
    bl via_r3
    lsls r0, r0, #16
    lsrs r7, r0, #16
    movs r5, #6
    movs r6, #0
    ldr r4, Party
loop:
    cmp r6, #5
    bhi done
    adds r0, r4, #0
    movs r1, #11
    ldr r3, GetMonData
    bl via_r3
    cmp r0, #0
    beq done
    adds r0, r4, #0
    movs r1, #0x2d
    ldr r3, GetMonData
    bl via_r3
    cmp r0, #0
    bne next
    cmp r5, #6
    bne knows
    adds r5, r6, #0
knows:
    adds r0, r4, #0
    adds r1, r7, #0
    ldr r3, MonKnowsMove
    bl via_r3
    cmp r0, #0
    bne found
next:
    adds r6, r6, #1
    adds r4, #100
    b loop
done:
    cmp r5, #6
    beq set
    adr r2, Hms
    movs r0, #0
hm:
    ldrh r1, [r2, r0]
    cmp r1, r7
    beq have
    adds r0, #2
    cmp r0, #16
    bcc hm
    b none
have:
    @ +0x62. A ROM whose HMs aren't vanilla's gets `b set` (0A E0) here: the HM's move without the bag check.
    lsrs r0, r0, #1
    ldr r1, Hm01
    adds r0, r0, r1
    movs r1, #1
    ldr r3, CheckBagHasItem
    bl via_r3
    cmp r0, #0
    bne set
none:
    movs r5, #6
    b set
found:
    adds r5, r6, #0
set:
    ldr r1, Var8004
    strh r5, [r1, #0x10]
    cmp r5, #6
    beq out
    movs r0, #100
    muls r0, r5, r0
    ldr r1, Party
    adds r0, r0, r1
    movs r1, #11
    ldr r3, GetMonData
    bl via_r3
    ldr r1, Var8004
    strh r0, [r1]
out:
    movs r0, #0
    pop {r4, r5, r6, r7}
    pop {r1}
    bx r1
via_r3:
    bx r3
    .align 2
Party: .word 0
Var8004: .word 0
ReadHalfword: .word 0
GetMonData: .word 0
MonKnowsMove: .word 0
CheckBagHasItem: .word 0
Hm01: .word 339
@ HM01 CUT, HM02 FLY, HM03 SURF, HM04 STRENGTH, HM05 FLASH, HM06 ROCK_SMASH, HM07 WATERFALL, HM08 DIVE.
Hms: .hword 15, 19, 57, 70, 148, 249, 127, 291
