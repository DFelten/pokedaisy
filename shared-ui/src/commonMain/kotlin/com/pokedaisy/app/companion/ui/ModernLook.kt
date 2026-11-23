package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokedaisy.app.companion.data.GENDER_SYMBOL_FEMALE
import com.pokedaisy.app.companion.data.GENDER_SYMBOL_MALE
import com.pokedaisy.app.companion.data.MonView
import com.pokedaisy.app.companion.i18n.tr
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.pokedaisy.app.companion.ui.theme.AppColors
import com.pokedaisy.app.companion.ui.theme.AppViews
import com.pokedaisy.app.companion.ui.theme.DarkMode

/** VIEW MODERN is on ([AppViews]): read where a screen draws its own pieces one way or the other. */
val modernView: Boolean get() = AppViews.modern

/**
 * VIEW MODERN's page, behind the Library, Settings, TOOLS and the companion: a soft wash top to bottom with two wide
 * glows (the brand blue up left, a warm one down right) and a faint diagonal weave, after the X / Y menus' backgrounds.
 * A THEME lends the glows its blobs' colours; DARK MODE turns it to deep navy.
 */
@Composable
fun ModernBackdrop(modifier: Modifier = Modifier.fillMaxSize()) {
    val dark = DarkMode.on
    val theme = AppColors.theme
    val themed = theme.hue != null && theme.blobs.size >= 2
    val glowA = if (themed) theme.blobs[0] else Color(0xFF5585EE)
    val glowB = if (themed) theme.blobs[1] else Color(0xFFFF9474)
    val top = if (dark) Color(0xFF151B2B) else Color(0xFFF4F7FD)
    val low = if (dark) Color(0xFF0E1320) else Color(0xFFE2E9F8)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(top, low)))
        val big = maxOf(size.width, size.height)
        drawCircle(
            Brush.radialGradient(listOf(glowA.copy(alpha = if (dark) 0.22f else 0.20f), Color.Transparent), Offset(size.width * 0.12f, size.height * 0.05f), big * 0.55f),
            big * 0.55f, Offset(size.width * 0.12f, size.height * 0.05f),
        )
        drawCircle(
            Brush.radialGradient(listOf(glowB.copy(alpha = if (dark) 0.14f else 0.16f), Color.Transparent), Offset(size.width * 0.92f, size.height * 0.95f), big * 0.5f),
            big * 0.5f, Offset(size.width * 0.92f, size.height * 0.95f),
        )
        // The weave: thin diagonal lines, a few percent of the ink.
        val line = lerp(top, if (dark) Color.White else Color(0xFF3D6FE0), if (dark) 0.035f else 0.05f)
        val gap = (big / 38f).coerceAtLeast(12f)
        var x = -size.height
        while (x < size.width) {
            drawLine(line, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.2f)
            x += gap
        }
    }
}

/** An HP bar's colour by what's left: green, yellow under half, red under a fifth (the games' own steps). */
fun modernHpColor(hp: Int, maxHp: Int): Color {
    val f = if (maxHp <= 0) 0f else hp.toFloat() / maxHp
    return when {
        f > 0.5f -> Color(0xFF34C46A)
        f > 0.2f -> Color(0xFFF2B630)
        else -> Color(0xFFEE5240)
    }
}

/** A rounded HP bar: its track, the colour for what's left. */
@Composable
fun ModernHpBar(hp: Int, maxHp: Int, modifier: Modifier = Modifier, track: Color? = null) {
    val dark = DarkMode.on
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track ?: if (dark) Color(0xFF2E374C) else Color(0xFFE3E8F1), cornerRadius = r)
        val f = if (maxHp <= 0) 0f else (hp.toFloat() / maxHp).coerceIn(0f, 1f)
        if (f > 0f) drawRoundRect(modernHpColor(hp, maxHp), size = Size(size.width * f, size.height), cornerRadius = r)
    }
}

