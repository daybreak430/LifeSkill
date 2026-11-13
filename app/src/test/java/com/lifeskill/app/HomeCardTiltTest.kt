package com.lifeskill.app

import androidx.compose.ui.geometry.Offset
import com.lifeskill.app.ui.homeTiltAngles
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCardTiltTest {
    private val flat = pitch(0.0)

    @Test fun uprightBaselineAndSmallHandMovementsStayFlat() {
        val neutral = pitch(70.0)
        assertEquals(Offset.Zero, homeTiltAngles(neutral, neutral))
        assertEquals(Offset.Zero, homeTiltAngles(neutral, pitch(71.0)))
        assertEquals(2.856f, homeTiltAngles(neutral, pitch(78.0)).x, .001f)
        assertEquals(-2.856f, homeTiltAngles(neutral, pitch(62.0)).x, .001f)
    }

    @Test fun tiltFollowsScreenAxesAndIsCappedAcrossBothAxes() {
        val landscape = floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        assertEquals(Offset.Zero, homeTiltAngles(flat, landscape))
        val angles = homeTiltAngles(landscape, pitch(10.0))
        assertEquals(0f, angles.x, .001f)
        assertEquals(-3.696f, angles.y, .001f)

        val diagonal = floatArrayOf(.8535534f, -.1464466f, .5f, -.1464466f, .8535534f, .5f, -.5f, -.5f, .7071068f)
        assertEquals(5f, homeTiltAngles(flat, diagonal).getDistance(), .001f)
    }

    private fun pitch(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        return floatArrayOf(1f, 0f, 0f, 0f, cosine, -sine, 0f, sine, cosine)
    }
}
