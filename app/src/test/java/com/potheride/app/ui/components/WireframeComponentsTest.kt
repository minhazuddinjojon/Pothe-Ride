package com.potheride.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.potheride.app.ui.theme.PotheRideTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Render tests for the components transcribed from the wireframes.
 *
 * These run on the JVM through Robolectric, so the UI is covered on every `test` run
 * rather than only when someone remembers to boot an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WireframeComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `stepper renders both glyphs and the current value`() {
        compose.setContent {
            PotheRideTheme {
                Stepper(value = 1, onValueChange = {}, min = 1, max = 3)
            }
        }
        // The glyph is a real minus sign, not a hyphen.
        compose.onNodeWithText("−").assertIsDisplayed()
        compose.onNodeWithText("+").assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun `stepper reports each step and stops at the ceiling`() {
        var seats = 2
        compose.setContent {
            PotheRideTheme {
                var value by remember { mutableStateOf(2) }
                Stepper(
                    value = value,
                    onValueChange = { value = it; seats = it },
                    min = 1,
                    max = 3
                )
            }
        }
        compose.onNodeWithText("+").performClick()
        compose.waitForIdle()
        assertEquals(3, seats)

        // At the ceiling the further tap must be a no-op, not a wrap or an overflow.
        compose.onNodeWithText("+").performClick()
        compose.waitForIdle()
        assertEquals(3, seats)
    }

    @Test
    fun `otp boxes show typed digits and dots for the rest`() {
        compose.setContent {
            PotheRideTheme { OtpBoxes(code = "12", length = 4) }
        }
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        // Two placeholders remain.
        compose.onAllNodesWithTextDot().let { assertTrue(it >= 2) }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextDot(): Int =
        onAllNodes(androidx.compose.ui.test.hasText("·")).fetchSemanticsNodes().size

    @Test
    fun `cta button is disabled when told to be`() {
        compose.setContent {
            PotheRideTheme { CtaButton("Get code", onClick = {}, enabled = false) }
        }
        compose.onNodeWithText("Get code").assertIsNotEnabled()
    }

    @Test
    fun `cta button fires exactly once per tap`() {
        var taps = 0
        compose.setContent {
            PotheRideTheme { CtaButton("Search rides", onClick = { taps++ }) }
        }
        compose.onNodeWithText("Search rides").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, taps)
    }

    @Test
    fun `status badge renders its label for every tone`() {
        compose.setContent {
            PotheRideTheme {
                androidx.compose.foundation.layout.Column {
                    StatusBadge("Pending review", BadgeTone.PENDING)
                    StatusBadge("Approved", BadgeTone.POSITIVE)
                    StatusBadge("Rejected", BadgeTone.NEGATIVE)
                    StatusBadge("92% overlap", BadgeTone.NEUTRAL)
                }
            }
        }
        listOf("Pending review", "Approved", "Rejected", "92% overlap").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun `upload row shows the attached filename once a document is picked`() {
        compose.setContent {
            PotheRideTheme {
                androidx.compose.foundation.layout.Column {
                    UploadRow("Upload front & back", attachedName = null, onPick = {})
                    UploadRow("Upload licence", attachedName = "licence.jpg", onPick = {})
                }
            }
        }
        compose.onNodeWithText("Upload front & back").assertIsDisplayed()
        compose.onNodeWithText("licence.jpg").assertIsDisplayed()
    }

    @Test
    fun `upload row reports the pick`() {
        var picked = false
        compose.setContent {
            PotheRideTheme {
                UploadRow("Upload licence", attachedName = null, onPick = { picked = true })
            }
        }
        compose.onNodeWithText("Upload licence").performClick()
        compose.waitForIdle()
        assertTrue(picked)
    }

    @Test
    fun `depth card renders its content and reports clicks`() {
        var opened = false
        compose.setContent {
            PotheRideTheme {
                DepthCard(onClick = { opened = true }) { Text("Search a ride") }
            }
        }
        compose.onNodeWithText("Search a ride").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertTrue(opened)
    }
}
