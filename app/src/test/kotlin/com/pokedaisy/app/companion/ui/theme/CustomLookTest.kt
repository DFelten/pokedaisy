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
}
