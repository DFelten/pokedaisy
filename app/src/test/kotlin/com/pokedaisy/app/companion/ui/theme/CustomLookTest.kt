package com.pokedaisy.app.companion.ui.theme

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CustomLookTest {
    @Test fun oneColorMakesAWholeTheme() {
        val t = CustomThemes.parse("""{ "name": "Midnight", "color": "#3D6FE0" }""", 1000)
        assertEquals(1000, t.id)
        assertEquals("MIDNIGHT", t.label)
        assertEquals(Color(0xFF3D6FE0), t.value)
        assertEquals(hueOf(Color(0xFF3D6FE0)), t.hue!!, 0.01f)
        assertEquals(0.5f, t.wash, 0f)
        assertEquals(3, t.blobs.size)
        assertFalse(t.darkPage)
        assertTrue(t.gameWash != null)
    }

    @Test fun everyFieldCanBeSet() {
        val t = CustomThemes.parse(
            """{ "name": "Night Sky", "color": "3d6fe0", "darkColor": "#8AB4FF", "tint": 2, "page": "#101828",
                "darkPage": true, "glows": ["#223355", "#552244"], "gameTint": null }""",
            1001,
        )
        assertEquals(Color(0xFF8AB4FF), t.valueNight)
        assertEquals(1f, t.wash, 0f)
        assertEquals(Color(0xFF101828), t.page)
        assertTrue(t.darkPage)
        assertEquals(listOf(Color(0xFF223355), Color(0xFF552244)), t.blobs)
        assertNull(t.gameWash)
    }

    @Test fun badFilesSayWhy() {
        fun reason(text: String): String = try { CustomThemes.parse(text, 1000); fail("parsed: $text"); "" } catch (e: LookImportError) { e.en }
        assertEquals("Not a theme file: it isn't JSON", reason("name = MIDNIGHT"))
        assertEquals("The theme has no name", reason("""{ "color": "#3D6FE0" }"""))
        assertEquals("The theme has no color", reason("""{ "name": "X" }"""))
        assertEquals("\"{0}\" isn't a color like #3D6FE0", reason("""{ "name": "X", "color": "blue" }"""))
    }

    /** The app's own fonts read as fonts, their capitals as tall as AppFont says. */
    @Test fun fontFilesAreRead() {
        val dir = File("src/main/assets/fonts")
        val nunito = File(dir, "Nunito-SemiBold.ttf").readBytes()
        assertTrue(FontFileInfo.isFont(nunito))
        assertEquals(".ttf", FontFileInfo.extension(nunito))
        assertEquals(AppFont.MODERN.capHeight, FontFileInfo.capHeight(nunito), 0.01f)
        val atkinson = File(dir, "AtkinsonHyperlegible-Regular.ttf").readBytes()
        assertEquals(AppFont.SMOOTH.capHeight, FontFileInfo.capHeight(atkinson), 0.01f)
        assertFalse(FontFileInfo.isFont("not a font at all".encodeToByteArray()))
    }

    /** Every font check (LookLimits, 2026-11-23): a cut-off file, one with no letters and one too big are refused. */
    @Test fun brokenFontsAreRefused() {
        val nunito = File("src/main/assets/fonts/Nunito-SemiBold.ttf").readBytes()
        assertNull(FontFileInfo.problem(nunito))
        assertEquals("That font file is damaged", FontFileInfo.problem(nunito.copyOf(nunito.size / 2)))
        // Its outline tables' tags renamed: a font with nothing to draw.
        val noGlyphs = nunito.copyOf().also { b ->
            val n = ((b[4].toInt() and 0xFF) shl 8) or (b[5].toInt() and 0xFF)
            for (i in 0 until n) {
                val at = 12 + i * 16
                val tag = String(b, at, 4, Charsets.ISO_8859_1)
                if (tag == "glyf" || tag == "CFF ") "zzzz".forEachIndexed { j, c -> b[at + j] = c.code.toByte() }
            }
        }
        assertEquals("That font has no letters in it", FontFileInfo.problem(noGlyphs))
        try { FontFileInfo.check(ByteArray(LookLimits.FONT_BYTES + 1)); fail("too big") } catch (e: LookImportError) { assertEquals("That font is too big ({0} at most)", e.en) }
    }

    /** A picture's kind and motion by its bytes, never its name: a two-frame GIF moves, a one-frame one doesn't. */
    @Test fun picturesAreToldByTheirBytes() {
        fun gif(frames: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write("GIF89a".toByteArray()); out.write(byteArrayOf(16, 0, 9, 0, 0, 0, 0))
            repeat(frames) {
                out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0, 10, 0, 0, 0))
                out.write(byteArrayOf(0x2C, 0, 0, 0, 0, 16, 0, 9, 0, 0, 2, 2, 0x4C, 0x01, 0))
            }
            out.write(0x3B)
            return out.toByteArray()
        }
        assertEquals(PictureKind.GIF, PictureKind.of(gif(1)))
        assertFalse(PictureMotion.isAnimated(gif(1)))
        assertTrue(PictureMotion.isAnimated(gif(2)))
        assertEquals(2, PictureMotion.gifFrames(gif(2)))
        assertEquals(16 to 9, PictureMotion.size(gif(2)))
        assertNull(PictureKind.of("{ \"name\": \"x\" }".encodeToByteArray()))
        try { LookLimits.checkPictureSize(8000, 8000, animated = true); fail("too large") } catch (e: LookImportError) { assertTrue(e.en.startsWith("That picture is too large")) }
        try { LookLimits.checkPictureSize(4, 4, animated = false); fail("too small") } catch (e: LookImportError) { assertTrue(e.en.startsWith("That picture is too small")) }
    }

    /** EDIT THEME's draft: its JSON says the same theme back, and a new colour carries what was worked out from the old one off. */
    @Test fun draftsRoundTrip() {
        val d = ThemeDraft.of("""{ "name": "Night Sky", "color": "#3D6FE0", "tint": 0.7, "darkPage": true, "gameTint": null, "fontFile": "Night.ttf" }""")
        assertEquals(d, ThemeDraft.of(d.toJson()))
        assertEquals("NIGHT SKY", d.name)
        val red = d.copy(page = Color(0xFF101010)).recolored(Color(0xFFD23A3A))
        assertNull(red.page)
        assertTrue(red.darkPage)
        assertEquals("#D23A3A", ThemeDraft.hex(red.color))
        assertNull(ThemeDraft.cleanName("  |  "))
        // TEXT COLOR / SECOND COLOR: kept in the file; dark mode's text worked out from the light one; the second colour's hue.
        val two = ThemeDraft.of("""{ "name": "Sunset", "color": "#8A4FD0", "secondColor": "#E8743C", "textColor": "#1F2E5C" }""")
        assertEquals(two, ThemeDraft.of(two.toJson()))
        val spec = two.spec(1000)
        assertEquals(Color(0xFF1F2E5C), spec.text)
        assertTrue(spec.textNight!!.red > 0.6f)
        assertEquals(hueOf(Color(0xFFE8743C)), spec.accentHue!!, 0.01f)
        assertNull(d.spec(1000).accentHue)
    }

    /** A theme pack in and out: its font goes among the fonts and comes back out with it; a name it can't hold is refused. */
    @Test fun themePacksGoInAndOut() {
        val dir = java.nio.file.Files.createTempDirectory("look").toFile()
        try {
            CustomLook.dir = okio.Path.Companion.run { dir.toOkioPath() }
            CustomLook.load()
            val nunito = File("src/main/assets/fonts/Nunito-SemiBold.ttf").readBytes()
            val spec = ThemeFiles.import(mapOf(ThemePack.THEME_FILE to """{ "name": "Peach", "color": "#E8743C" }""".encodeToByteArray(), "font.ttf" to nunito))
            assertEquals("PEACH", spec.label)
            assertEquals(1, CustomLook.fonts.size)
            assertTrue(ThemeFiles.hasFiles(spec.id))
            val out = ThemeFiles.export(spec.id)
            assertEquals(setOf(ThemePack.THEME_FILE, "font.ttf"), out.keys)
            assertTrue(nunito.contentEquals(out.getValue("font.ttf")))
            // In again: a second PEACH gets a number.
            assertEquals("PEACH 2", ThemeFiles.import(out).label)
            try { ThemeFiles.import(mapOf(ThemePack.THEME_FILE to out.getValue(ThemePack.THEME_FILE), "run.sh" to ByteArray(1))); fail("took run.sh") }
            catch (e: LookImportError) { assertEquals("A theme pack can't hold \"{0}\"", e.en) }
            // What's kept is read back the same at the next start.
            CustomLook.load()
            assertEquals(listOf("PEACH", "PEACH 2"), CustomLook.themes.map { it.label })
        } finally {
            CustomLook.dir = null
            CustomLook.load()
            dir.deleteRecursively()
        }
    }
}
