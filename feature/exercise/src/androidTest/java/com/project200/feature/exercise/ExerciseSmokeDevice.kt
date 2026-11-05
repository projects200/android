package com.project200.feature.exercise

import android.app.Activity
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

internal class ExerciseSmokeDevice {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val power = checkNotNull(instrumentation.targetContext.getSystemService(PowerManager::class.java))
    private val keyguard = checkNotNull(instrumentation.targetContext.getSystemService(KeyguardManager::class.java))

    fun preparationRule(): TestRule =
        TestRule { base, description ->
            object : Statement() {
                override fun evaluate() {
                    capture(description, "before-device-preparation")
                    try {
                        check(!keyguard.isDeviceSecure && !keyguard.isKeyguardSecure) {
                            "The isolated smoke device must not have a secure keyguard"
                        }
                        shell("input keyevent KEYCODE_WAKEUP")
                        shell("wm dismiss-keyguard")
                        awaitReady("an interactive device without keyguard") { deviceReady() }
                        capture(description, "after-device-preparation")
                    } catch (failure: Throwable) {
                        capture(description, "device-preparation-failure")
                        throw failure
                    }
                    base.evaluate()
                }
            }
        }

    fun focusedWindowRule(activityProvider: () -> Activity): TestRule =
        TestRule { base, description ->
            object : Statement() {
                override fun evaluate() {
                    var activity: Activity? = null
                    try {
                        val host = activityProvider()
                        activity = host
                        instrumentation.runOnMainSync {
                            host.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        capture(description, "before-window-readiness", host)
                        // Do not enter Espresso until the actual host window can receive input.
                        awaitReady("the test activity decor window to have focus and complete layout") {
                            var windowReady = false
                            instrumentation.runOnMainSync {
                                val decor = host.window.decorView
                                windowReady = decor.isAttachedToWindow && decor.hasWindowFocus() && !decor.isLayoutRequested
                            }
                            deviceReady() && windowReady
                        }
                        capture(description, "window-ready", host)
                        base.evaluate()
                    } catch (failure: Throwable) {
                        // This rule is inside Compose/ActivityScenario, so the surface still exists here.
                        capture(description, "failure-before-activity-teardown", activity, screenshot = true)
                        throw failure
                    }
                }
            }
        }

    private fun deviceReady(): Boolean = power.isInteractive && !keyguard.isKeyguardLocked && !keyguard.isDeviceLocked

    private fun awaitReady(
        condition: String,
        ready: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + READINESS_TIMEOUT_MS
        while (!ready()) {
            check(SystemClock.uptimeMillis() < deadline) { "Smoke preparation timed out waiting for $condition" }
            // Poll device/window state, not an unconditional delay or a retry of a failed assertion.
            SystemClock.sleep(50)
        }
    }

    private fun capture(
        description: Description,
        phase: String,
        activity: Activity? = null,
        screenshot: Boolean = false,
    ) {
        val prefix = "exercise-retry-surface-${description.methodName}-$phase"
        bestEffort(phase) {
            var activityState = "activity=not-launched"
            if (activity != null) {
                instrumentation.runOnMainSync {
                    val decor = activity.window.decorView
                    activityState =
                        "activity=${activity.componentName.flattenToShortString()} finishing=${activity.isFinishing} " +
                        "destroyed=${activity.isDestroyed} attached=${decor.isAttachedToWindow} " +
                        "windowFocus=${decor.hasWindowFocus()} layoutRequested=${decor.isLayoutRequested} " +
                        "visibility=${decor.visibility} size=${decor.width}x${decor.height}"
                }
            }
            val state =
                "test=${description.methodName} phase=$phase uptimeMs=${SystemClock.uptimeMillis()}\n" +
                    "interactive=${power.isInteractive} keyguardLocked=${keyguard.isKeyguardLocked} " +
                    "deviceLocked=${keyguard.isDeviceLocked} deviceSecure=${keyguard.isDeviceSecure} " +
                    "keyguardSecure=${keyguard.isKeyguardSecure}\n$activityState\n"
            Log.i(TAG, state)
            evidenceFile("$prefix-state.txt").writeText(state)
        }
        bestEffort(phase) {
            val windows =
                selectedFacts(
                    "dumpsys window windows",
                    listOf("mCurrentFocus=", "mFocusedApp=", "mTopFocusedDisplayId="),
                )
            val activities =
                selectedFacts(
                    "dumpsys activity activities",
                    listOf("topResumedActivity=", "mResumedActivity:", "ResumedActivity:"),
                )
            evidenceFile("$prefix-system.txt").writeText("test=${description.methodName} phase=$phase\n$windows\n$activities\n")
        }
        if (screenshot && activity != null) {
            bestEffort(phase) {
                val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "No device screenshot returned" }
                try {
                    evidenceFile("$prefix.png").outputStream().use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Screenshot encoding failed" }
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun selectedFacts(
        command: String,
        prefixes: List<String>,
    ): String =
        shell(command)
            .lineSequence()
            .map { it.trim() }
            .filter { line -> prefixes.any { line.startsWith(it) } }
            .joinToString("\n")

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { input ->
            input.bufferedReader().use { it.readText() }
        }

    private fun evidenceFile(name: String): File {
        // AGP pulls this directory before uninstalling the library test APK, even on test failure.
        val directory =
            File(
                checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")) {
                    "AGP must provide additionalTestOutputDir for smoke evidence"
                },
            )
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create smoke evidence directory" }
        return File(directory, name)
    }

    private fun bestEffort(
        phase: String,
        action: () -> Unit,
    ) {
        runCatching(action).onFailure { failure ->
            // Diagnostic errors must never replace the preparation or UI assertion exception.
            runCatching { Log.w(TAG, "phase=$phase diagnosticFailure=${failure.javaClass.name}") }
        }
    }

    private companion object {
        const val TAG = "ExerciseSmokeDevice"
        const val READINESS_TIMEOUT_MS = 10_000L
    }
}
