package com.project200.undabang

import com.google.common.truth.Truth.assertThat
import com.project200.domain.manager.ExerciseCacheCleaner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@ExperimentalCoroutinesApi
class ExerciseCacheCleanupTest {
    @Test
    fun `시작 예약은 정리를 기다리지 않고 백그라운드에서 완료한다`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var started = false
            var completed = false
            val cleaner =
                object : ExerciseCacheCleaner {
                    override suspend fun cleanExpiredCache() {
                        started = true
                        release.await()
                        completed = true
                    }
                }

            val job = launchExerciseCacheCleanup(cleaner)

            assertThat(started).isFalse()
            assertThat(completed).isFalse()
            runCurrent()
            assertThat(started).isTrue()
            assertThat(completed).isFalse()
            assertThat(job.isActive).isTrue()
            release.complete(Unit)
            job.join()
            assertThat(completed).isTrue()
            assertThat(job.isCancelled).isFalse()
        }

    @Test
    fun `정리 예외는 시작 호출자에게 실패나 취소로 전파하지 않는다`() =
        runTest {
            val cleaner =
                object : ExerciseCacheCleaner {
                    override suspend fun cleanExpiredCache() {
                        throw IllegalStateException("정리 실패")
                    }
                }

            val job = launchExerciseCacheCleanup(cleaner)
            job.join()

            assertThat(job.isCompleted).isTrue()
            assertThat(job.isCancelled).isFalse()
            assertThat(coroutineContext[Job]?.isActive).isTrue()
        }

    @Test
    fun `정리의 취소 예외를 성공으로 삼키지 않는다`() =
        runTest {
            val expected = CancellationException("정리 취소")
            var completionCause: Throwable? = null
            val cleaner =
                object : ExerciseCacheCleaner {
                    override suspend fun cleanExpiredCache() {
                        throw expected
                    }
                }

            val job = launchExerciseCacheCleanup(cleaner)
            job.invokeOnCompletion { completionCause = it }
            job.join()

            assertThat(job.isCancelled).isTrue()
            assertThat(completionCause).isSameInstanceAs(expected)
            assertThat(coroutineContext[Job]?.isActive).isTrue()
        }

    @Test
    fun `정리 작업 취소는 실행 중인 정리에도 전달한다`() =
        runTest {
            var cancelled = false
            val cleaner =
                object : ExerciseCacheCleaner {
                    override suspend fun cleanExpiredCache() {
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled = true
                        }
                    }
                }

            val job = launchExerciseCacheCleanup(cleaner)
            runCurrent()
            job.cancel()
            job.join()

            assertThat(cancelled).isTrue()
            assertThat(job.isCancelled).isTrue()
            assertThat(coroutineContext[Job]?.isActive).isTrue()
        }
}
