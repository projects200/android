package com.project200.data.utils

/**
 * 이 어노테이션이 붙은 Retrofit API 호출에는
 * IdToken에 대한 Authorization 헤더(토큰)를 추가합니다.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class IdTokenApi

/**
 * 이 어노테이션이 붙은 Retrofit API 호출에는
 * AccessToken에 대한 Authorization 헤더(토큰)를 추가합니다.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AccessTokenApi