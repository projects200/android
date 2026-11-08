package com.project200.feature.exercise

import android.app.Activity
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

internal class ExerciseSmokeDevice {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val power = checkNotNull(instrumentation.targetContext.getSystemService(PowerManager::class.java))
    private val keyguard = checkNotNull(instrumentation.targetContext.getSystemService(KeyguardManager::class.java))

    fun focusedWindowRule(activityProvider: () -> Activity): TestRule =
        TestRule { base, description ->
            object : Statement() {
                override fun evaluate() {
                    var activity: Activity? = null
                    try {
                        val host = activityProvider()
                        activity = host
                        capture(description, "before-window-readiness", host)
                        // Do not enter Espresso until the actual host window can receive input.
                        awaitReady("the test activity decor window to have focus and complete layout") {
                            var windowReady = false
                            instrumentation.runOnMainSync {
                                val decor = host.window.decorView
                                windowReady = decor.isAttachedToWindow && decor.hasWindowFocus() && !decor.isLayoutRequested
                            }
                            windowReady
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

    private fun awaitReady(
        condition: String,
        ready: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + READINESS_TIMEOUT_MS
        while (!ready()) {
            check(SystemClock.uptimeMillis() < deadline) { "Smoke window readiness timed out waiting for $condition" }
            // 실패한 assertion을 재시도하거나 무조건 지연하지 않고 실제 창 상태를 확인합니다
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
                    "testPackage=${instrumentation.context.packageName} " +
                    "targetSdk=${instrumentation.context.applicationInfo.targetSdkVersion}\n" +
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
            // 진단 오류가 창 준비나 UI assertion의 원래 예외를 대체하지 않도록 합니다
            runCatching { Log.w(TAG, "phase=$phase diagnosticFailure=${failure.javaClass.name}") }
        }
    }

    private companion object {
        const val TAG = "ExerciseSmokeDevice"
        const val READINESS_TIMEOUT_MS = 10_000L
    }
}
