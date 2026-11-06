package com.project200.feature.exercise

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.canShowExerciseBodyRetry
import com.project200.feature.exercise.detail.ExerciseDetailScreen
import com.project200.feature.exercise.main.ExerciseListAdapter
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.utils.Failure
import com.project200.presentation.utils.UiState
import com.project200.undabang.feature.exercise.R
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import com.project200.undabang.presentation.R as PresentationR

/**
 * Real production surfaces hosted by the library's existing Compose test activity.
 * Inputs are synthetic UI state only: no account, repository, Worker or server is used.
 * This smoke does not replace the authenticated device/server checks in #657.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseRetrySurfaceSmokeTest {
    private val device = ExerciseSmokeDevice()

    @get:Rule(order = 0)
    val compose = createAndroidComposeRule<ComponentActivity>()

    // Capture failures inside the activity rule, before Compose/ActivityScenario tears it down.
    @get:Rule(order = 1)
    val focusedWindow = device.focusedWindowRule { compose.activity }

    @Test
    fun normalAndPendingRowsHaveNoRetryOrNormalTransmissionText() {
        val adapter = showList(listOf(listItem(syncPending = false)))
        assertListRetryHidden()

        compose.runOnIdle { adapter.submitList(listOf(listItem(syncPending = true))) }
        compose.waitUntil { adapter.currentList.single().syncPending }
        assertListRetryHidden()
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
        }
    }

    @Test
    fun retryOnlyUpdatesKeepTheVisibleListBodyAndScrollAnchor() {
        lateinit var recycler: RecyclerView
        val items =
            (0 until 40).map {
                listItem(recordId = "row-$it", title = "Synthetic row $it with a long title that wraps onto several lines")
            }
        val adapter = showList(items, onRecyclerCreated = { recycler = it })
        compose.runOnIdle {
            (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(20, 12)
        }
        compose.waitForIdle()
        var firstPosition = -1
        var firstTop = 0
        var geometry = emptyList<Int>()
        compose.runOnIdle {
            firstPosition = (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            firstTop = recycler.getChildAt(0).top
            assertTrue(firstPosition > 0)
            geometry = rowGeometry(recycler, 20)
            adapter.submitRetryIds(setOf("row-20"))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(firstPosition, (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
            assertEquals(firstTop, recycler.getChildAt(0).top)
            assertEquals(geometry, rowGeometry(recycler, 20))
            assertEquals(items, adapter.currentList)
            adapter.submitRetryIds(emptySet())
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(firstPosition, (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
            assertEquals(firstTop, recycler.getChildAt(0).top)
            assertEquals(geometry, rowGeometry(recycler, 20))
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
        compose.onNodeWithTag("exercise_detail_scroll").performScrollToIndex(0)
        compose.onNodeWithTag("exercise_body_retry").assertDoesNotExist()
        compose.onNodeWithTag("exercise_body_failure").assertDoesNotExist()

        compose.runOnIdle { snapshot.value = record(syncPending = true) }
        compose.onNodeWithTag("exercise_detail_scroll").performScrollToIndex(0)
        compose.onNodeWithTag("exercise_body_retry").assertDoesNotExist()
        compose.onNodeWithTag("exercise_body_failure").assertDoesNotExist()
        compose.runOnIdle { retryVisible.value = retryEligible() }
        scrollDetailTo("exercise_body_retry")
        compose.onNodeWithTag("exercise_body_retry")
            .assertIsDisplayed()
            .assertContentDescriptionEquals(compose.activity.getString(R.string.exercise_body_retry))
            .performClick()
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
            scrollDetailTo("exercise_body_retry")
            compose.onNodeWithTag("exercise_body_retry").assertIsDisplayed()
            compose.onNodeWithTag("exercise_body_failure").assertIsDisplayed()
            compose.runOnIdle { retryVisible.value = eligible }
            compose.onNodeWithTag("exercise_detail_scroll").performScrollToIndex(0)
            compose.onNodeWithTag("exercise_body_retry").assertDoesNotExist()
            compose.onNodeWithTag("exercise_body_failure").assertDoesNotExist()
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
                    canShowBodyRetry = true,
                    onDiscardRemoteClick = { discards++ },
                )
            }
        }
        compose.onNodeWithTag("exercise_remote_deleted").assertIsDisplayed()
        compose.onNodeWithTag("exercise_remote_keep").assertIsDisplayed()
        compose.onNodeWithTag("exercise_body_retry").assertDoesNotExist()
        assertDetailBody()
        scrollDetailTo("exercise_remote_discard")
        compose.onNodeWithTag("exercise_remote_discard").performClick()
        compose.onNodeWithTag("exercise_remote_discard_confirm").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, discards) }
        compose.onNodeWithTag("exercise_remote_discard_cancel").performClick()
        compose.onNodeWithTag("exercise_remote_discard_confirm").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, discards) }
        assertDetailBody()
        scrollDetailTo("exercise_remote_keep")
        compose.onNodeWithTag("exercise_remote_keep").performClick()
        compose.onNodeWithTag("exercise_remote_deleted").assertDoesNotExist()
        compose.onNodeWithTag("exercise_body_retry").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, discards) }
        assertDetailBody()
    }

    @Test
    fun confirmationInvokesTheCallbackBoundWhenOpenedNotItsReplacement() {
        val replacement = mutableStateOf(false)
        val remoteDeleted = mutableStateOf(true)
        var originalDiscards = 0
        var replacementDiscards = 0
        compose.setContent {
            AppTheme {
                ExerciseDetailScreen(
                    state = UiState.Success(record(syncPending = true)),
                    onBackClick = {},
                    onShareClick = {},
                    onMenuClick = {},
                    remoteDeleted = remoteDeleted.value,
                    onDiscardRemoteClick =
                        if (replacement.value) {
                            { replacementDiscards++ }
                        } else {
                            { originalDiscards++ }
                        },
                )
            }
        }
        compose.onNodeWithTag("exercise_remote_discard").performClick()
        compose.onNodeWithTag("exercise_remote_discard_confirm").assertIsDisplayed()
        compose.runOnIdle { replacement.value = true }
        compose.onNodeWithTag("exercise_remote_discard_confirm").performClick()
        compose.onNodeWithTag("exercise_remote_discard_confirm").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, originalDiscards)
            assertEquals(0, replacementDiscards)
        }

        compose.onNodeWithTag("exercise_remote_discard").performClick()
        compose.onNodeWithTag("exercise_remote_discard_cancel").performClick()
        compose.runOnIdle {
            assertEquals(1, originalDiscards)
            assertEquals(0, replacementDiscards)
        }
        compose.onNodeWithTag("exercise_remote_discard").performClick()
        compose.onNodeWithTag("exercise_remote_discard_confirm").performClick()
        compose.runOnIdle {
            assertEquals(1, originalDiscards)
            assertEquals(1, replacementDiscards)
        }
        compose.onNodeWithTag("exercise_remote_discard").performClick()
        compose.onNodeWithTag("exercise_remote_discard_confirm").assertIsDisplayed()
        compose.runOnIdle { remoteDeleted.value = false }
        compose.onNodeWithTag("exercise_remote_discard_confirm").assertDoesNotExist()
        compose.runOnIdle { remoteDeleted.value = true }
        compose.onNodeWithTag("exercise_remote_discard_confirm").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, originalDiscards)
            assertEquals(1, replacementDiscards)
        }
    }

    @Test
    fun retryAndRemoteNoticeTransitionsPreserveTheScrolledDateAnchor() {
        val retryVisible = mutableStateOf(false)
        val remoteDeleted = mutableStateOf(false)
        val snapshot =
            record(syncPending = true).copy(
                personalType = List(30) { "Synthetic exercise type line $it" }.joinToString("\n"),
                location = "Synthetic retained location",
                detail = List(30) { "Synthetic retained body line $it" }.joinToString("\n"),
            )
        compose.setContent {
            AppTheme {
                Box(Modifier.width(320.dp).height(480.dp)) {
                    ExerciseDetailScreen(
                        state = UiState.Success(snapshot),
                        onBackClick = {},
                        onShareClick = {},
                        onMenuClick = {},
                        canShowBodyRetry = retryVisible.value,
                        remoteDeleted = remoteDeleted.value,
                    )
                }
            }
        }
        scrollDetailTo("exercise_detail_start_time")
        val start = compose.onNodeWithTag("exercise_detail_start_time")
        val end = compose.onNodeWithTag("exercise_detail_end_time")
        start.assertIsDisplayed()
        end.assertIsDisplayed()
        val startBounds = start.fetchSemanticsNode().boundsInRoot
        val endBounds = end.fetchSemanticsNode().boundsInRoot
        val startText = start.fetchSemanticsNode().config[SemanticsProperties.Text]
        val endText = end.fetchSemanticsNode().config[SemanticsProperties.Text]
        listOf(true to false, false to false, false to true, false to false).forEach { (retry, remote) ->
            compose.runOnIdle {
                retryVisible.value = retry
                remoteDeleted.value = remote
            }
            start.assertIsDisplayed()
            end.assertIsDisplayed()
            assertEquals(startBounds, start.fetchSemanticsNode().boundsInRoot)
            assertEquals(endBounds, end.fetchSemanticsNode().boundsInRoot)
            assertEquals(startText, start.fetchSemanticsNode().config[SemanticsProperties.Text])
            assertEquals(endText, end.fetchSemanticsNode().config[SemanticsProperties.Text])
        }
        scrollDetailTo("exercise_detail_title")
        compose.onNodeWithTag("exercise_detail_title").assertTextEquals(TITLE)
    }

    @Test
    fun narrowLargeFontTopBarKeepsTitleBetweenNavigationAndActions() {
        var navigations = 0
        var shares = 0
        var menus = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AppTheme {
                    Box(Modifier.width(320.dp).height(480.dp)) {
                        ExerciseDetailScreen(
                            state = UiState.Success(record(syncPending = false)),
                            onBackClick = { navigations++ },
                            onShareClick = { shares++ },
                            onMenuClick = { menus++ },
                        )
                    }
                }
            }
        }
        val title = compose.onNodeWithText(compose.activity.getString(R.string.exercise_detail))
        val navigation = compose.onNodeWithContentDescription("뒤로가기")
        val share = compose.onNodeWithContentDescription(compose.activity.getString(R.string.share))
        val menu = compose.onNodeWithTag("exercise_detail_menu")
        title.assertIsDisplayed()
        navigation.assertIsDisplayed()
        share.assertIsDisplayed()
        menu.assertIsDisplayed()
        val titleBounds = title.fetchSemanticsNode().boundsInRoot
        val navigationBounds = navigation.fetchSemanticsNode().boundsInRoot
        val shareBounds = share.fetchSemanticsNode().boundsInRoot
        val menuBounds = menu.fetchSemanticsNode().boundsInRoot
        assertTrue(titleBounds.width > 0)
        assertTrue(navigationBounds.right <= titleBounds.left)
        assertTrue(titleBounds.right <= shareBounds.left)
        assertTrue(shareBounds.right <= menuBounds.left)
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertFalse(layouts.single().hasVisualOverflow && !layouts.single().isLineEllipsized(0))
        navigation.performClick()
        share.performClick()
        menu.performClick()
        compose.runOnIdle {
            assertEquals(1, navigations)
            assertEquals(1, shares)
            assertEquals(1, menus)
        }
    }

    @Test
    fun readOnlyAndLoadErrorDetailKeepTheMenuReachable() {
        val state =
            mutableStateOf<UiState<ExerciseRecord>>(
                UiState.Success(record(syncPending = false).copy(canModify = false)),
            )
        var menus = 0
        compose.setContent {
            AppTheme {
                ExerciseDetailScreen(
                    state = state.value,
                    onBackClick = {},
                    onShareClick = {},
                    onMenuClick = { menus++ },
                )
            }
        }
        compose.onNodeWithTag("exercise_detail_menu").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, menus)
            state.value = UiState.Error(Failure.NetworkError)
        }
        compose.onNodeWithTag("exercise_detail_menu").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, menus) }
    }

    @Test
    fun narrowRemoteActionsShareOneCompactRowAndKeepActuallyDismissesTheNotice() {
        var discards = 0
        showRemoteDeletedDetail(onDiscardRemoteClick = { discards++ })

        val keepBounds =
            assertReadableAction("exercise_remote_keep", compose.activity.getString(R.string.exercise_remote_keep))
        val discardBounds =
            assertReadableAction("exercise_remote_discard", compose.activity.getString(R.string.exercise_remote_discard))
        val touchTarget = with(compose.density) { 48.dp.toPx() }
        assertEquals(keepBounds.top, discardBounds.top, 1f)
        assertTrue(keepBounds.right <= discardBounds.left)
        assertTrue(keepBounds.height >= touchTarget - 1f)
        assertTrue(discardBounds.height >= touchTarget - 1f)

        compose.onNodeWithTag("exercise_remote_keep").performClick()
        compose.onNodeWithTag("exercise_remote_deleted").assertDoesNotExist()
        compose.onNodeWithTag("exercise_remote_discard").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, discards) }
        assertDetailBody()
    }

    @Test
    fun narrowDoubleSizeTextWrapsFullRemoteActionsWithoutOverlapAndKeepsThemClickable() {
        var discards = 0
        showRemoteDeletedDetail(fontScale = 2f, onDiscardRemoteClick = { discards++ })

        val keepBounds =
            assertReadableAction("exercise_remote_keep", compose.activity.getString(R.string.exercise_remote_keep))
        val discardBounds =
            assertReadableAction("exercise_remote_discard", compose.activity.getString(R.string.exercise_remote_discard))
        val touchTarget = with(compose.density) { 48.dp.toPx() }
        assertTrue(keepBounds.bottom <= discardBounds.top)
        assertTrue(keepBounds.height >= touchTarget - 1f)
        assertTrue(discardBounds.height >= touchTarget - 1f)

        compose.onNodeWithTag("exercise_remote_discard").performClick()
        compose.onNodeWithTag("exercise_remote_discard_confirm").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, discards) }
        compose.onNodeWithTag("exercise_remote_discard_cancel").performClick()
        compose.onNodeWithTag("exercise_remote_discard_confirm").assertDoesNotExist()
        compose.onNodeWithTag("exercise_remote_keep").performClick()
        compose.onNodeWithTag("exercise_remote_deleted").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, discards) }
        assertDetailBody()
    }

    @Test
    fun narrowConfirmationShowsTheWholeMessageAndDeleteActionBeforeDiscarding() {
        var discards = 0
        showRemoteDeletedDetail(onDiscardRemoteClick = { discards++ })
        compose.onNodeWithTag("exercise_remote_discard").performClick()
        // A Compose Dialog has its own window; narrowing only the screen Box does not constrain it.
        onView(instanceOf(DialogWindowProvider::class.java))
            .inRoot(isDialog())
            .check { view, error ->
                if (error != null) throw error
                (view as DialogWindowProvider).window.setLayout(
                    with(compose.density) { 320.dp.roundToPx() },
                    WindowManager.LayoutParams.WRAP_CONTENT,
                )
            }
        compose.waitForIdle()

        assertFullyVisibleText(
            compose.onNodeWithText(
                compose.activity.getString(R.string.exercise_remote_discard_message),
                useUnmergedTree = true,
            ),
        )
        val cancelBounds =
            assertReadableAction("exercise_remote_discard_cancel", compose.activity.getString(R.string.cancel))
        val confirmBounds =
            assertReadableAction("exercise_remote_discard_confirm", compose.activity.getString(PresentationR.string.delete))
        assertTrue(cancelBounds.right <= confirmBounds.left)
        compose.runOnIdle { assertEquals(0, discards) }
        compose.onNodeWithTag("exercise_remote_discard_confirm").performClick()
        compose.onNodeWithTag("exercise_remote_discard_confirm").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, discards) }
        assertDetailBody()
    }

    private fun showRemoteDeletedDetail(
        fontScale: Float = 1f,
        onDiscardRemoteClick: () -> Unit,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
                AppTheme {
                    Box(Modifier.width(320.dp).height(480.dp)) {
                        ExerciseDetailScreen(
                            state = UiState.Success(record(syncPending = true)),
                            onBackClick = {},
                            onShareClick = {},
                            onMenuClick = {},
                            remoteDeleted = true,
                            onDiscardRemoteClick = onDiscardRemoteClick,
                        )
                    }
                }
            }
        }
    }

    private fun assertReadableAction(
        tag: String,
        text: String,
    ): Rect {
        val action = compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled()
        val label = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
        assertEquals(1, assertFullyVisibleText(label).lineCount)
        val actionBounds = action.fetchSemanticsNode().boundsInRoot
        val labelBounds = label.fetchSemanticsNode().boundsInRoot
        assertTrue(actionBounds.left <= labelBounds.left)
        assertTrue(labelBounds.right <= actionBounds.right)
        assertTrue(actionBounds.top <= labelBounds.top)
        assertTrue(labelBounds.bottom <= actionBounds.bottom)
        return actionBounds
    }

    private fun assertFullyVisibleText(node: SemanticsNodeInteraction): TextLayoutResult {
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val semanticsNode = node.fetchSemanticsNode()
        val visibleBounds = semanticsNode.boundsInRoot
        val lineBounds =
            (0 until layout.lineCount).map { line ->
                Rect(
                    layout.getLineLeft(line),
                    layout.getLineTop(line),
                    layout.getLineRight(line),
                    layout.getLineBottom(line),
                )
            }
        val diagnostics =
            "text=${layout.layoutInput.text.text}, size=${layout.size}, " +
                "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
                "didOverflowWidth=${layout.didOverflowWidth}, didOverflowHeight=${layout.didOverflowHeight}, " +
                "maxLines=${layout.layoutInput.maxLines}, didExceedMaxLines=${layout.multiParagraph.didExceedMaxLines}, " +
                "lines=$lineBounds, position=${semanticsNode.positionInRoot}, visibleBounds=$visibleBounds"
        // Compose 1.6's ParagraphLayoutCache.slowCreateTextLayoutResultOrNull rebuilds MultiParagraph
        // at the incoming maxWidth, but retains the smaller measured size. didOverflowWidth can
        // therefore report unused paragraph space as overflow; check occupied line bounds instead.
        assertFalse("The complete text must fit vertically: $diagnostics", layout.didOverflowHeight)
        assertTrue(
            "The complete text must fit horizontally: $diagnostics",
            lineBounds.all { it.left >= 0f && it.right <= layout.size.width },
        )
        assertTrue(
            "No text may be ellipsized: $diagnostics",
            (0 until layout.lineCount).none { layout.isLineEllipsized(it) },
        )
        assertEquals(
            "No text may be omitted: $diagnostics",
            layout.layoutInput.text.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true),
        )
        assertTrue(
            "Text must not be clipped horizontally by its ancestors: $diagnostics",
            layout.size.width <= visibleBounds.width + 1f,
        )
        assertTrue(
            "Text must not be clipped vertically by its ancestors: $diagnostics",
            layout.size.height <= visibleBounds.height + 1f,
        )
        assertTrue(
            "Every line must lie within the visible bounds: $diagnostics",
            lineBounds.all {
                val boundsInRoot = it.translate(semanticsNode.positionInRoot)
                boundsInRoot.left >= visibleBounds.left - 1f &&
                    boundsInRoot.right <= visibleBounds.right + 1f &&
                    boundsInRoot.top >= visibleBounds.top - 1f &&
                    boundsInRoot.bottom <= visibleBounds.bottom + 1f
            },
        )
        return layout
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
        onView(withId(R.id.iv_body_retry))
            .check(matches(not(isDisplayed())))
            .check { view, error ->
                if (error != null) throw error
                val hidden = requireNotNull(view)
                assertTrue(hidden.width > 0)
                assertTrue(hidden.height > 0)
                assertFalse(hidden.isClickable)
                assertFalse(hidden.isFocusable)
                assertFalse(hidden.isEnabled)
                assertFalse(hidden.isImportantForAccessibility)
                assertFalse(hidden.performClick())
            }
    }

    private fun rowGeometry(
        recycler: RecyclerView,
        position: Int,
    ): List<Int> {
        val row = requireNotNull(recycler.findViewHolderForAdapterPosition(position)).itemView
        val title = row.findViewById<TextView>(R.id.exercise_title_tv)
        assertTrue(title.lineCount > 1)
        return listOf(row.width, row.height, title.left, title.top, title.width, title.height, title.lineCount)
    }

    private fun scrollDetailTo(tag: String) {
        compose.onNodeWithTag("exercise_detail_scroll").performScrollToNode(hasTestTag(tag))
    }

    private fun assertDetailBody() {
        scrollDetailTo("exercise_detail_title")
        compose.onNodeWithTag("exercise_detail_title").assertIsDisplayed().assertTextEquals(TITLE)
        compose.onNodeWithTag("exercise_detail_scroll").performScrollToNode(hasText(BODY))
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
