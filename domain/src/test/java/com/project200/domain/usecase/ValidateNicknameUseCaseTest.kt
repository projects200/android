package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ValidateNicknameUseCaseTest {

    private lateinit var useCase: ValidateNicknameUseCase

    @Before
    fun setUp() {
        useCase = ValidateNicknameUseCase()
    }

    @Test
    fun `한글 닉네임은 유효하다`() {
        // Given
        val nickname = "테스트유저"

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `영어 닉네임은 유효하다`() {
        // Given
        val nickname = "TestUser"

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `숫자가 포함된 닉네임은 유효하다`() {
        // Given
        val nickname = "User123"

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `한글 영어 숫자 조합 닉네임은 유효하다`() {
        // Given
        val nickname = "유저User123"

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `빈 문자열은 유효하지 않다`() {
        // Given
        val nickname = ""

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `공백만 있는 문자열은 유효하지 않다`() {
        // Given
        val nickname = "   "

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `특수문자가 포함된 닉네임은 유효하지 않다`() {
        // Given
        val nickname = "User@123"

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `공백이 포함된 닉네임은 유효하지 않다`() {
        // Given
        val nickname = "User Name"

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `30자 닉네임은 유효하다`() {
        // Given
        val nickname = "a".repeat(30)

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `31자 이상 닉네임은 유효하지 않다`() {
        // Given
        val nickname = "a".repeat(31)

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `1자 닉네임은 유효하다`() {
        // Given
        val nickname = "a"

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `이모지가 포함된 닉네임은 유효하지 않다`() {
        // Given
        val nickname = "User😀"

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `일본어가 포함된 닉네임은 유효하지 않다`() {
        // Given
        val nickname = "ユーザー"

        // When
        val result = useCase(nickname)

        // Then
        assertThat(result).isFalse()
    }
}
