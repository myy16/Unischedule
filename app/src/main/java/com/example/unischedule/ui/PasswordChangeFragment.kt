package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.unischedule.R
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.databinding.FragmentPasswordChangeBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.PasswordChangeViewModel
import com.example.unischedule.viewmodel.PasswordChangeViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class PasswordChangeFragment : Fragment() {

    private var _binding: FragmentPasswordChangeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PasswordChangeViewModel by viewModels {
        PasswordChangeViewModelFactory(FirestoreRepository(FirebaseFirestore.getInstance()))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPasswordChangeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (UserSession.mustChangePassword(requireContext())) {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                Snackbar.make(binding.root, "Devam etmek için şifrenizi değiştirmelisiniz", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.btnChangePassword.setOnClickListener {
            val currentPass = binding.etCurrentPassword.text.toString()
            val newPass = binding.etNewPassword.text.toString()
            val confirmPass = binding.etConfirmPassword.text.toString()

            // Validation
            binding.errorText.visibility = View.GONE

            if (currentPass.isBlank() || newPass.isBlank() || confirmPass.isBlank()) {
                showError("Tüm alanları doldurun")
                return@setOnClickListener
            }
            if (newPass != confirmPass) {
                showError("Yeni şifreler eşleşmiyor")
                return@setOnClickListener
            }
            if (newPass.length < 6) {
                showError("Şifre en az 6 karakter olmalı")
                return@setOnClickListener
            }
            if (currentPass == newPass) {
                showError("Yeni şifre mevcut şifreden farklı olmalı")
                return@setOnClickListener
            }

            val userId = UserSession.userId
            val role = UserSession.userRole
            if (userId == null || role == null) {
                showError("Oturum bulunamadı, tekrar giriş yapın")
                return@setOnClickListener
            }

            viewModel.changePassword(userId, currentPass, newPass, role)
        }

        // Observe state changes
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.changeState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnChangePassword.isEnabled = true
                        }
                        is UiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            Snackbar.make(binding.root, "Şifre başarıyla değiştirildi!", Snackbar.LENGTH_SHORT).show()
                            
                            // Phase 2: Update local encrypted session to reflect that password was changed
                            val id = UserSession.userId
                            val role = UserSession.userRole
                            val username = UserSession.userName
                            if (id != null && role != null && username != null) {
                                UserSession.save(requireContext(), id, role, username, false)
                            }
                            
                            viewModel.resetState()
                            if (UserSession.userRole == UserSession.Role.ADMIN) {
                                findNavController().navigate(R.id.nav_dashboard)
                            } else {
                                findNavController().navigate(R.id.action_passwordChangeFragment_to_instructorDashboardFragment)
                            }
                        }
                        is UiState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnChangePassword.isEnabled = true
                            showError(state.message)
                        }
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}