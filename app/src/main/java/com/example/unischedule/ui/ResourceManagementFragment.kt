package com.example.unischedule.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.unischedule.data.database.UniversityDatabase
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.databinding.FragmentResourceManagementBinding
import com.example.unischedule.util.ExcelHelper
import com.example.unischedule.viewmodel.AdminViewModel
import com.example.unischedule.viewmodel.ViewModelFactory
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.adapter.FragmentStateAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResourceManagementFragment : Fragment() {

    private var _binding: FragmentResourceManagementBinding? = null
    private val binding get() = _binding!!
    private val TAG = "ResourceMgmtLifecycle"

    private val viewModel: AdminViewModel by viewModels {
        val db = UniversityDatabase.getDatabase(requireContext(), lifecycleScope)
        ViewModelFactory(UniversityRepository(db.universityDao()))
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importDataFromExcel(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResourceManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ResourcePagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Faculties"
                1 -> "Departments"
                2 -> "Instructors"
                else -> "Classrooms"
            }
        }.attach()
        
        // Use standard Bundle to get selectedTab
        val selectedTab = arguments?.getInt("selectedTab") ?: 0
        binding.viewPager.setCurrentItem(selectedTab, false)

        binding.btnImportExcel.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            importLauncher.launch(Intent.createChooser(intent, "Select Excel File"))
        }
    }

    private fun importDataFromExcel(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val importedCourses = ExcelHelper.importCoursesFromExcel(inputStream)
                    importedCourses.forEach { course ->
                        viewModel.addCourse(course)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Imported ${importedCourses.size} courses", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ResourcePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 4
        override fun createFragment(position: Int): Fragment {
            return BaseResourceListFragment.newInstance(position)
        }
    }
}
