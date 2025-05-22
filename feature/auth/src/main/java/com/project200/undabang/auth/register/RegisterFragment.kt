package com.project200.undabang.auth.register

import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.project200.domain.model.SignUpResult
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.navigator.ActivityNavigator
import com.project200.presentation.base.DatePickerDialogFragment
import com.project200.undabang.feature.auth.R
import com.project200.undabang.feature.auth.databinding.FragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint
// import timber.log.Timber // Timber 사용하지 않으면 제거
import javax.inject.Inject

@AndroidEntryPoint
class RegisterFragment : BindingFragment<FragmentRegisterBinding>(R.layout.fragment_register) {

    private val viewModel: RegisterViewModel by viewModels()

    @Inject
    lateinit var appNavigator: ActivityNavigator

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
            result?.let { signUpResult ->
                when (signUpResult) {
                    is SignUpResult.Success -> {
                        Toast.makeText(requireContext(), "회원가입 성공!", Toast.LENGTH_SHORT).show()
                        appNavigator.navigateToMain(requireContext())
                    }
                    is SignUpResult.Failure -> {
                        Toast.makeText(requireContext(), signUpResult.errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    companion object {
        const val MALE = "M"
        const val FEMALE = "F"
        const val HIDDEN = "U"
    }
}