/** A status (PSN, FNT...) as a small rounded chip in its colour. */
@Composable
fun ModernStatusChip(status: String, m: GbaTextMetrics, modifier: Modifier = Modifier) {
    val color = if (status == "FNT") Color(0xFF7A8399) else AppColors.statusColor(status)
    Box(
        modifier.drawBehind { drawRoundRect(color, cornerRadius = CornerRadius(size.height / 2f)) }
            .padding(horizontal = m.u * 4, vertical = m.u * 0.5f),
    ) { GbaText(status, Color.White, Color.Transparent, m, bold = true) }
}

/** The PARTY in VIEW MODERN: the six slots in a column, the cursor's Pokémon on a stage beside them, when it's this wide. */
internal const val MODERN_STAGE_ASPECT = 1.15f

/** The list's share of the width beside the stage. */
internal const val MODERN_LIST_SHARE = 0.56f

/** The running game's Pokédex tables, for the stage's picture (null: its icon). */
internal val LocalPartyDex = androidx.compose.runtime.staticCompositionLocalOf<com.pokedaisy.app.companion.data.PokedexTables?> { null }

/** How far a pill sits in from its row's left edge: the cursor's chevron goes there. */
internal fun modernPillInset(rowHeight: Float) = rowHeight * 0.42f

/** A pill's height in its row, the gap above and below it. */
private fun pillHeight(rowHeight: Float) = rowHeight * 0.84f

/**
 * VIEW MODERN's party behind its slots, after the Switch games' party screen: one rounded window, the list's side the
 * accent's colour cut by a slanted edge (a darker stripe along it), the stage's side light with a large faint ring.
 * [listShare] 1 = no stage, the band all across.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawModernPartyWindow(listShare: Float, accent: Color, paper: Color, dark: Boolean) {
    val r = minOf(18.dp.toPx(), size.minDimension * 0.06f)
    CompanionSkins.MODERN.dropShadow?.let { drawSoftShadow(it, r) }
    val clip = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
    }
    clipPath(clip) {
        // The stage: light, a little of the accent towards its bottom.
        drawRect(Brush.verticalGradient(listOf(paper, lerp(paper, accent, if (dark) 0.10f else 0.14f))))
        if (listShare < 1f) {
            val sx = size.width * listShare
            val cx = sx + (size.width - sx) * 0.5f
            val cy = size.height * 0.5f
            val rr = minOf(size.width - sx, size.height) * 0.46f
            val ring = accent.copy(alpha = if (dark) 0.16f else 0.12f)
            val w = rr * 0.12f
            drawCircle(ring, rr, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            drawLine(ring, Offset(cx - rr, cy), Offset(cx - rr * 0.32f, cy), w)
            drawLine(ring, Offset(cx + rr * 0.32f, cy), Offset(cx + rr, cy), w)
            drawCircle(ring, rr * 0.26f, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        }
        // The band: its right edge leans, wider at the top.
        val lean = size.height * 0.16f
        val right = if (listShare >= 1f) size.width + lean else size.width * listShare + lean * 0.5f
        fun band(dx: Float) = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f); lineTo(right + dx, 0f); lineTo(right + dx - lean, size.height); lineTo(0f, size.height); close()
        }
        // DARK MODE's accent is a light one (for text): the band takes it down towards the night.
        val fill = if (dark) lerp(accent, Color(0xFF151B2B), 0.5f) else accent
        drawPath(band(0f), lerp(fill, Color.Black, 0.22f))
        drawPath(band(-size.height * 0.035f), fill)
    }
}

/**
 * VIEW MODERN's party slot, after the Switch games': a pill (white on the band, the cursor's dark with a chevron to its
 * left), the Pokémon's icon at its left end, its name and gender over a thin HP bar, then its HP and level (or status).
 * An empty slot draws nothing, as in those games.
 */
