package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.unischedule.R
import com.example.unischedule.data.database.UniversityDatabase
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

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

            lifecycleScope.launch {
                val db = UniversityDatabase.getDatabase(requireContext(), lifecycleScope)
                val dao = db.universityDao()

                // 1. Check Admin
                val admin = dao.getAdminByUsername(username)
                if (admin != null && admin.passwordHash == password) {
                    UserSession.userId = admin.id
                    UserSession.userRole = UserSession.Role.ADMIN
                    UserSession.userName = admin.username
                    findNavController().navigate(R.id.action_loginFragment_to_nav_dashboard)
                    return@launch
                }

                // 2. Check Instructor
                val instructor = dao.getInstructorByEmail(username)
                if (instructor != null && instructor.passwordHash == password) {
                    UserSession.userId = instructor.id
                    UserSession.userRole = UserSession.Role.INSTRUCTOR
                    UserSession.userName = instructor.name
                    findNavController().navigate(R.id.action_loginFragment_to_instructorDashboardFragment)
                    return@launch
                }
                
                Toast.makeText(context, "Invalid credentials", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
