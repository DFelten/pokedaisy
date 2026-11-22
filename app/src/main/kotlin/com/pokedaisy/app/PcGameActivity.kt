package com.pokedaisy.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.data.activeGame
import com.pokedaisy.app.companion.i18n.tr
import com.pokedaisy.app.companion.ui.CompanionBack
import com.pokedaisy.app.companion.ui.CompanionScreen
import com.pokedaisy.app.companion.ui.OwnItemNames
import com.pokedaisy.app.companion.ui.AppBackdrop
import com.pokedaisy.app.companion.ui.OptionColors
import com.pokedaisy.app.companion.ui.OptionTitleWindow
import com.pokedaisy.app.companion.ui.rememberGbaTextMetrics
import com.pokedaisy.app.companion.ui.theme.AppTheme
import org.json.JSONObject
import java.io.File
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.concurrent.thread

/**
 * PC fan games (FeatureFlags.pcGames, a prototype; docs/specs/pc-fan-games.md): one Pokemon Essentials game on the
 * mkxp-z core ([PcCore]), drawn on this activity's GL thread. The game alone on the main screen; with a second screen
 * the companion there ([CompanionScreen]: PARTY with its summaries, and the bag), built from the game's own data through
 * our preload's mailbox ([PcCompanion]). BACK goes back in the companion first, then twice closes the game;
 * the games save in their own menus (their save folder is backed up before each start, [PcGames.backupSaves]).
 *
 * Resume: each pause and close writes a resume state ([PcGames.resumeState], the Ruby heap, ~100 MB gzipped) with the
 * save folders' fingerprint beside it; a fresh launch with RESUME GAMES on loads it after the first frame, only while
 * that fingerprint still holds (else it's set aside as `.replaced` and the game boots as usual). A quit from the game's
 * own menu drops it.
 */
class PcGameActivity : ComponentActivity() {

    companion object {
        /** The game's Game.ini. */
        const val EXTRA_GAME = "game"
        private const val TAG = "pokedaisy/pc"
    }

    private var gl: GLSurfaceView? = null
    private var audio: AudioTrack? = null
    @Volatile private var started = false
    @Volatile private var closing = false
    @Volatile private var resumed = false
    private var keyMask = 0
    private var stickMask = 0
    private var lastBack = 0L
    private var presentation: DualScreenPresentation? = null
    private var ini: File? = null
    /** The state to load after the first frame (GL thread), or null. */
    @Volatile private var pendingResume: File? = null

