package com.project200.undabang.auth.register

import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.project200.domain.model.SignUpResult
import com.project200.undabang.main.MainActivity
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.DatePickerDialogFragment
import com.project200.undabang.feature.auth.R
import com.project200.undabang.feature.auth.databinding.FragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class RegisterFragment : BindingFragment<FragmentRegisterBinding>(R.layout.fragment_register) {

    private val viewModel: RegisterViewModel by viewModels()

    override fun getViewBinding(view: View): FragmentRegisterBinding {
        return FragmentRegisterBinding.bind(view)
    }

    override fun setupViews() = with(binding) {
        nicknameEt.doAfterTextChanged {
            viewModel.updateNickname(it.toString())
        }

        birthDayTv.setOnClickListener {
            val datePicker = DatePickerDialogFragment(viewModel.birth.value) { selectedDate ->
                viewModel.updateBirth(selectedDate)
            }
            datePicker.show(parentFragmentManager, DatePickerDialogFragment::class.java.simpleName)
        }

        maleBtnImv.setOnClickListener {
            viewModel.selectGender(MALE)
        }

        femaleBtnImv.setOnClickListener {
            viewModel.selectGender(FEMALE)
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
        }

        isFormValid.observe(viewLifecycleOwner) { isValid ->
            binding.registerCompleteBtn.isEnabled = isValid
        }

        signUpResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                when (it) {
                    is SignUpResult.Success -> {
                        val memberId = it.memberId
                        Timber.d("회원가입 성공! Member ID: $memberId")
                        startActivity(Intent(requireContext(), com.project200.undabang.main.MainActivity::class.java))
                        requireActivity().finish()
                    }
                    is SignUpResult.Failure -> {
                        val displayMessage = when (it.errorCode) {
                            "MEMBER_NICKNAME_DUPLICATED" -> "이미 사용 중인 닉네임입니다."
                            else -> it.errorCode
                        }
                        Toast.makeText(requireContext(), displayMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    companion object {
        const val MALE = "male"
        const val FEMALE = "female"
    }
}
