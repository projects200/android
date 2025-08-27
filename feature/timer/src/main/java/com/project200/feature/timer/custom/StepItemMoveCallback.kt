package com.project200.feature.timer.custom

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * 스텝 아이템의 드래그 앤 드롭을 처리하는 ItemTouchHelper.Callback 구현체입니다.
 * @param onMoveAction 아이템 위치가 변경되었을 때 호출될 람다 함수. (fromPosition, toPosition)을 인자로 받습니다.
 */
class StepItemMoveCallback(
    private val onMoveAction: (fromPosition: Int, toPosition: Int) -> Unit
) : ItemTouchHelper.Callback() {

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        // StepViewHolder만 드래그가 가능하도록 설정
        return if (viewHolder is StepViewHolder) {
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            makeMovementFlags(dragFlags, 0) // swipeFlags는 0으로 설정하여 스와이프 비활성화
        } else {
            makeMovementFlags(0, 0) // 그 외 ViewHolder(Footer)는 이동 불가
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.bindingAdapterPosition
        val toPosition = target.bindingAdapterPosition

        // target이 Footer인 경우 이동하지 않도록 방지
        if (target is FooterViewHolder) {
            return false
        }

        onMoveAction(fromPosition, toPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun isLongPressDragEnabled(): Boolean {
        // 핸들러를 통해서만 드래그를 시작할 것이므로 long press는 비활성화
        return false
    }
}