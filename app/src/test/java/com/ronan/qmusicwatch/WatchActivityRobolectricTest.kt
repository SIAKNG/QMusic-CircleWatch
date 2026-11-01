package com.ronan.qmusicwatch

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35], qualifiers = "w240dp-h240dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class WatchActivityRobolectricTest {
    @Test fun manifestDeclaresNotificationPermissionForMediaControls() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        val permissions = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
        assertTrue(Manifest.permission.REQUEST_INSTALL_PACKAGES in permissions)
        assertTrue(Manifest.permission.VIBRATE in permissions)
        controller.destroy()
    }

    @Test fun nativeCrownMotionIsNormalizedAndDispatchedOnlyOnce() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        var calls = 0
        var pixels = 0f
        val rotaryHandler: (Float, Long) -> Boolean = { value, _ -> calls++; pixels = value; true }
        activity.setHardwareRotaryHandler(rotaryHandler)
        val properties = android.view.MotionEvent.PointerProperties().apply { id = 0 }
        val coords = android.view.MotionEvent.PointerCoords().apply {
            setAxisValue(android.view.MotionEvent.AXIS_VSCROLL, 1f)
        }
        val event = android.view.MotionEvent.obtain(1000, 1000, android.view.MotionEvent.ACTION_SCROLL,
            1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_MOUSE, 0)
        try {
            assertTrue(activity.dispatchGenericMotionEvent(event))
            assertEquals(1, calls)
            assertTrue("Activity fallback must use Compose's pixel direction", pixels < 0f)
            activity.clearHardwareRotaryHandler(rotaryHandler)
            var hiddenCalls = 0
            activity.registerListCrownHandler({ _, _ -> hiddenCalls++; true }) { false }
            activity.registerListCrownHandler({ value, _ -> calls++; pixels = value; true }) { true }
            assertTrue(activity.dispatchGenericMotionEvent(event))
            assertEquals("Visible list must handle native mouse-wheel crowns without keyboard focus", 2, calls)
            assertEquals("Offscreen pages must not move", 0, hiddenCalls)
            assertTrue(pixels < 0f)
        } finally { event.recycle(); controller.destroy() }
    }

    @Test fun mainActivityCreatesAndLaysOutOnA480SquareDisplay() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        try {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertFalse(activity.isFinishing)
            assertTrue("Compose root was not attached", content.childCount > 0)
            assertEquals(480, activity.resources.displayMetrics.widthPixels)
            assertEquals(480, activity.resources.displayMetrics.heightPixels)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(sdk = [30, 35], qualifiers = "w233dp-h233dp-round-xhdpi")
    fun mainActivityAlsoCreatesOnAndroid11AndRoundDisplays() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        try {
            assertTrue(controller.get().resources.configuration.isScreenRound)
            assertFalse(controller.get().isFinishing)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h320dp-mdpi")
    fun mainActivityAlsoLaysOutOnA320DpSquareDisplay() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        try {
            assertEquals(320, controller.get().resources.displayMetrics.widthPixels)
            assertEquals(320, controller.get().resources.displayMetrics.heightPixels)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
