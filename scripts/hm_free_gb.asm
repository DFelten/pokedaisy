; NO HMS NEEDED on the Game Boy (shared/src/commonMain/kotlin/com/pokedaisy/app/companion/data/HmFreeGb.kt): code of our own put
; in a bank's unused end, assembled per cart by scripts/gen_hm_free_gb.py with rgbasm (the addresses below come in as
; -D defines from pret's .sym for that cart). Each section is written over zero bytes the game never reads.
;
; Gen 1 (Red / Blue / Yellow): field moves are only ever used from the party menu, whose list GetMonFieldMoves builds
; from the Pokémon's own moves. HmMenu1 runs it, then adds the HMs whose badge you have that it doesn't list (CUT, SURF,
; STRENGTH, FLASH, FLY, in that order) up to the menu's four rows. The game's own handlers then run them, badge checks
; and all. DisplayFieldMoveMonMenu's `call GetMonFieldMoves` is pointed here.
;
; Gen 2 (Gold / Silver / Crystal): CUT, SURF, STRENGTH, WHIRLPOOL and WATERFALL from the field ask CheckPartyMove (move
; in d: carry = nobody knows it, else wCurPartyMon = who does). CheckPartyMove2 is the game's routine with one more
; step: nobody knowing one of those HMs, the first Pokémon that isn't an egg does it. Headbutt and Rock Smash (TMs)
; keep the game's rule. CheckPartyMove's first bytes jump here. HmMenu2 adds FLY and FLASH to the party submenu (their
; badges in, room for them and CANCEL), as GetMonSubmenuItems' `ld a, MONMENUITEM_STATS / call AddMonMenuItem`.

IF DEF(GEN1)

SECTION "hm menu", ROMX[HERE], BANK[BANKNO]
HmMenu1:
	call GetMonFieldMoves
	ld a, [wObtainedBadges]
	ld d, a
	ld hl, .table
.next
	ld a, [wNumFieldMoves]
	cp 4
	ret nc
	ld a, [hli]          ; the badge's bit
	and a
	ret z
	and d
	jr z, .skip
	ld e, [hl]           ; the move's FieldMoveNames index
	push hl
	ld hl, wFieldMoves
	ld b, 4
.scan
	ld a, [hli]
	cp e
	jr z, .have
	dec b
	jr nz, .scan
	ld a, [wNumFieldMoves]
	ld c, a
	ld b, 0
	ld hl, wFieldMoves
	add hl, bc
	ld [hl], e
	inc a
	ld [wNumFieldMoves], a
	pop hl
	push hl
	inc hl
	ld b, [hl]           ; its leftmost tile
	ld a, [wFieldMovesLeftmostXCoord]
	cp b
	jr c, .have
	ld a, b
	ld [wFieldMovesLeftmostXCoord], a
.have
	pop hl
.skip
	inc hl
	inc hl
	jr .next
.table
	db 1 << 1, 1, $0C    ; CUT: CASCADEBADGE
	db 1 << 4, 4, $0C    ; SURF: SOULBADGE
	db 1 << 3, 5, $0A    ; STRENGTH: RAINBOWBADGE
	db 1 << 0, 6, $0C    ; FLASH: BOULDERBADGE
	db 1 << 2, 2, $0C    ; FLY: THUNDERBADGE
	db 0
HmEnd::

ENDC

IF DEF(GEN2_CHECK)

DEF CUT EQU $0F
DEF FLY EQU $13
DEF SURF EQU $39
DEF STRENGTH EQU $46
DEF WATERFALL EQU $7F
DEF WHIRLPOOL EQU $FA
DEF EGG EQU $FD
DEF PARTYMON_STRUCT_LENGTH EQU 48

SECTION "check party move", ROMX[HERE], BANK[BANKNO]
CheckPartyMove2:
	ld e, 0
	xor a
	ld [wCurPartyMon], a
.loop
	ld c, e
	ld b, 0
	ld hl, wPartySpecies
	add hl, bc
	ld a, [hl]
	and a
	jr z, .none
	cp -1
	jr z, .none
	cp EGG
	jr z, .next
	ld bc, PARTYMON_STRUCT_LENGTH
	ld hl, wPartyMon1Moves
	ld a, e
	call AddNTimes
	ld b, 4
.check
	ld a, [hli]
	cp d
	jr z, .yes
	dec b
	jr nz, .check
.next
	inc e
	jr .loop
.yes
	ld a, e
	ld [wCurPartyMon], a
	xor a
	ret
.none
	ld hl, .hms
.hm
	ld a, [hli]
	and a
	jr z, .fail
	cp d
	jr nz, .hm
	ld e, 0
.first
	ld c, e
	ld b, 0
	ld hl, wPartySpecies
	add hl, bc
	ld a, [hl]
	and a
	jr z, .fail
	cp -1
	jr z, .fail
	cp EGG
	jr nz, .yes
	inc e
	jr .first
.fail
	scf
	ret
.hms
	db CUT, SURF, STRENGTH, WHIRLPOOL, WATERFALL, 0
HmEnd::

ENDC

IF DEF(GEN2_MENU)

DEF MONMENUITEM_FLY EQU 2
DEF MONMENUITEM_FLASH EQU 6
DEF MONMENUITEM_STATS EQU 15
DEF ZEPHYRBADGE EQU 0
DEF STORMBADGE EQU 5

SECTION "hm menu", ROMX[HERE], BANK[BANKNO]
HmMenu2:
	ld a, [wLinkMode]
	and a
	jr nz, .done
	ld a, [wJohtoBadges]
	bit STORMBADGE, a
	ld a, MONMENUITEM_FLY
	call nz, .add
	ld a, [wJohtoBadges]
	bit ZEPHYRBADGE, a
	ld a, MONMENUITEM_FLASH
	call nz, .add
.done
	ld a, MONMENUITEM_STATS
	jp AddMonMenuItem

; a menu item, unless it's there or there'd be no room for STATS / SWITCH / MOVE / ITEM and CANCEL after it
.add
	ld e, a
	ld a, [wMonSubmenuCount]
	cp 3
	ret nc
	ld b, a
	ld hl, wMonSubmenuItems
	and a
	jr z, .put
.scan
	ld a, [hli]
	cp e
	ret z
	dec b
	jr nz, .scan
.put
	ld a, e
	jp AddMonMenuItem
HmEnd::

ENDC
