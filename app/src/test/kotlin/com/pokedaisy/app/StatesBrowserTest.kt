package com.pokedaisy.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatesBrowserTest {
    private fun dir() = Files.createTempDirectory("states").toFile()

    private fun state(game: File, name: String, at: Long, bytes: Int = 10) {
        game.mkdirs()
        File(game, name).apply { writeBytes(ByteArray(bytes)); setLastModified(at) }
        File(game, "$name.png").apply { writeBytes(ByteArray(3)); setLastModified(at) }
    }

    @Test fun listsGamesByTheirStatesNewestFirstAndSkipsOtherFolders() {
        val root = dir()
        state(File(root, "0123abcd"), "ss1", 1_000_000)
        state(File(root, "0123abcd"), "resume", 3_000_000)
        File(root, "0123abcd/game.txt").writeText("Old Game")
        state(File(root, "89abcdef"), "battle_retry", 5_000_000)
        File(root, "fedcba98").mkdirs()                  // no states: left out
        state(File(root, "not-a-game"), "ss1", 9_000_000) // not a key: left out
        val games = StatesBrowser.list(root, mapOf("89abcdef" to File("/roms/x.gba"))) { "Library Name" }
        assertEquals(listOf("89abcdef", "0123abcd"), games.map { it.key })
        assertEquals("Library Name", games[0].name)
        assertEquals("Old Game", games[1].name)
        assertEquals(listOf(StatesBrowser.RESUME, 1), games[1].states.map { it.slot })
        assertEquals(File(root, "0123abcd/resume.png"), games[1].cover)
    }

    @Test fun moveKeepsTheNewerCopyAndEmptiesTheOldFolder() {
        val from = dir(); val to = dir()
        state(File(from, "0123abcd"), "ss0", 2_000_000, bytes = 20)
        state(File(from, "0123abcd"), "ss1", 1_000_000, bytes = 20)
        state(File(to, "0123abcd"), "ss1", 4_000_000, bytes = 7)    // newer there: kept
        File(from, "notes").mkdirs()
        val moved = StatesBrowser.move(from, to)
        assertEquals(0, moved.failed)
        assertEquals(20L, File(to, "0123abcd/ss0").length())
        assertEquals(7L, File(to, "0123abcd/ss1").length())
        assertEquals(2_000_000L, File(to, "0123abcd/ss0").lastModified())
        assertFalse(File(from, "0123abcd").exists())
        assertTrue(File(from, "notes").isDirectory)
    }
}
