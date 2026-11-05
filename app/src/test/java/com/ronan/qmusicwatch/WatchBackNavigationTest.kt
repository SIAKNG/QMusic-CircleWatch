package com.ronan.qmusicwatch

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.ronan.qmusicwatch.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w233dp-h233dp-round-xhdpi", application = android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class WatchBackNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController
    private lateinit var activity: ComponentActivity
    private var exits = 0
    private fun setup() = compose.setContent {
        nav = rememberNavController()
        activity = LocalActivity.current as ComponentActivity
        val back = rememberWatchBackAction(nav) { exits++ }
        val density = LocalDensity.current
        Box(Modifier.fillMaxSize().testTag("navigation-host").watchSwipeBack(true,
            with(density) { 54.dp.toPx() }, with(density) { 48.dp.toPx() }, back)) {
            NavHost(nav, "home", enterTransition = { WatchNavigationMotion.enter() },
                exitTransition = { WatchNavigationMotion.exit(this) },
                popEnterTransition = { WatchNavigationMotion.popEnter() },
                popExitTransition = { WatchNavigationMotion.popExit() }) {
                watchDestination("home") { Text("主界面") }
                watchDestination("created") { Text("创建的歌单") }
                watchDestination("detail") { Text("歌单详情") }
            }
        }
    }
    private fun edgeSwipe() {
        compose.onNodeWithTag("navigation-host").performTouchInput {
            swipe(Offset(2f, height * .5f), Offset(width * .8f, height * .5f), 260)
        }
        compose.waitForIdle()
    }
    @Test fun edgeSwipePopsOnePageAtATimeAndOnlyExitsAtHome() {
        setup()
        compose.runOnIdle { nav.navigate("created"); nav.navigate("detail") }
        compose.waitForIdle()
        edgeSwipe()
        compose.runOnIdle { assertEquals("created", nav.currentDestination?.route); assertEquals(0, exits) }
        edgeSwipe()
        compose.runOnIdle { assertEquals("home", nav.currentDestination?.route); assertEquals(0, exits) }
        edgeSwipe()
        compose.runOnIdle { assertEquals(1, exits) }
    }
    @Test fun systemBackPopsTheInAppStackAndThemeDoesNotDismissTheActivity() {
        setup()
        compose.runOnIdle { nav.navigate("created"); nav.navigate("detail") }
        compose.waitForIdle()
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("created", nav.currentDestination?.route)
            assertFalse(activity.isFinishing)
            assertEquals(0, exits)
            val context = android.view.ContextThemeWrapper(activity, R.style.Theme_QMusicWatch)
            val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowSwipeToDismiss))
            try { assertFalse(attrs.getBoolean(0, true)) } finally { attrs.recycle() }
        }
    }
    @Test fun backCanInterruptEntryWithoutWaitingForTheAnimation() {
        setup()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { nav.navigate("created"); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { nav.navigate("detail") { launchSingleTop = true }; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("created", nav.currentDestination?.route); assertEquals(0, exits) }
        edgeSwipe()
        compose.runOnIdle { assertEquals("home", nav.currentDestination?.route) }
    }
}
