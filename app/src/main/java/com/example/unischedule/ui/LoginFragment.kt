package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.unischedule.R
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.databinding.FragmentLoginBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.LoginViewModel
import com.example.unischedule.viewmodel.LoginViewModelFactory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(FirestoreRepository(FirebaseFirestore.getInstance()))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginButton.setOnClickListener {
            val username = binding.usernameEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.login(username, password)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginState.collect { state ->
                    when (state) {
                        is UiState.Loading -> Unit
                        is UiState.Error -> Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                        is UiState.Success -> handleLoginSuccess(state.data)
                    }
                }
            }
        }
    }

    private fun handleLoginSuccess(user: com.example.unischedule.data.repository.AuthenticatedUser) {
        UserSession.userId = user.id
        UserSession.userRole = user.role
        UserSession.userName = user.username

        val destination = when {
            user.mustChangePassword -> R.id.action_loginFragment_to_passwordChangeFragment
            user.role == UserSession.Role.ADMIN -> R.id.action_loginFragment_to_nav_dashboard
            else -> R.id.action_loginFragment_to_instructorDashboardFragment
        }

        findNavController().navigate(destination)
        viewModel.resetState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