    /** What the overlay says (loading, an error), null once the game shows. */
    private var notice by mutableStateOf<String?>(tr("LOADING..."))
    /** What the companion on the second screen shows ([PcCompanion]); LOADING until the game's data comes in. */
    private var snapshot by mutableStateOf(SnapshotView(connected = true, game = GameKind.ESSENTIALS))
    private var companion: PcCompanion? = null
    private val companionBack = CompanionBack()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goImmersive()
        val ini = intent?.getStringExtra(EXTRA_GAME)?.let { File(it) }
        this.ini = ini
        val root = FrameLayout(this)
        root.addView(ComposeView(this).apply { setContent { AppTheme { Notice() } } })
        setContentView(root)
        if (ini == null || !ini.isFile) {
            notice = tr("NO GAME FOUND")
            return
        }
        thread(name = "pc-prepare") {
            val core = runCatching { PcGames.installCore(this) }.getOrNull()
            val sys = runCatching { PcGames.installPreloads(this) }.getOrNull()
            val saves = PcGames.saveDir(this).apply { mkdirs() }
            val backedUp = PcGames.backupSaves(this)
            pendingResume = if (backedUp) resumeToLoad(ini) else null
            runOnUiThread {
                when {
                    core == null -> notice = tr("NO CORE: put {0} in {1}", PcGames.CORE_NAME, "Android/data/$packageName/files/pc/cores/")
                    sys == null -> notice = tr("COULDN'T SET UP THE GAME'S FILES")
                    !backedUp -> notice = tr("COULDN'T BACK UP THE SAVES: the game stays closed")
                    else -> startGame(root, core, ini, sys, saves)
                }
            }
        }
    }

    private fun startGame(root: FrameLayout, core: File, ini: File, sys: File, saves: File) {
        companion = PcCompanion(ini.parentFile ?: filesDir, filesDir)
        val view = GLSurfaceView(this)
        view.setEGLContextClientVersion(3)
        view.preserveEGLContextOnPause = true
        view.setRenderer(object : GLSurfaceView.Renderer {
            private var w = 1
            private var h = 1
            private val buf = ShortArray(48000)

            override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
                if (started) {
                    // The GL context went (preserveEGLContextOnPause can't always keep it): the core's textures
                    // with it. A prototype: close rather than draw garbage.
                    Log.w(TAG, "GL context lost: closing the game")
                    runOnUiThread { finish() }
                    return
                }
                val ok = PcCore.pcStart(core.path, ini.path, sys.path, saves.path)
                started = ok
                runOnUiThread {
                    if (ok) {
                        startAudio(PcCore.pcSampleRate())
                        if (pendingResume == null) notice = null else notice = tr("RESUMING...")
                        startMailbox()
                    } else notice = tr("THE GAME DIDN'T START")
                }
            }

            override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
                w = width
                h = height
            }

            override fun onDrawFrame(unused: GL10?) {
                if (!started || closing) return
                if (PcCore.pcFrame(w, h) < 0) {
                    // The game's own quit: its resume state would be older than what the player chose.
                    ini?.let { PcGames.resumeState(this@PcGameActivity, it).delete() }
                    closeGame(saveState = false)
                    return
                }
                pendingResume?.let { state ->
                    pendingResume = null
                    val ok = PcCore.pcLoadState(state.path)
                    if (!ok) Log.w(TAG, "resume state didn't load: the game goes on from its boot")
                    runOnUiThread { notice = null }
                }
                val n = PcCore.pcAudio(buf)
                if (n > 0) audio?.write(buf, 0, n, AudioTrack.WRITE_NON_BLOCKING)
            }
        })
        root.addView(view, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        gl = view
        if (!resumed) view.onPause()
    }

    private fun startAudio(rate: Int) {
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        audio = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, rate / 10 * 4)) // ~100 ms
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
            .also { if (resumed) it.play() }
    }

    /** The companion's data, about once a second while the game is in front. */
    private fun startMailbox() {
        thread(name = "pc-mailbox", isDaemon = true) {
            while (!closing && !isDestroyed) {
                if (resumed && started) {
                    val c = companion
                    PcCore.pcMailbox()?.let { bytes ->
                        c?.snapshot(String(bytes, Charsets.UTF_8))?.let { view -> runOnUiThread { if (!isDestroyed) snapshot = view } }
                    }
                }
                SystemClock.sleep(1000)
            }
        }
    }

    /** The resume state for [ini] if it should load now (RESUME GAMES on, its saves unchanged), else null. */
    private fun resumeToLoad(ini: File): File? {
        val state = PcGames.resumeState(this, ini)
        val sidecar = PcGames.sidecarFor(state)
        if (!state.isFile || !Prefs(this).autoResume) return null
        val made = runCatching { JSONObject(sidecar.readText()).optString("saves") }.getOrNull()
        val now = PcGames.savesFingerprint(this)
        if (made == now) return state
        // The saves changed since (played elsewhere, a save copied in): the state would bring older progress back.
        Log.w(TAG, "resume state set aside: the saves changed since it was made ($made -> $now)")
        state.renameTo(File(state.path + ".replaced"))
        sidecar.delete()
        return null
    }

    /** Queues a resume state write on the GL thread (before it pauses or the game unloads). */
    private fun queueStateSave(view: GLSurfaceView) {
        val game = ini ?: return
        if (!started || pendingResume != null) return // not running yet, or the resume hasn't even loaded
        val state = PcGames.resumeState(this, game)
        val text = JSONObject().put("saves", PcGames.savesFingerprint(this)).put("game", game.path).toString()
        view.queueEvent { if (started) PcCore.pcSaveState(state.path, PcGames.sidecarFor(state).path, text) }
    }

    private fun closeGame(saveState: Boolean = true) {
        if (closing) return
        closing = true
        PcCore.pcInput(0)
        val view = gl
        if (view == null || !started) {
            finish()
            return
        }
        if (saveState) queueStateSave(view)
        view.queueEvent {
            PcCore.pcStop()
            started = false
            runOnUiThread { finish() }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // The companion's look and lookups: a PC game's (its types, its bag's names: PcCompanion).
        activeGame = GameKind.ESSENTIALS
        OptionColors.inGame = true
        OwnItemNames.lookup = itemNames
        goImmersive()
        gl?.onResume()
        audio?.play()
        showSecondScreen()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        keyMask = 0
        stickMask = 0
        PcCore.pcInput(0)
        audio?.pause()
        // HOME, another app, the screen going off: the game waits here, and a resume state goes down in case Android
        // ends the app meanwhile (taken now, written on a thread of its own).
        gl?.let { if (!closing) queueStateSave(it) }
        gl?.onPause()
        presentation?.dismiss()
        presentation = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closing = true
        audio?.release()
        audio = null
        if (OwnItemNames.lookup === itemNames) OwnItemNames.lookup = null
    }

    /** The bag's names by the ids [PcCompanion] gave its rows. */
    private val itemNames: (Int) -> String? = { id -> companion?.itemNames?.get(id) }

    private fun goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSecondScreen() {
        if (presentation != null) return
        val display = (if (Prefs(this).singleScreen) null else Screens.second(this)) ?: return
        presentation = DualScreenPresentation(this, display) { ctx: Context ->
            ComposeView(ctx).apply { setContent { AppTheme { CompanionScreen(snapshot = snapshot, back = companionBack) } } }
        }.also { runCatching { it.show() } }
    }

    // ---- input -------------------------------------------------------------------------------------------------

    private fun bitFor(code: Int): Int = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> PcCore.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> PcCore.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> PcCore.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> PcCore.RIGHT
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_Z -> PcCore.A
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_X -> PcCore.B
        KeyEvent.KEYCODE_BUTTON_X -> PcCore.X
        KeyEvent.KEYCODE_BUTTON_Y -> PcCore.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> PcCore.L
        KeyEvent.KEYCODE_BUTTON_R1 -> PcCore.R
        KeyEvent.KEYCODE_BUTTON_L2 -> PcCore.L2
        KeyEvent.KEYCODE_BUTTON_R2 -> PcCore.R2
        KeyEvent.KEYCODE_BUTTON_THUMBL -> PcCore.L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> PcCore.R3
        KeyEvent.KEYCODE_BUTTON_START -> PcCore.START
        KeyEvent.KEYCODE_BUTTON_SELECT -> PcCore.SELECT
        else -> -1
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !companionBack.back()) {
                val now = SystemClock.uptimeMillis()
                if (now - lastBack < 2000 || !started) closeGame() else {
                    lastBack = now
                    android.widget.Toast.makeText(this, tr("Press BACK again to close the game"), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return true
        }
        val bit = bitFor(event.keyCode)
        if (bit < 0) return super.dispatchKeyEvent(event)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> keyMask = keyMask or (1 shl bit)
            KeyEvent.ACTION_UP -> keyMask = keyMask and (1 shl bit).inv()
        }
        PcCore.pcInput(keyMask or stickMask)
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK || event.action != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(event)
        }
        val x = event.getAxisValue(MotionEvent.AXIS_X).takeIf { kotlin.math.abs(it) > 0.5f } ?: event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y).takeIf { kotlin.math.abs(it) > 0.5f } ?: event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        var m = 0
        if (x < -0.5f) m = m or (1 shl PcCore.LEFT)
        if (x > 0.5f) m = m or (1 shl PcCore.RIGHT)
        if (y < -0.5f) m = m or (1 shl PcCore.UP)
        if (y > 0.5f) m = m or (1 shl PcCore.DOWN)
        stickMask = m
        PcCore.pcInput(keyMask or stickMask)
        return true
    }

    // ---- what's drawn over the game and on the second screen -----------------------------------------------------

    @Composable
    private fun Notice() {
        val text = notice ?: return
        Box(Modifier.fillMaxSize()) {
            AppBackdrop()
            OptionTitleWindow(text, rememberGbaTextMetrics(), Modifier.align(Alignment.Center).padding(32.dp), titleMaxLines = 3)
        }
    }
}