@Composable
fun ModernPartySlot(mon: MonView?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, sound: Boolean = true) {
    if (mon == null) { Box(modifier); return }
    val density = LocalDensity.current
    val dark = DarkMode.on
    BoxWithConstraints(modifier) {
        val rowH = with(density) { maxHeight.toPx() }
        val pillH = with(density) { pillHeight(rowH).toDp() }
        val inset = with(density) { modernPillInset(rowH).toDp() }
        val pillW = maxWidth - inset
        val fainted = !mon.isEgg && mon.hp == 0
        val fill = when {
            selected -> if (dark) Color(0xFFF1F4FA) else Color(0xFF22262F)
            fainted -> if (dark) Color(0xFF3A2A31) else Color(0xFFF4E6E6)
            else -> if (dark) Color(0xFF283146) else Color.White
        }
        val ink = when {
            selected -> if (dark) Color(0xFF1B2030) else Color.White
            else -> if (dark) Color(0xFFE9EDF5) else Color(0xFF1E2230)
        }
        val soft = ink.copy(alpha = 0.72f)
        // The cursor's chevron, white with a dark edge, left of the pill.
        if (selected) Canvas(Modifier.align(Alignment.CenterStart).padding(start = inset * 0.12f).size(inset * 0.66f, pillH * 0.5f)) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height)
            }
            val stroke = size.width * 0.34f
            drawPath(p, Color(0xFF22262F), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 1.9f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawPath(p, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        Box(
            Modifier.align(Alignment.CenterStart).padding(start = inset).fillMaxWidth().height(pillH)
                .slotClickable(sound, enabled = true, onClick = onClick)
                .drawBehind {
                    val cr = CornerRadius(size.height / 2f)
                    CompanionSkins.MODERN.dropShadow?.let { drawSoftShadow(it, cr.x) }
                    drawRoundRect(fill, cornerRadius = cr)
                },
        ) {
            val big = rememberGbaTextMetrics()
            val small = rememberGbaTextMetrics(1f)
            val h = pillH
            // A short pill (a grid's on a small screen): its name alone on the first line, the level and HP bar under it.
            val compact = pillW < h * 4.4f
            // Two lines in the pill: a short one (the RG DS) takes the name a size down, and the HP numbers only with room.
            val room = h * 0.86f
            val m = if (big.lineHeight + small.lineHeight + big.u * 2 <= room) big else small
            val numbers = !compact && m.lineHeight + small.lineHeight + m.u * 2 <= room
            val gender = when (mon.genderSymbol) {
                GENDER_SYMBOL_MALE -> "\u2642" to Color(0xFF3D7BE8)
                GENDER_SYMBOL_FEMALE -> "\u2640" to Color(0xFFE5507A)
                else -> null
            }
            val status = if (mon.isEgg) "" else if (mon.hp == 0) "FNT" else mon.status
            Row(Modifier.matchParentSize().padding(end = h * if (compact) 0.3f else 0.42f), verticalAlignment = Alignment.CenterVertically) {
                // The icon at its left end, a little larger than the pill is tall, as the games set theirs.
                val icon = if (compact) h * 0.9f else h * 1.05f
                Box(Modifier.width(icon * 1.07f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    SpeciesIcon(mon.iconAsset, size = icon, modifier = if (fainted) Modifier.graphicsLayerAlpha(0.55f) else Modifier)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    // Its name and gender, then (right) its status and level.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GbaText(mon.shownName, ink, Color.Transparent, m, Modifier.weight(1f, fill = !compact), bold = true)
                        gender?.let { (sign, color) ->
                            Spacer(Modifier.width(m.u * 3))
                            GbaText(sign, color, Color.Transparent, m, bold = true)
                        }
                        if (!compact && !mon.isEgg) {
                            if (status.isNotEmpty()) {
                                Spacer(Modifier.width(m.u * 3))
                                ModernStatusChip(status, small)
                            }
                            Spacer(Modifier.width(m.u * 4))
                            GbaText(tr("Lv{0}", mon.level), soft, Color.Transparent, small, bold = true)
                        }
                    }
                    // A thin HP bar, its numbers after it (a short pill: its level and status before it).
                    if (!mon.isEgg) {
                        Spacer(Modifier.height(m.u * 2))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (compact) {
                                if (status.isNotEmpty()) {
                                    ModernStatusChip(status, small)
                                    Spacer(Modifier.width(m.u * 3))
                                } else {
                                    GbaText(tr("Lv{0}", mon.level), soft, Color.Transparent, small, bold = true)
                                    Spacer(Modifier.width(m.u * 3))
                                }
                            }
                            ModernHpBar(
                                mon.hp, mon.maxHp, Modifier.weight(1f).height(maxOf(5.dp, h * 0.09f)),
                                track = if (selected) soft.copy(alpha = 0.25f) else null,
                            )
                            if (numbers) {
                                Spacer(Modifier.width(m.u * 4))
                                GbaText("${mon.hp}/${mon.maxHp}", soft, Color.Transparent, small, bold = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.graphicsLayerAlpha(a: Float) = this.then(Modifier.graphicsLayer { alpha = a })

/**
 * VIEW MODERN's stage beside the party list: [mon]'s front picture from the ROM (else its icon), as large as whole pixels
 * allow, its types under it.
 */
@Composable
fun ModernPartyStage(mon: MonView?, modifier: Modifier = Modifier) {
    if (mon == null) { Box(modifier); return }
    val dex = LocalPartyDex.current
    val density = LocalDensity.current
    val m = rememberGbaTextMetrics()
    Column(modifier.padding(m.u * 8), horizontalAlignment = Alignment.CenterHorizontally) {
        val species = mon.species.takeIf { !mon.isEgg && dex != null } ?: 0
        val sprite = if (dex != null && species > 0) {
            rememberRomBitmap(dex, species, com.pokedaisy.app.companion.data.PokedexSource::cachedFrontSprite, com.pokedaisy.app.companion.data.PokedexSource::frontSprite)
        } else null
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (sprite != null) TrimmedSprite(sprite, Modifier.fillMaxSize(0.82f))
            else BoxWithConstraints(Modifier.fillMaxSize(0.82f), contentAlignment = Alignment.Center) {
                val room = with(density) { minOf(maxWidth, maxHeight).toPx() }
                SpeciesIcon(mon.iconAsset, with(density) { (32f * kotlin.math.floor(room / 32f).coerceAtLeast(1f)).toDp() })
            }
        }
        // Its types, a size up where they fit side by side.
        if (!mon.isEgg && mon.types.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val tag = if (!LocalShortCompanion.current && maxWidth >= 110.dp * mon.types.size) 16.sp else 12.sp
            Row(horizontalArrangement = Arrangement.spacedBy(m.u * 3)) { mon.types.forEach { TypeBadge(it, fontSize = tag) } }
        }
    }
}

/**
 * VIEW MODERN's folder tab beside a window (SETTINGS' groups, the bag's pockets): rounded on its left, square on its right
 * where it meets the window. The open one is the window's own paper, its hairline round the left, top and bottom, and runs
 * [reach] px into the window over its left rim so the two read as one; the others are [idle] in the same hairline.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawModernFolderTab(
    open: Boolean, paper: Color, idle: Color, line: Color, radius: Float, reach: Float,
) {
    val w = size.width + if (open) reach else 0f
    val path = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(
            0f, 0f, w, size.height,
            topLeftCornerRadius = CornerRadius(radius), bottomLeftCornerRadius = CornerRadius(radius),
            topRightCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
        ))
    }
    val hair = 1.dp.toPx()
    if (open) CompanionSkins.MODERN.dropShadow?.let { s ->
        drawPath(path, s.copy(alpha = s.alpha * 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(hair * 3))
    }
    drawPath(path, if (open) paper else idle)
    drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(hair))
    // The open tab's right side is the window's: its paper over the seam.
    if (open) drawRect(paper, Offset(size.width - hair, hair), Size(reach + hair, size.height - 2 * hair))
}

/** A party row's tags' room: past the pill's icon and short of its round end, a corner tag standing on the pill's top edge
 * (half over the band, off the name). */
internal fun Modifier.modernBadgeRoom(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val h = constraints.maxHeight
        val pill = pillHeight(h.toFloat())
        val gap = ((h - pill) / 2f).toInt()
        val start = (modernPillInset(h.toFloat()) + pill * 0.9f).toInt()
        val end = (pill * 0.42f).toInt()
        val p = measurable.measure(constraints.copy(
            minWidth = 0, maxWidth = (constraints.maxWidth - start - end).coerceAtLeast(0), minHeight = 0, maxHeight = (h - gap).coerceAtLeast(0),
        ))
        layout(constraints.maxWidth, h) { p.place(start, gap - (pill * 0.24f).toInt()) }
    },
)
