package com.wdesign.studio

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens every main screen through its deep link and taps every clickable element one by one,
 * re-opening the screen before each tap. Intents that leave the app (file pickers, share sheet,
 * Themes app, system settings) are stubbed, so the test stays inside W Design.
 *
 * Any crash in the app kills the instrumented process, which `am instrument` reports as
 * "Process crashed" and the CI smoke job fails on. Harness-only problems (a node that scrolled
 * away or disappeared) are skipped, never treated as success for a crash.
 */
@RunWith(AndroidJUnit4::class)
class TapEverythingTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val routes = listOf("home", "discover", "create", "designs", "settings", "xiaomilab", "lockscreen-demo", "mtz")
    private val maxTapsPerScreen = 30

    @Before fun stubExternalIntents() {
        Intents.init()
        intending(not(isInternal())).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
    }

    @After fun release() { Intents.release() }

    private fun open(route: String): ActivityScenario<MainActivity> {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("wdesign://open/$route")).setClass(ctx, MainActivity::class.java)
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        compose.waitForIdle()
        Thread.sleep(600) // let async previews and the device snapshot arrive
        compose.waitForIdle()
        return scenario
    }

    @Test fun everyScreenOpens() {
        for (route in routes) {
            open(route).use { s ->
                assertTrue("$route did not open", s.state.isAtLeast(Lifecycle.State.STARTED))
            }
        }
    }

    @Test fun everyButtonOnEveryScreenIsSafe() {
        var taps = 0
        for (route in routes) {
            val count = open(route).use { compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size }
            val limit = minOf(count, maxTapsPerScreen)
            Log.i("TapEverything", "$route: $count clickable nodes, tapping $limit")
            for (i in 0 until limit) {
                open(route).use { s ->
                    val nodes = compose.onAllNodes(hasClickAction())
                    if (i < nodes.fetchSemanticsNodes().size) {
                        val node = nodes[i]
                        try {
                            runCatching { node.performScrollTo() }
                            node.performClick()
                            taps++
                        } catch (e: AssertionError) {
                            Log.w("TapEverything", "$route #$i skipped: ${e.message?.take(120)}")
                        }
                        compose.waitForIdle()
                        Thread.sleep(400) // give background work a chance to fail loudly
                        compose.waitForIdle()
                    }
                    assertTrue("$route #$i: activity is gone after the tap", s.state.isAtLeast(Lifecycle.State.STARTED))
                }
            }
        }
        Log.i("TapEverything", "tapped $taps elements without a crash")
        assertTrue("no element was tapped at all", taps > 0)
    }
}
