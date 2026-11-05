package com.project200.feature.exercise

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.canShowExerciseBodyRetry
import com.project200.feature.exercise.detail.ExerciseDetailScreen
import com.project200.feature.exercise.main.ExerciseListAdapter
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.utils.UiState
import com.project200.undabang.feature.exercise.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * Real production surfaces hosted by the library's existing Compose test activity.
 * Inputs are synthetic UI state only: no account, repository, Worker or server is used.
 * This smoke does not replace the authenticated device/server checks in #657.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseRetrySurfaceSmokeTest {
    private val device = ExerciseSmokeDevice()

    @get:Rule(order = 0)
    val devicePreparation = device.preparationRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    // Capture failures inside the activity rule, before Compose/ActivityScenario tears it down.
    @get:Rule(order = 2)
    val focusedWindow = device.focusedWindowRule { compose.activity }

    @Test
    fun normalAndPendingRowsHaveNoRetryOrNormalTransmissionText() {
        val adapter = showList(listOf(listItem(syncPending = false)))
        assertListRetryHidden()
        onView(withText("동기화 대기")).check(doesNotExist())

        compose.runOnIdle { adapter.submitList(listOf(listItem(syncPending = true))) }
        compose.waitUntil { adapter.currentList.single().syncPending }
        assertListRetryHidden()
        onView(withText("동기화 대기")).check(doesNotExist())
        onView(withId(R.id.exercise_title_tv)).check(matches(withText(TITLE)))
    }

    @Test
    fun bodyFailureIsRedAndAccessibleAndRetryClickDoesNotNavigateTheRow() {
        var retries = 0
        var navigations = 0
        val adapter =
            showList(
                items = listOf(listItem(syncPending = true)),
                onItemClicked = {
                    assertEquals(RECORD_ID, it)
                    navigations++
                },
                onRetryClick = {
                    assertEquals(RECORD_ID, it)
                    retries++
                },
            )
        updateListRetry(adapter, retryEligible())

        onView(withId(R.id.iv_body_retry))
            .check(matches(isDisplayed()))
            .check(matches(withContentDescription("기록 전송 다시 시도")))
            .check { view, error ->
                if (error != null) throw error
                val icon = view as ImageView
                val rendered = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888)
                icon.draw(Canvas(rendered))
                val pixels = IntArray(rendered.width * rendered.height)
                rendered.getPixels(pixels, 0, rendered.width, 0, 0, rendered.width, rendered.height)
                assertTrue(
                    "The visible retry affordance must contain red pixels",
                    pixels.any {
                        Color.alpha(it) > 0 && Color.red(it) > 150 &&
                            Color.red(it) > Color.green(it) * 1.3 && Color.red(it) > Color.blue(it) * 1.3
                    },
                )
                rendered.recycle()
            }
            .perform(click())
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(0, navigations)
        }
        onView(withId(R.id.exercise_list_cl)).perform(click())
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, navigations)
        }

        listOf(
            retryEligible(validatedInternet = false),
            retryEligible(workerBusy = true),
            retryEligible(authenticatedAccount = false),
            retryEligible(retryableBodyFailure = false),
        ).forEach { eligible ->
            updateListRetry(adapter, retryEligible())
            onView(withId(R.id.iv_body_retry)).check(matches(isDisplayed()))
            updateListRetry(adapter, eligible)
            assertListRetryHidden()
            onView(withId(R.id.exercise_title_tv)).check(matches(withText(TITLE)))
            onView(withText("동기화 대기")).check(doesNotExist())
        }
    }

    @Test
    fun retryOnlyUpdatesKeepTheVisibleListBodyAndScrollAnchor() {
        lateinit var recycler: RecyclerView
        val items = (0 until 40).map { listItem(recordId = "row-$it", title = "Synthetic row $it") }
        val adapter = showList(items, onRecyclerCreated = { recycler = it })
        compose.runOnIdle {
            (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(20, 12)
        }
        compose.waitForIdle()
        var firstPosition = -1
        var firstTop = 0
        compose.runOnIdle {
            firstPosition = (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            firstTop = recycler.getChildAt(0).top
            assertTrue(firstPosition > 0)
            adapter.submitRetryIds(setOf("row-20"))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(firstPosition, (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
            assertEquals(firstTop, recycler.getChildAt(0).top)
            assertEquals(items, adapter.currentList)
            adapter.submitRetryIds(emptySet())
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(firstPosition, (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
            assertEquals(firstTop, recycler.getChildAt(0).top)
            assertEquals(items, adapter.currentList)
        }
    }

    @Test
    fun detailFailureAndOfflineActiveTransitionsKeepTheVisibleBody() {
        val retryVisible = mutableStateOf(false)
        val snapshot = mutableStateOf(record(syncPending = false))
        var retries = 0
        compose.setContent {
            AppTheme {
                ExerciseDetailScreen(
                    state = UiState.Success(snapshot.value),
                    onBackClick = {},
                    onShareClick = {},
                    onMenuClick = {},
                    canShowBodyRetry = retryVisible.value,
                    onRetryClick = { retries++ },
                )
            }
        }
        assertDetailBody()
        compose.onNodeWithTag("exercise_body_retry").assertDoesNotExist()
        compose.onNodeWithTag("exercise_body_failure").assertDoesNotExist()
        compose.onNodeWithText("동기화 대기").assertDoesNotExist()

        compose.runOnIdle { snapshot.value = record(syncPending = true) }
        compose.onNodeWithTag("exercise_body_retry").assertDoesNotExist()
        compose.onNodeWithTag("exercise_body_failure").assertDoesNotExist()
        compose.onNodeWithText("동기화 대기").assertDoesNotExist()
        compose.runOnIdle { retryVisible.value = retryEligible() }
        compose.onNodeWithTag("exercise_body_retry").assertIsDisplayed().performClick()
        compose.onNodeWithTag("exercise_body_failure").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, retries) }
        assertDetailBody()

        listOf(
            retryEligible(validatedInternet = false),
            retryEligible(workerBusy = true),
            retryEligible(authenticatedAccount = false),
            retryEligible(retryableBodyFailure = false),
        ).forEach { eligible ->
            compose.runOnIdle { retryVisible.value = retryEligible() }
            compose.onNodeWithTag("exercise_body_retry").assertIsDisplayed()
            compose.onNodeWithTag("exercise_body_failure").assertIsDisplayed()
            compose.runOnIdle { retryVisible.value = eligible }
            compose.onNodeWithTag("exercise_body_retry").assertDoesNotExist()
            compose.onNodeWithTag("exercise_body_failure").assertDoesNotExist()
            compose.onNodeWithText("동기화 대기").assertDoesNotExist()
            assertDetailBody()
        }
    }

    @Test
    fun remoteDeleteCancelKeepsTheOriginalAndNeverInvokesDiscard() {
        var discards = 0
        compose.setContent {
            AppTheme {
                ExerciseDetailScreen(
                    state = UiState.Success(record(syncPending = true)),
                    onBackClick = {},
                    onShareClick = {},
                    onMenuClick = {},
                    remoteDeleted = true,
                    onDiscardRemoteClick = { discards++ },
                )
            }
        }
        compose.onNodeWithTag("exercise_remote_deleted").assertIsDisplayed()
        compose.onNodeWithTag("exercise_remote_keep").assertIsDisplayed()
        compose.onNodeWithTag("exercise_body_retry").assertDoesNotExist()
        assertDetailBody()
        compose.onNodeWithTag("exercise_remote_discard").performClick()
        compose.onNodeWithTag("exercise_remote_discard_confirm").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, discards) }
        compose.onNodeWithTag("exercise_remote_discard_cancel").performClick()
        compose.onNodeWithTag("exercise_remote_discard_confirm").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, discards) }
        assertDetailBody()
    }

    private fun retryEligible(
        validatedInternet: Boolean = true,
        authenticatedAccount: Boolean = true,
        retryableBodyFailure: Boolean = true,
        workerBusy: Boolean = false,
    ): Boolean =
        canShowExerciseBodyRetry(
            validatedInternet = validatedInternet,
            authenticatedAccount = authenticatedAccount,
            retryableBodyFailure = retryableBodyFailure,
            workerBusy = workerBusy,
        )

    private fun updateListRetry(
        adapter: ExerciseListAdapter,
        eligible: Boolean,
    ) {
        compose.runOnIdle { adapter.submitRetryIds(if (eligible) setOf(RECORD_ID) else emptySet()) }
    }

    private fun showList(
        items: List<ExerciseListItem>,
        onItemClicked: (String) -> Unit = {},
        onRetryClick: (String) -> Unit = {},
        onRecyclerCreated: (RecyclerView) -> Unit = {},
    ): ExerciseListAdapter {
        val adapter = ExerciseListAdapter(onItemClicked = onItemClicked, onRetryClick = onRetryClick)
        compose.setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    RecyclerView(ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Light)).apply {
                        layoutManager = LinearLayoutManager(context)
                        itemAnimator = null
                        this.adapter = adapter
                        onRecyclerCreated(this)
                    }
                },
            )
        }
        compose.runOnIdle { adapter.submitList(items) }
        compose.waitUntil { adapter.currentList.size == items.size }
        compose.waitForIdle()
        return adapter
    }

    private fun assertListRetryHidden() {
        onView(withId(R.id.iv_body_retry)).check(matches(withEffectiveVisibility(GONE)))
    }

    private fun assertDetailBody() {
        compose.onNodeWithTag("exercise_detail_title").assertIsDisplayed().assertTextEquals(TITLE)
        compose.onNodeWithText(BODY).assertIsDisplayed()
    }

    private fun listItem(
        recordId: String = RECORD_ID,
        title: String = TITLE,
        syncPending: Boolean = false,
    ) = ExerciseListItem(
        recordId = recordId,
        title = title,
        type = "",
        startTime = START,
        endTime = START.plusMinutes(30),
        imageUrl = emptyList(),
        syncPending = syncPending,
    )

    private fun record(syncPending: Boolean) =
        ExerciseRecord(
            title = TITLE,
            detail = BODY,
            personalType = "",
            startedAt = START,
            endedAt = START.plusMinutes(30),
            location = "",
            pictures = emptyList(),
            syncPending = syncPending,
        )

    private companion object {
        const val RECORD_ID = "surface-smoke-record"
        const val TITLE = "Synthetic visible exercise"
        const val BODY = "Synthetic body retained across retry state updates"
        val START: LocalDateTime = LocalDateTime.of(2026, 11, 1, 12, 0)
    }
}
