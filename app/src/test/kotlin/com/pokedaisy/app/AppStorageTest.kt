package com.pokedaisy.app

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStorageTest {
    private val root = Files.createTempDirectory("pokedaisy-storage").toFile()
    private val internal = File(root, "files")
    @After fun clean() { root.deleteRecursively() }

    @Test fun aWorkingExternalFolderIsUsed() {
        val external = File(root, "external/files")
        assertEquals(external, AppStorage.choose(external, internal))
        assertTrue(external.isDirectory)
        assertFalse(File(internal, AppStorage.MARKER).exists())
    }

    /** The RG Rotate: Android hands back a path it couldn't make. filesDir then, and still once the folder works. */
    @Test fun aFolderThatCantBeMadeFallsBackForGood() {
        val blocker = File(root, "Android").apply { writeText("x") }   // a file where the folder would go
        val external = File(blocker, "data/files")
        assertEquals(internal, AppStorage.choose(external, internal))
        assertTrue(File(internal, AppStorage.MARKER).isFile)
        val working = File(root, "external/files").apply { mkdirs() }
        assertEquals(internal, AppStorage.choose(working, internal))
    }

    /** No external storage at all: filesDir for now, but nothing kept, so the external folder comes back with it. */
    @Test fun noExternalStorageIsNotKept() {
        assertEquals(internal, AppStorage.choose(null, internal))
        assertFalse(File(internal, AppStorage.MARKER).exists())
        val external = File(root, "external/files")
        assertEquals(external, AppStorage.choose(external, internal))
    }
}
