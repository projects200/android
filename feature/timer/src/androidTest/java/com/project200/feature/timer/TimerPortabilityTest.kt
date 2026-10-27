package com.project200.feature.timer

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project200.domain.model.CustomTimer
import com.project200.presentation.compose.theme.AppTheme
import com.project200.undabang.feature.timer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class TimerPortabilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun narrowAndWideDialogsKeepBothRealInputsOutsideTheDivider() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, androidx.appcompat.R.style.Theme_AppCompat_Light)
            for (screenWidthDp in listOf(320, 600)) {
                val root = LayoutInflater.from(context).inflate(R.layout.dialog_time_picker, null)
                val width = (screenWidthDp * context.resources.displayMetrics.density * 0.85f).roundToInt()
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                val minute = root.findViewById<EditText>(R.id.minute_et)
                val second = root.findViewById<EditText>(R.id.second_et)
                val divider = root.findViewById<View>(R.id.time_divider)
                assertTrue(minute.right <= divider.left)
                assertTrue(divider.right <= second.left)
                assertTrue(minute.width - minute.paddingLeft - minute.paddingRight >= minute.paint.measureText("59"))
                assertTrue(second.width - second.paddingLeft - second.paddingRight >= second.paint.measureText("59"))
            }
        }
    }

    @Test
    fun customCardKeepsItsLocalIdentityWhenRenamedAndAssignedAServerId() {
        val owned = mutableStateOf(CustomTimer(localId = "owned", name = "Before"))
        var selected: String? = null
        compose.setContent {
            AppTheme {
                TimerListScreen(
                    customTimers = listOf(CustomTimer(localId = "other", name = "Before"), owned.value),
                    onBackClick = {},
                    onSimpleTimerClick = {},
                    onCustomTimerClick = { selected = it.localId },
                    onAddCustomTimerClick = {},
                )
            }
        }
        compose.onNodeWithTag("timer_list_scroll").performScrollToNode(hasTestTag("custom_timer_owned"))
        compose.onNodeWithTag("custom_timer_owned").assertTextEquals("Before").performClick()
        compose.runOnIdle {
            assertEquals("owned", selected)
            owned.value = owned.value.copy(name = "After", serverId = 81)
        }
        compose.onAllNodesWithTag("custom_timer_owned").assertCountEquals(1)
        compose.onNodeWithTag("custom_timer_owned").assertTextEquals("After").performClick()
        compose.runOnIdle { assertEquals("owned", selected) }
    }

    @Test
    fun listAnchorsBelongToTheScrollableContentAndReachTheActualEnd() {
        compose.setContent {
            AppTheme {
                TimerListScreen(
                    customTimers = (1..40).map { CustomTimer(localId = it.toString(), name = "Timer $it") },
                    onBackClick = {},
                    onSimpleTimerClick = {},
                    onCustomTimerClick = {},
                    onAddCustomTimerClick = {},
                )
            }
        }
        compose.onNodeWithTag("simple_timer_card").assertIsDisplayed()
        compose.onNodeWithTag("timer_list_scroll").performScrollToNode(hasTestTag("custom_timer_20"))
        compose.onNodeWithTag("custom_timer_20").assertIsDisplayed()
        compose.onNodeWithTag("timer_list_scroll").performScrollToNode(hasTestTag("custom_timer_add_card"))
        compose.onNodeWithTag("custom_timer_add_card").assertIsDisplayed()
        compose.onNodeWithTag("timer_list_scroll").performScrollToNode(hasTestTag("simple_timer_card"))
        compose.onNodeWithTag("simple_timer_card").assertIsDisplayed()
    }
}
