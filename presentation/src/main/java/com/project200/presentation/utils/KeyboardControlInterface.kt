package com.project200.presentation.utils

import android.view.MotionEvent

interface KeyboardControlInterface {
    /**
     * 터치 이벤트가 발생했을 때 키보드를 숨겨야 하는지 여부를 반환합니다.
     * @return true: 키보드를 숨기는 기본 동작을 수행함
     * @return false: 키보드를 숨기지 않고 터치 이벤트를 유지함
     */
    fun shouldHideKeyboardOnTouch(ev: MotionEvent): Boolean
}
