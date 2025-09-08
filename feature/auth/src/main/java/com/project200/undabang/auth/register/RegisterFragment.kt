package com.project200.undabang.auth.register

import android.view.View
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginStart
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.SignUpResult
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.navigator.ActivityNavigator
import com.project200.presentation.base.DatePickerDialogFragment
import com.project200.undabang.feature.auth.R
import com.project200.undabang.feature.auth.databinding.FragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
// import timber.log.Timber // Timber 사용하지 않으면 제거
import javax.inject.Inject

@AndroidEntryPoint
class RegisterFragment : BindingFragment<FragmentRegisterBinding>(R.layout.fragment_register) {

    private val viewModel: RegisterViewModel by viewModels()

    @Inject
    lateinit var appNavigator: ActivityNavigator

    @Inject
    lateinit var clockProvider: ClockProvider

    override fun getViewBinding(view: View): FragmentRegisterBinding {
        return FragmentRegisterBinding.bind(view)
    }

    override fun setupViews() = with(binding) {
        setupMargin()

        nicknameEt.doAfterTextChanged {
            viewModel.updateNickname(it.toString())
        }

        birthDayTv.setOnClickListener {
            val datePicker = DatePickerDialogFragment(
                initialDateString = viewModel.birth.value,
                maxDate = clockProvider.now().minusDays(1)) { selectedDate ->
                viewModel.updateBirth(selectedDate)
            }
            datePicker.show(parentFragmentManager, DatePickerDialogFragment::class.java.simpleName)
        }

        genderMaleLl.setOnClickListener {
            viewModel.selectGender(MALE)
        }

        genderFemaleLl.setOnClickListener {
            viewModel.selectGender(FEMALE)
        }

        genderHiddenLl.setOnClickListener {
            viewModel.selectGender(HIDDEN)
        }

        registerCompleteBtn.setOnClickListener {
            viewModel.signUp()
        }
    }

    override fun setupObservers() = with(viewModel) {
        birth.observe(viewLifecycleOwner) { birthDay ->
            binding.birthDayTv.text = birthDay ?: getString(R.string.birth_default)
        }

        gender.observe(viewLifecycleOwner) { gender ->
            binding.maleBtnImv.isSelected = gender == MALE
            binding.femaleBtnImv.isSelected = gender == FEMALE
            binding.hiddenBtnImv.isSelected = gender == HIDDEN
        }

        isFormValid.observe(viewLifecycleOwner) { isValid ->
            binding.registerCompleteBtn.isEnabled = isValid
        }

        signUpResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is BaseResult.Success -> {
                    Toast.makeText(requireContext(), getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                    appNavigator.navigateToMain(requireContext())
                }
                is BaseResult.Error -> {
                    when(result.errorCode) {
                        NICKNAME_DUPLICATE_ERROR -> {
                            Toast.makeText(requireContext(), getString(R.string.error_nickname_duplicated), Toast.LENGTH_LONG).show()
                        }
                        ERROR_CODE_INVALID_NICKNAME -> {
                            Toast.makeText(requireContext(), getString(R.string.error_nickname_invalid), Toast.LENGTH_LONG).show()
                        }
                        FORM_INCOMPLETE -> {
                            Toast.makeText(requireContext(), getString(R.string.error_form_incomplete), Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Toast.makeText(requireContext(), getString(R.string.error_unknown), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    // 시스템 폰트 크기에 따라 marginStart 조정
    private fun setupMargin() {
        val fontScale = resources.configuration.fontScale
        Timber.tag("RegisterFragment").d("Font scale: $fontScale")

        val layoutParams = binding.nicknameEt.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.marginStart = resources.getDimensionPixelSize(
            if(fontScale > FONT_SCALE_THRESHOLD) R.dimen.nickname_horizontal_margin_large_font else R.dimen.nickname_horizontal_margin
        )
        binding.nicknameEt.layoutParams = layoutParams
        binding.nicknameEt.requestLayout()
    }

    companion object {
        const val NICKNAME_DUPLICATE_ERROR = "409"
        const val MALE = "M"
        const val FEMALE = "F"
        const val HIDDEN = "U"
        const val FONT_SCALE_THRESHOLD = 1.5f // 폰트 크기 임계값
        const val ERROR_CODE_INVALID_NICKNAME = "INVALID_NICKNAME"
        const val FORM_INCOMPLETE = "FORM_INCOMPLETE"
    }
}