package com.aura.player

import com.aura.player.ui.glass.Spr
import com.aura.player.ui.glass.rubberBand
import com.aura.player.ui.glass.stretchAxes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilsTest {
    @Test fun formatsMinutesAndSeconds() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("3:05", formatDuration(185_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
        assertEquals("0:00", formatDuration(-5000))
    }

    @Test fun greetingMatchesHour() {
        assertEquals("Good morning", greetingFor(8))
        assertEquals("Good afternoon", greetingFor(14))
        assertEquals("Good evening", greetingFor(20))
        assertEquals("Late night", greetingFor(2))
    }

    @Test fun movedReordersAndIgnoresInvalid() {
        val l = listOf(1, 2, 3, 4)
        assertEquals(listOf(2, 1, 3, 4), l.moved(1, 0))
        assertEquals(listOf(1, 3, 4, 2), l.moved(1, 3))
        assertSame(l, l.moved(-1, 2))
        assertSame(l, l.moved(2, 2))
        assertSame(l, l.moved(0, 9))
    }

    @Test fun albumKeyPrefersMediaStoreId() {
        assertEquals("ms:42", albumKeyOf(42, "A", "B"))
        assertEquals("saf:a|b", albumKeyOf(0, "A", "B"))
    }

    @Test fun cleanTagFallsBack() {
        assertEquals("Unknown", cleanTag(null, "Unknown"))
        assertEquals("Unknown", cleanTag("<unknown>", "Unknown"))
        assertEquals("Song", cleanTag("  Song ", "Unknown"))
    }

    @Test fun trackNumberStripsDiscPrefix() {
        assertEquals(5, normalizeTrackNumber(5))
        assertEquals(7, normalizeTrackNumber(2007))
    }

    @Test fun hueIsStableAndInRange() {
        assertEquals(hueFor("abc"), hueFor("abc"), 0f)
        assertNotEquals(hueFor("abc"), hueFor("abd"), 0f)
        assertTrue(hueFor("anything") in 0f..360f)
        assertTrue(hueFor("") in 0f..360f)
    }

    @Test fun rubberBandBoundsAndSignPreserved() {
        assertEquals(0f, rubberBand(0f, 100f), 0.0001f)
        assertEquals(0f, rubberBand(50f, 0f), 0.0001f)
        val pos = rubberBand(80f, 200f, 0.55f)
        val neg = rubberBand(-80f, 200f, 0.55f)
        assertTrue(pos > 0f && pos <= 200f * 0.55f)
        assertEquals(-pos, neg, 0.0001f)
    }

    @Test fun stretchAxesDeformsAnisotropically() {
        val (sx0, sy0) = stretchAxes(0f, horizontal = true)
        assertEquals(1f, sx0, 0.0001f)
        assertEquals(1f, sy0, 0.0001f)

        val (sx, sy) = stretchAxes(1f, horizontal = true)
        assertTrue(sx > 1f)
        assertTrue(sy < 1f && sy >= 0.84f)

        val (vx, vy) = stretchAxes(1f, horizontal = false)
        assertEquals(sy, vx, 0.0001f)
        assertEquals(sx, vy, 0.0001f)
    }

    @Test fun springPresetsAreValidAndDistinct() {
        Spr.entries.forEach { preset ->
            assertTrue(preset.damping in 0.3f..1.0f)
            assertTrue(preset.stiffness >= 200f)
        }
        assertTrue(Spr.ButtonRelease.damping < Spr.ButtonPress.damping)
        assertTrue(Spr.Favorite.damping < Spr.Navigation.damping)
    }
}
