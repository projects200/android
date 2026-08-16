package com.project200.undabang.auth.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.presentation.base.DatePickerDialogFragment
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.navigator.ActivityNavigator
import com.project200.undabang.feature.auth.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RegisterFragment : Fragment() {
    private val viewModel: RegisterViewModel by viewModels()

    @Inject
    lateinit var appNavigator: ActivityNavigator

    @Inject
    lateinit var clockProvider: ClockProvider

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val nickname by viewModel.nickname.collectAsStateWithLifecycle()
                val birth by viewModel.birth.collectAsStateWithLifecycle()
                val gender by viewModel.gender.collectAsStateWithLifecycle()
                val isFormValid by viewModel.isFormValid.collectAsStateWithLifecycle()

                RegisterScreen(
                    nickname = nickname,
                    birth = birth,
                    gender = gender,
                    isFormValid = isFormValid,
                    onNicknameChange = viewModel::updateNickname,
                    onBirthClick = ::showDatePicker,
                    onGenderSelect = viewModel::selectGender,
                    onCompleteClick = viewModel::signUp,
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.signUpResult.collect { result ->
                    when (result) {
                        is BaseResult.Success -> {
                            Toast.makeText(requireContext(), getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                            appNavigator.navigateToMain(requireContext())
                        }
                        is BaseResult.Error -> {
                            val messageResId =
                                when (result.errorCode) {
                                    NICKNAME_DUPLICATE_ERROR -> R.string.error_nickname_duplicated
                                    ERROR_CODE_INVALID_NICKNAME -> R.string.error_nickname_invalid
                                    FORM_INCOMPLETE -> R.string.error_form_incomplete
                                    else -> R.string.error_unknown
                                }
                            Toast.makeText(requireContext(), getString(messageResId), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun showDatePicker() {
        DatePickerDialogFragment.show(
            fragmentManager = parentFragmentManager,
            initialDateString = viewModel.birth.value,
            maxDate = clockProvider.now().minusDays(1),
        ) { selectedDate ->
            viewModel.updateBirth(selectedDate)
        }
    }

    companion object {
        const val NICKNAME_DUPLICATE_ERROR = "MEMBER_NICKNAME_DUPLICATED"
        const val MALE = "M"
        const val FEMALE = "F"
        const val HIDDEN = "U"
        const val ERROR_CODE_INVALID_NICKNAME = "INVALID_NICKNAME"
        const val FORM_INCOMPLETE = "FORM_INCOMPLETE"
    }
}
