package com.project200.common.utils

import javax.inject.Qualifier

// 일반 SharedPreferences를 위한 이름표
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultPrefs

// 암호화된 SharedPreferences를 위한 이름표
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EncryptedPrefs
