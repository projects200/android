package com.project200.feature.timer.custom

import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project200.presentation.base.BaseAlertDialog
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.view.MenuBottomSheetDialog
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentCustomTimerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CustomTimerFragment: BindingFragment<FragmentCustomTimerBinding>(R.layout.fragment_custom_timer) {
    private var progressAnimator: ValueAnimator? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun getViewBinding(view: View): FragmentCustomTimerBinding {
        return FragmentCustomTimerBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            showBackButton(true) { findNavController().navigateUp() }
            setSubButton(R.drawable.ic_menu, onClick = { showMenu() })
        }

        context?.let {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer.create(it, notificationUri)
        }
        initClickListeners()
    }

    private fun initClickListeners() {
        binding.timerPlayBtn.setOnClickListener {
            // TODO: 타이머 실행/정지
        }
        binding.timerEndBtn.setOnClickListener {
            // TODO: 타이머 종료
        }
    }


    private fun showMenu() {
        MenuBottomSheetDialog(
            onEditClicked = {
                // TODO: 타이머 수정 기능이 추가되면 구현 예정
            },
            onDeleteClicked = { showDeleteConfirmationDialog() },
            isEditVisible = false // TODO: 타이머 수정 기능이 추가되면 제거 예정
        ).show(parentFragmentManager, MenuBottomSheetDialog::class.java.simpleName)
    }

    private fun showDeleteConfirmationDialog() {
        BaseAlertDialog(
            title = getString(R.string.simple_timer),
            desc = null,
            onConfirmClicked = {
                // TODO: 커스텀 타이머 삭제
            }
        ).show(parentFragmentManager, BaseAlertDialog::class.java.simpleName)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        mediaPlayer = null
        progressAnimator?.cancel()
        progressAnimator = null
    }
}