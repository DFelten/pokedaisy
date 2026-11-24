package com.pokedaisy.app

/** Stand-ins for PC fan games' list and activity (a prototype behind FeatureFlags.pcGames, off in the preview). */
object PcGames {
    class Game(val title: String, val ini: java.io.File)
    fun list(context: android.content.Context): List<Game> = emptyList()
}

class PcGameActivity : androidx.activity.ComponentActivity() {
    companion object {
        const val EXTRA_GAME = "game"
    }
}
