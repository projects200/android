package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseListItem // 수정된 모델 임포트
import com.project200.domain.repository.ExerciseRecordRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class GetExerciseRecordListUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ExerciseRecordRepository

    private lateinit var useCase: GetExerciseRecordListUseCase

    private val testDate: LocalDate = LocalDate.of(2024, 5, 26)
    private val sampleList = listOf(
        ExerciseListItem(
            recordId = 1L,
            title = "아침 달리기",
            type = "달리기",
            startTime = LocalDateTime.now().minusHours(2),
            endTime = LocalDateTime.now().minusHours(1),
            imageUrl = "http://example.com/run.jpg"
        ),
        ExerciseListItem(
            recordId = 2L,
            title = "저녁 헬스",
            type = "웨이트 트레이닝",
            startTime = LocalDateTime.now().minusHours(10),
            endTime = LocalDateTime.now().minusHours(9),
            imageUrl = null
        )
    )

    @Before
    fun setUp() {
        useCase = GetExerciseRecordListUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 Repository의 getExerciseRecordList를 호출하고 성공 결과를 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleList)
        coEvery { mockRepository.getExerciseRecordList(testDate) } returns successResult

        // When
        val result = useCase.invoke(testDate)

        // Then
        coVerify(exactly = 1) { mockRepository.getExerciseRecordList(testDate) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(2)
        assertThat(result.data[0].title).isEqualTo("아침 달리기")
        assertThat(result.data[0].type).isEqualTo("달리기")
        assertThat(result.data[1].imageUrl).isNull()
    }

    @Test
    fun `invoke 호출 시 Repository가 에러를 반환하면 해당 에러를 그대로 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("FETCH_ERROR", "목록을 불러오는데 실패했습니다.")
        coEvery { mockRepository.getExerciseRecordList(testDate) } returns errorResult

        // When
        val result = useCase.invoke(testDate)

        // Then
        coVerify(exactly = 1) { mockRepository.getExerciseRecordList(testDate) }
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("FETCH_ERROR")
    }
}