package com.project200.undabang.exercise

import android.app.Application
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.common.truth.Truth.assertThat
import com.project200.data.local.PreferenceManager
import com.project200.domain.model.ExerciseSyncResult
import com.project200.domain.usecase.GetMemberIdUseCase
import com.project200.domain.usecase.SyncExercisesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class ExerciseSyncWorkerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val member = mockk<GetMemberIdUseCase>()
    private val sync = mockk<SyncExercisesUseCase>()

    private fun worker(attempt: Int = 0, requested: String? = "member"): ExerciseSyncWorker {
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.inputData } returns Data.Builder().putString(ExerciseSyncWorker.KEY_MEMBER_ID, requested).build()
        every { parameters.runAttemptCount } returns attempt
        return ExerciseSyncWorker(context, parameters, member, sync)
    }

    @Test
    fun `실제 Worker는 성공과 인증 보류를 success로 반환한다`() = runTest {
        coEvery { member() } returns "member"
        for (outcome in listOf(ExerciseSyncResult.Success, ExerciseSyncResult.Skipped)) {
            coEvery { sync("member") } returns outcome
            assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.success())
        }
    }

    @Test
    fun `실제 Worker는 영구 실패를 실패로 재시도 오류를 상한 전까지 retry로 반환한다`() = runTest {
        coEvery { member() } returns "member"
        coEvery { sync("member") } returns ExerciseSyncResult.Failure
        assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.failure())
        coEvery { sync("member") } returns ExerciseSyncResult.Retry
        for (attempt in 0 until ExerciseSyncWorker.MAX_ATTEMPTS - 1) {
            assertThat(worker(attempt).doWork()).isEqualTo(ListenableWorker.Result.retry())
        }
        for (attempt in listOf(ExerciseSyncWorker.MAX_ATTEMPTS - 1, ExerciseSyncWorker.MAX_ATTEMPTS)) {
            assertThat(worker(attempt).doWork()).isEqualTo(ListenableWorker.Result.failure())
        }
        // 상한은 Worker 결과만 바꿉니다. Repository에 삭제/초기화 API를 호출하지 않습니다
        coVerify(exactly = ExerciseSyncWorker.MAX_ATTEMPTS + 2) { sync("member") }
    }

    @Test
    fun `요청 회원이 없거나 현재 회원과 다르면 Repository를 호출하지 않는다`() = runTest {
        coEvery { member() } returns "different"
        assertThat(worker(requested = null).doWork()).isEqualTo(ListenableWorker.Result.success())
        assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.success())
        coVerify(exactly = 0) { sync(any()) }
    }

    @Test
    fun `실제 Worker는 코루틴 취소를 success나 retry로 삼키지 않는다`() = runTest {
        coEvery { member() } returns "member"
        coEvery { sync("member") } throws CancellationException("중단")
        try {
            worker().doWork()
            throw AssertionError("취소가 전파되지 않았습니다")
        } catch (expected: CancellationException) {
            assertThat(expected.message).isEqualTo("중단")
        }
    }

    @Test
    fun `실제 작업 요청은 연결 제약과 지수 backoff 및 회원 입력을 가진다`() {
        val request = ExerciseSyncSchedulerImpl.request("member")
        assertThat(request.workSpec.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(request.workSpec.backoffPolicy).isEqualTo(BackoffPolicy.EXPONENTIAL)
        assertThat(request.workSpec.backoffDelayDuration).isEqualTo(30_000L)
        assertThat(request.workSpec.input.getString(ExerciseSyncWorker.KEY_MEMBER_ID)).isEqualTo("member")
        assertThat(request.workSpec.workerClassName).isEqualTo(ExerciseSyncWorker::class.java.name)
    }

    @Test
    fun `예약과 취소는 같은 고유 작업을 사용하고 E2E에서도 비활성화하지 않는다`() {
        val preferences = mockk<PreferenceManager>()
        val manager = mockk<WorkManager>(relaxed = true)
        val request = slot<OneTimeWorkRequest>()
        every { preferences.getMemberId() } returns "member"
        mockkStatic(WorkManager::class)
        try {
            every { WorkManager.getInstance(context) } returns manager
            val scheduler = ExerciseSyncSchedulerImpl(context, preferences)
            scheduler.schedule()
            scheduler.cancel()
            verify(exactly = 1) {
                manager.enqueueUniqueWork(ExerciseSyncWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, capture(request))
            }
            verify(exactly = 1) { manager.cancelUniqueWork(ExerciseSyncWorker.WORK_NAME) }
            assertThat(request.captured.workSpec.input.getString(ExerciseSyncWorker.KEY_MEMBER_ID)).isEqualTo("member")
            every { preferences.getMemberId() } returns null
            scheduler.schedule()
            verify(exactly = 1) { manager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
        } finally {
            unmockkStatic(WorkManager::class)
        }
    }
}
