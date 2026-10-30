package com.project200.undabang.e2e

import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.ExerciseRecordRemoteDataSource
import com.project200.data.dto.BaseResponse
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.repository.ExerciseRecordRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MaestroFixtureCleanerTest {
    private val exerciseRecordRepository = mockk<ExerciseRecordRepository>()
    private val localDataSource = mockk<ExerciseRecordLocalDataSource>()
    private val api = mockk<ApiService>()
    private val dispatcher = StandardTestDispatcher()
    private val cleaner =
        MaestroFixtureCleaner(exerciseRecordRepository, ExerciseRecordRemoteDataSource(api), localDataSource, dispatcher)
    private val memberId = "fixture-member"
    private val today = LocalDate.of(2026, 10, 12)
    private val title = "maestro-e2e-0123456789abcdef0123456789abcdef"

    @Before
    fun setUp() {
        every { localDataSource.currentMemberId() } returns memberId
        every { localDataSource.isCurrentMember(memberId) } returns true
        coEvery { localDataSource.resolveServerId("1", memberId) } returns 101L
        coEvery { localDataSource.resolveServerId("2", memberId) } returns 102L
        coEvery { localDataSource.hasPendingServerRecord(any(), memberId) } returns false
        every { localDataSource.invalidateCache(any()) } just Runs
    }

    @After
    fun neverCreateLocalDeleteTombstones() {
        coVerify(exactly = 0) { exerciseRecordRepository.deleteExerciseRecord(any()) }
    }

    @Test
    fun `일반 기록은 보존하고 정확한 예약 제목만 삭제한다`() =
        runTest(dispatcher) {
            // Given
            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(today, today, refresh = true)
            } returns
                success(listOf(ExerciseCount(today, 4)))
            coEvery { exerciseRecordRepository.getExerciseRecordList(today, refresh = true) } returns
                success(
                    listOf(
                        item(1, title),
                        item(2, "$title-edited"),
                        item(3, "daily exercise"),
                        item(4, "$title-extra"),
                    ),
                )
            coEvery { exerciseRecordRepository.getExerciseDetail("1", refresh = true) } returns success(detail(title))
            coEvery { exerciseRecordRepository.getExerciseDetail("2", refresh = true) } returns success(detail("$title-edited"))
            coEvery { api.deleteExerciseRecord(any()) } returns remoteSuccess()

            // When
            cleaner.clean(today, today)

            // Then
            coVerify(exactly = 1) { api.deleteExerciseRecord(101L) }
            coVerify(exactly = 1) { api.deleteExerciseRecord(102L) }
            coVerify(exactly = 2) { api.deleteExerciseRecord(any()) }
            verify(exactly = 2) { localDataSource.invalidateCache(memberId) }
            coVerify(exactly = 0) { exerciseRecordRepository.getExerciseDetail("3", any(), any()) }
            coVerify(exactly = 0) { exerciseRecordRepository.getExerciseDetail("4", any(), any()) }
        }

    @Test
    fun `삭제 직전에 일반 제목으로 바뀐 기록은 보존한다`() =
        runTest(dispatcher) {
            // Given
            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(today, today, refresh = true)
            } returns
                success(listOf(ExerciseCount(today, 1)))
            coEvery {
                exerciseRecordRepository.getExerciseRecordList(today, refresh = true)
            } returns success(listOf(item(1, title)))
            coEvery { exerciseRecordRepository.getExerciseDetail("1", refresh = true) } returns success(detail("my real exercise"))

            // When
            cleaner.clean(today, today)

            // Then
            assertNoServerDeletion()
        }

    @Test
    fun `다음 월 조회가 실패하면 이전 월 후보도 삭제하지 않는다`() =
        runTest(dispatcher) {
            // Given
            val start = LocalDate.of(2026, 9, 30)
            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(start, start, refresh = true)
            } returns
                success(listOf(ExerciseCount(start, 1)))
            coEvery {
                exerciseRecordRepository.getExerciseRecordList(start, refresh = true)
            } returns
                success(listOf(item(1, title, start)))
            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(
                    LocalDate.of(2026, 10, 1),
                    today,
                    refresh = true,
                )
            } returns serverError()
            // When
            expectFailure("fixture_read_failed") { cleaner.clean(start, today) }

            // Then
            assertNoServerDeletion()
            coVerify(exactly = 0) { exerciseRecordRepository.getExerciseDetail(any(), any(), any()) }
        }

    @Test
    fun `서버가 조회 구간 밖 날짜를 반환하면 삭제하지 않는다`() =
        runTest(dispatcher) {
            // Given
            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(today, today, refresh = true)
            } returns
                success(listOf(ExerciseCount(today.minusDays(1), 1)))

            // When
            expectFailure("fixture_read_failed") { cleaner.clean(today, today) }

            // Then
            coVerify(exactly = 0) { exerciseRecordRepository.getExerciseRecordList(any(), any(), any()) }
            assertNoServerDeletion()
        }

    @Test
    fun `기록이 없는 날짜는 목록을 조회하지 않는다`() =
        runTest(dispatcher) {
            // Given
            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(today, today, refresh = true)
            } returns
                success(listOf(ExerciseCount(today, 0)))

            // When
            cleaner.clean(today, today)

            // Then
            coVerify(exactly = 0) { exerciseRecordRepository.getExerciseRecordList(any(), any(), any()) }
            assertNoServerDeletion()
        }

    @Test
    fun `삭제 응답이 실패하면 정리 성공을 반환하지 않는다`() =
        runTest(dispatcher) {
            // Given
            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(today, today, refresh = true)
            } returns
                success(listOf(ExerciseCount(today, 1)))
            coEvery {
                exerciseRecordRepository.getExerciseRecordList(today, refresh = true)
            } returns success(listOf(item(1, title)))
            coEvery { exerciseRecordRepository.getExerciseDetail("1", refresh = true) } returns success(detail(title))
            coEvery { api.deleteExerciseRecord(101L) } returns BaseResponse(false, "SERVER_ERROR", "")

            // When, Then
            expectFailure("fixture_delete_failed") { cleaner.clean(today, today) }
            coVerify(exactly = 1) { api.deleteExerciseRecord(101L) }
            verify(exactly = 0) { localDataSource.invalidateCache(any()) }
        }

    @Test
    fun `일시적인 조회 오류는 재시도 후 정리를 계속한다`() =
        runTest(dispatcher) {
            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(today, today, refresh = true)
            } returnsMany
                listOf(
                    BaseResult.Error("NETWORK_ERROR", "네트워크 오류"),
                    success(emptyList()),
                )

            cleaner.clean(today, today)

            coVerify(exactly = 2) {
                exerciseRecordRepository.getExerciseCountByRange(today, today, refresh = true)
            }
        }

    @Test
    fun `일시적인 삭제 오류는 재시도 후 성공한다`() =
        runTest(dispatcher) {
            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(today, today, refresh = true)
            } returns success(listOf(ExerciseCount(today, 1)))
            coEvery {
                exerciseRecordRepository.getExerciseRecordList(today, refresh = true)
            } returns success(listOf(item(1, title)))
            coEvery { exerciseRecordRepository.getExerciseDetail("1", refresh = true) } returns success(detail(title))
            coEvery { api.deleteExerciseRecord(101L) } returnsMany
                listOf(
                    BaseResponse(false, "503", ""),
                    remoteSuccess(),
                )

            cleaner.clean(today, today)

            coVerify(exactly = 2) { api.deleteExerciseRecord(101L) }
            verify(exactly = 1) { localDataSource.invalidateCache(memberId) }
        }

    @Test
    fun `오프라인 스냅샷이나 갱신 실패를 근거로 기록을 지우지 않는다`() =
        runTest(dispatcher) {
            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(today, today, refresh = true)
            } returns
                BaseResult.Success(listOf(ExerciseCount(today, 1)), isCachedSnapshot = true)
            expectFailure("fixture_read_failed") { cleaner.clean(today, today) }
            assertNoServerDeletion()

            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(today, today, refresh = true)
            } returns
                BaseResult.Success(
                    listOf(ExerciseCount(today, 1)),
                    refreshError = BaseResult.Error("401", "인증 실패"),
                )
            expectFailure("fixture_read_failed") { cleaner.clean(today, today) }
            assertNoServerDeletion()
        }

    @Test
    fun `상세에 대기 변경이 남아 있으면 서버 삭제를 실행하지 않는다`() =
        runTest(dispatcher) {
            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(today, today, refresh = true)
            } returns success(listOf(ExerciseCount(today, 1)))
            coEvery {
                exerciseRecordRepository.getExerciseRecordList(today, refresh = true)
            } returns success(listOf(item(1, title)))
            coEvery { exerciseRecordRepository.getExerciseDetail("1", refresh = true) } returns
                success(detail(title).copy(syncPending = true, canModify = false))
            expectFailure("fixture_read_failed") { cleaner.clean(today, today) }
            assertNoServerDeletion()
        }

    @Test
    fun `시작일이 종료일보다 늦으면 조회나 삭제를 실행하지 않는다`() =
        runTest(dispatcher) {
            expectFailure("fixture_range_invalid") { cleaner.clean(today.plusDays(1), today) }

            coVerify(exactly = 0) { exerciseRecordRepository.getExerciseCountByRange(any(), any(), any(), any()) }
            assertNoServerDeletion()
        }

    @Test
    fun `목록의 대기 기록이나 다른 날짜 기록은 삭제 후보로 사용하지 않는다`() =
        runTest(dispatcher) {
            stubCandidate()
            for (record in listOf(item(1, title).copy(syncPending = true), item(1, title, today.minusDays(1)))) {
                coEvery { exerciseRecordRepository.getExerciseRecordList(today, refresh = true) } returns success(listOf(record))

                expectFailure("fixture_read_failed") { cleaner.clean(today, today) }
            }

            coVerify(exactly = 0) { exerciseRecordRepository.getExerciseDetail(any(), any(), any()) }
            assertNoServerDeletion()
        }

    @Test
    fun `상세 날짜나 수정 권한이 바뀌면 예약 제목이어도 삭제하지 않는다`() =
        runTest(dispatcher) {
            stubCandidate()
            val records =
                listOf(
                    detail(title).copy(startedAt = today.minusDays(1).atTime(9, 0)),
                    detail(title).copy(canModify = false),
                )
            for (record in records) {
                coEvery { exerciseRecordRepository.getExerciseDetail("1", refresh = true) } returns success(record)

                expectFailure("fixture_read_failed") { cleaner.clean(today, today) }
            }

            assertNoServerDeletion()
        }

    @Test
    fun `로컬 UUID는 캡처한 회원의 실제 서버 ID로 해석한 뒤 삭제한다`() =
        runTest(dispatcher) {
            val localId = "0bdb1d30-56ed-42af-bad5-4caeed53de1f"
            stubCandidate(localId)
            coEvery { localDataSource.resolveServerId(localId, memberId) } returns 901L
            coEvery { api.deleteExerciseRecord(901L) } returns remoteSuccess()

            cleaner.clean(today, today)

            coVerifyOrder {
                exerciseRecordRepository.getExerciseDetail(localId, refresh = true)
                localDataSource.resolveServerId(localId, memberId)
                localDataSource.hasPendingServerRecord(901L, memberId)
                localDataSource.isCurrentMember(memberId)
                api.deleteExerciseRecord(901L)
                localDataSource.invalidateCache(memberId)
            }
            coVerify(exactly = 1) { api.deleteExerciseRecord(any()) }
        }

    @Test
    fun `서버 ID가 없거나 양수가 아니면 로컬 ID를 대신 보내지 않는다`() =
        runTest(dispatcher) {
            stubCandidate()
            for (serverId in listOf(null, 0L, -1L)) {
                coEvery { localDataSource.resolveServerId("1", memberId) } returns serverId

                expectFailure("fixture_delete_failed") { cleaner.clean(today, today) }
            }

            coVerify(exactly = 3) { localDataSource.resolveServerId("1", memberId) }
            assertNoServerDeletion()
        }

    @Test
    fun `현재 회원이 없으면 fixture 조회와 삭제를 실행하지 않는다`() =
        runTest(dispatcher) {
            every { localDataSource.currentMemberId() } returns null

            expectFailure("fixture_read_failed") { cleaner.clean(today, today) }

            coVerify(exactly = 0) { exerciseRecordRepository.getExerciseCountByRange(any(), any(), any(), any()) }
            assertNoServerDeletion()
        }

    @Test
    fun `상세 조회 중 계정이 바뀌면 이전 계정의 후보를 삭제하지 않는다`() =
        runTest(dispatcher) {
            stubCandidate()
            coEvery { exerciseRecordRepository.getExerciseDetail("1", refresh = true) } coAnswers {
                every { localDataSource.currentMemberId() } returns "other-member"
                every { localDataSource.isCurrentMember(memberId) } returns false
                success(detail(title))
            }

            expectFailure("fixture_delete_failed") { cleaner.clean(today, today) }

            coVerify(exactly = 1) { localDataSource.resolveServerId("1", memberId) }
            assertNoServerDeletion()
        }

    @Test
    fun `삭제 직전 대기 조회 중 계정이 바뀌어도 서버를 호출하지 않는다`() =
        runTest(dispatcher) {
            stubCandidate()
            coEvery { localDataSource.hasPendingServerRecord(101L, memberId) } coAnswers {
                every { localDataSource.isCurrentMember(memberId) } returns false
                false
            }

            expectFailure("fixture_delete_failed") { cleaner.clean(today, today) }

            assertNoServerDeletion()
        }

    @Test
    fun `fresh 상세 이후 로컬 대기 변경이 생기면 서버를 삭제하지 않는다`() =
        runTest(dispatcher) {
            stubCandidate()
            coEvery { localDataSource.hasPendingServerRecord(101L, memberId) } returns true

            expectFailure("fixture_delete_failed") { cleaner.clean(today, today) }

            coVerify(exactly = 1) { localDataSource.hasPendingServerRecord(101L, memberId) }
            assertNoServerDeletion()
        }

    @Test
    fun `삭제 재시도 전에 계정이 바뀌면 추가 요청과 캐시 무효화를 중단한다`() =
        runTest(dispatcher) {
            stubCandidate()
            coEvery { api.deleteExerciseRecord(101L) } coAnswers {
                every { localDataSource.isCurrentMember(memberId) } returns false
                BaseResponse(false, "503", "")
            }

            expectFailure("fixture_delete_failed") { cleaner.clean(today, today) }

            coVerify(exactly = 1) { api.deleteExerciseRecord(101L) }
            verify(exactly = 0) { localDataSource.invalidateCache(any()) }
        }

    @Test
    fun `삭제 재시도 전에 대기 변경이 생기면 추가 서버 삭제를 중단한다`() =
        runTest(dispatcher) {
            stubCandidate()
            coEvery { api.deleteExerciseRecord(101L) } coAnswers {
                coEvery { localDataSource.hasPendingServerRecord(101L, memberId) } returns true
                BaseResponse(false, "503", "")
            }

            expectFailure("fixture_delete_failed") { cleaner.clean(today, today) }

            coVerify(exactly = 1) { api.deleteExerciseRecord(101L) }
            verify(exactly = 0) { localDataSource.invalidateCache(any()) }
        }

    @Test
    fun `실제 네트워크 삭제 오류는 세 번까지만 재시도하고 캐시를 유지한다`() =
        runTest(dispatcher) {
            stubCandidate()
            coEvery { api.deleteExerciseRecord(101L) } throws IOException("offline")

            expectFailure("fixture_delete_failed") { cleaner.clean(today, today) }

            coVerify(exactly = 3) { api.deleteExerciseRecord(101L) }
            coVerify(exactly = 3) { localDataSource.hasPendingServerRecord(101L, memberId) }
            assertThat(testScheduler.currentTime).isEqualTo(3_000L)
            verify(exactly = 0) { localDataSource.invalidateCache(any()) }
        }

    @Test
    fun `서버 삭제 성공 응답 이후에만 요청 회원의 캐시를 무효화한다`() =
        runTest(dispatcher) {
            stubCandidate()
            val entered = CompletableDeferred<Unit>()
            val response = CompletableDeferred<BaseResponse<Unit?>>()
            coEvery { api.deleteExerciseRecord(101L) } coAnswers {
                entered.complete(Unit)
                response.await()
            }

            val cleanup = async { cleaner.clean(today, today) }
            entered.await()
            verify(exactly = 0) { localDataSource.invalidateCache(any()) }
            every { localDataSource.currentMemberId() } returns "other-member"
            every { localDataSource.isCurrentMember(memberId) } returns false
            response.complete(remoteSuccess())
            cleanup.await()

            verify(exactly = 1) { localDataSource.invalidateCache(memberId) }
            verify(exactly = 0) { localDataSource.invalidateCache("other-member") }
        }

    private fun stubCandidate(recordId: String = "1") {
        coEvery {
            exerciseRecordRepository.getExerciseCountByRange(today, today, refresh = true)
        } returns success(listOf(ExerciseCount(today, 1)))
        coEvery { exerciseRecordRepository.getExerciseRecordList(today, refresh = true) } returns
            success(listOf(item(1, title).copy(recordId = recordId)))
        coEvery { exerciseRecordRepository.getExerciseDetail(recordId, refresh = true) } returns success(detail(title))
    }

    private fun item(
        id: Long,
        itemTitle: String,
        date: LocalDate = today,
    ) = ExerciseListItem(id.toString(), itemTitle, "OTHER", date.atTime(9, 0), date.atTime(10, 0), null)

    private fun detail(recordTitle: String) = ExerciseRecord(recordTitle, "", "OTHER", today.atTime(9, 0), today.atTime(10, 0), "", null)

    private fun <T> success(data: T): BaseResult<T> = BaseResult.Success(data)

    private fun serverError(): BaseResult<Nothing> = BaseResult.Error(errorCode = "SERVER_ERROR", message = "")

    private fun remoteSuccess(): BaseResponse<Unit?> = BaseResponse(true, "OK", "")

    private fun assertNoServerDeletion() {
        coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
        verify(exactly = 0) { localDataSource.invalidateCache(any()) }
    }

    private suspend fun expectFailure(
        reason: String,
        operation: suspend () -> Unit,
    ) {
        try {
            operation()
            throw AssertionError("실패 응답이 허용되었습니다")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo(reason)
        }
    }
}
