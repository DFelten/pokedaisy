package com.pokedaisy.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.BattleInput
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.i18n.tr
import com.pokedaisy.app.companion.ui.CompanionBack
import com.pokedaisy.app.companion.ui.CompanionKeys
import com.pokedaisy.app.companion.ui.CompanionScreen
import com.pokedaisy.app.companion.ui.MenuKey
import com.pokedaisy.app.companion.ui.OptionButton
import com.pokedaisy.app.companion.ui.SidePanelHost
import com.pokedaisy.app.companion.ui.SidePanelOpenTab
import com.pokedaisy.app.companion.ui.rememberGbaTextMetrics

/**
 * A PC fan game's companion on a device with one screen (PcGameActivity): the companion's window over the running game,
 * as a GBA game's is (SidePanelHost), or, while it's closed, the tab on the screen's right edge that opens it. The window
 * has a CLOSE GAME button on the dim at its top left: a phone's gesture BACK can't be held, and the gear's SETTINGS (with
 * its CLOSE GAME) isn't there for a PC game.
 */
@Composable
fun PcCompanionWindow(
    open: Boolean,
    snapshot: SnapshotView,
    back: CompanionBack,
    keys: CompanionKeys,
    battleInput: BattleInput?,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onCloseGame: () -> Unit,
    /** The D-pad and A the companion left, for Compose's focus (sendFocusKey); null = moved inside here. */
    focusKeys: ((MenuKey, Boolean, () -> Unit) -> Boolean)? = null,
    compactText: Boolean = false,
) {
    if (!open) {
        // Halfway up the right edge: clear of the touch controls' shoulder buttons above and A / B below.
        Box(Modifier.fillMaxSize()) { Box(Modifier.align(Alignment.CenterEnd)) { SidePanelOpenTab(onOpen) } }
        return
    }
    Box(Modifier.fillMaxSize()) {
        SidePanelHost(window = true, onClose = onClose, keys = keys, focusKeys = focusKeys, compactText = compactText) {
            CompanionScreen(snapshot = snapshot, back = back, battleInput = battleInput, keys = keys)
        }
        // Over the dim, so its tap is its own (the dim's closes the window).
        OptionButton(tr("CLOSE GAME"), rememberGbaTextMetrics(), onClick = onCloseGame, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
    }
}
