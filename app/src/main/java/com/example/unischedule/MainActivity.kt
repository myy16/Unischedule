package com.example.unischedule

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.data.database.UniversityDatabase
import com.example.unischedule.data.firestore.Faculty as FirestoreFaculty
import com.example.unischedule.data.firestore.Department as FirestoreDepartment
import com.example.unischedule.data.firestore.Classroom as FirestoreClassroom
import com.example.unischedule.data.firestore.Course as FirestoreCourse
import com.example.unischedule.databinding.ActivityMainBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.unischedule.viewmodel.UserRole
import com.example.unischedule.viewmodel.UserViewModel
import com.example.unischedule.viewmodel.UserViewModelFactory

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivityLifecycle"
    private val firestoreRepository by lazy { FirestoreRepository(FirebaseFirestore.getInstance()) }
    private val userViewModel: UserViewModel by viewModels {
        UserViewModelFactory(firestoreRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            try {
                // Step 1: Seed test lecturer
                firestoreRepository.seedBaharKulogluLecturerIfMissing()
                Log.d(TAG, "Lecturer seed completed")
                
                // Step 2: Seed admin user
                seedAdminIfMissing()
                
                // Step 3: Migrate Room data to Firestore (one-time operation)
                migrateRoomDataToFirestore()
                
            } catch (e: Exception) {
                Log.w(TAG, "Initialization failed", e)
            }
        }

        setSupportActionBar(binding.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController: NavController = navHostFragment.navController

        // Setting up destinations where the drawer should NOT show
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_dashboard, R.id.nav_resources, R.id.nav_courses, R.id.nav_scheduler, R.id.instructorDashboardFragment
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.roleState.collect { role ->
                    updateMenuVisibility(navView, role)
                }
            }
        }

        userViewModel.loadProfileFromFirebaseAuth()

        // Handle logout separately
        navView.setNavigationItemSelectedListener { menuItem ->
            if (menuItem.itemId == R.id.nav_logout) {
                UserSession.logout()
                navController.navigate(R.id.loginFragment)
                drawerLayout.closeDrawers()
                true
            } else {
                // Default behavior for other items
                val handled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(menuItem, navController)
                if (handled) drawerLayout.closeDrawers()
                handled
            }
        }

        // Hide toolbar and drawer on Login screen; swap drawer menu by role
        navController.addOnDestinationChangedListener { _, destination: NavDestination, _ ->
            if (destination.id == R.id.loginFragment || destination.id == R.id.passwordChangeFragment) {
                binding.toolbar.visibility = View.GONE
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            } else {
                binding.toolbar.visibility = View.VISIBLE
                userViewModel.loadCurrentUserProfile(UserSession.userId?.toString())
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

                // Phase 2: Swap drawer menu based on role
                val isLecturer = UserSession.userRole == UserSession.Role.LECTURER ||
                    destination.id == R.id.instructorDashboardFragment ||
                    destination.id == R.id.calendarFragment ||
                    destination.id == R.id.availabilityFragment
                val menuRes = if (isLecturer) R.menu.instructor_drawer_menu else R.menu.admin_drawer_menu
                navView.menu.clear()
                navView.inflateMenu(menuRes)
            }
        }
    }

    private fun updateMenuVisibility(navigationView: NavigationView, role: UserRole) {
        val menu = navigationView.menu
        val isAdmin = role == UserRole.ADMIN || UserSession.userRole == UserSession.Role.ADMIN

        // Required explicit section visibility for RBAC.
        menu.findItem(R.id.admin_section)?.isVisible = isAdmin

        val adminOnlyItems = listOf(
            R.id.nav_faculty,
            R.id.nav_rooms,
            R.id.nav_add_course,
            R.id.nav_resources,
            R.id.nav_courses,
            R.id.nav_scheduler,
            R.id.nav_dashboard
        )

        adminOnlyItems.forEach { itemId ->
            menu.findItem(itemId)?.isVisible = isAdmin
        }
    }

    private suspend fun seedAdminIfMissing() {
        try {
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("admins")
                .limit(1)
                .get()
                .await()

            if (snapshot.isEmpty) {
                val adminObj = com.example.unischedule.data.firestore.AdminAccount(
                    id = 1L,
                    username = "admin",
                    passwordHash = com.example.unischedule.util.PasswordHasher.sha256("Admin123"),
                    role = "Admin",
                    mustChangePassword = false
                )
                db.collection("admins").document("1").set(adminObj).await()
                Log.d(TAG, "Admin created successfully")
            } else {
                Log.d(TAG, "Admins collection already has at least one admin. Skipping creation.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding admin", e)
        }
    }

    private suspend fun migrateRoomDataToFirestore() {
        try {
            val database = UniversityDatabase.getDatabase(this, lifecycleScope)
            val dao = database.universityDao()

            Log.d(TAG, "Starting Room → Firestore migration")

            val db = FirebaseFirestore.getInstance()

            // Migrate Faculties
            val existingFaculties = db.collection("faculties").get().await().documents.mapNotNull { it.id.toLongOrNull() }.toSet()

            val roomFaculties = dao.getAllFacultiesSync()
            var facultyMigratedCount = 0
            roomFaculties.forEach { faculty ->
                if (!existingFaculties.contains(faculty.id)) {
                    firestoreRepository.addFaculty(
                        FirestoreFaculty(id = faculty.id, name = faculty.name)
                    )
                    facultyMigratedCount++
                }
            }
            Log.d(TAG, "Migrated $facultyMigratedCount faculties")

            // Migrate Departments
            val existingDepartments = db.collection("departments").get().await().documents.mapNotNull { it.id.toLongOrNull() }.toSet()

            val roomDepartments = dao.getAllDepartmentsSync()
            var deptMigratedCount = 0
            roomDepartments.forEach { dept ->
                if (!existingDepartments.contains(dept.id)) {
                    firestoreRepository.addDepartment(
                        FirestoreDepartment(id = dept.id, facultyId = dept.facultyId, name = dept.name)
                    )
                    deptMigratedCount++
                }
            }
            Log.d(TAG, "Migrated $deptMigratedCount departments")

            // Migrate Courses
            val existingCourses = db.collection("courses").get().await().documents.mapNotNull { it.id.toLongOrNull() }.toSet()

            val roomCourses = dao.getAllCoursesSync()
            var courseMigratedCount = 0
            roomCourses.forEach { course ->
                if (!existingCourses.contains(course.id)) {
                    firestoreRepository.addCourse(
                        FirestoreCourse(
                            id = course.id, departmentId = course.departmentId, code = course.code,
                            name = course.name, year = course.year, semester = course.semester,
                            isMandatory = course.isMandatory
                        )
                    )
                    courseMigratedCount++
                }
            }
            Log.d(TAG, "Migrated $courseMigratedCount courses")

            // Migrate Classrooms
            val existingClassrooms = db.collection("classrooms").get().await().documents.mapNotNull { it.id.toLongOrNull() }.toSet()

            val roomClassrooms = dao.getAllClassroomsSync()
            var classroomMigratedCount = 0
            roomClassrooms.forEach { classroom ->
                if (!existingClassrooms.contains(classroom.id)) {
                    firestoreRepository.addClassroom(
                        FirestoreClassroom(
                            id = classroom.id, name = classroom.name, capacity = classroom.capacity,
                            isLab = classroom.isLab, isAvailable = true
                        )
                    )
                    classroomMigratedCount++
                }
            }
            Log.d(TAG, "Migrated $classroomMigratedCount classrooms")

            // Migrate Instructors (to Lecturers)
            val existingLecturers = db.collection("lecturers").get().await().documents.mapNotNull { it.id.toLongOrNull() }.toSet()

            val roomInstructors = dao.getAllInstructorsSync()
            var instructorMigratedCount = 0
            roomInstructors.forEach { instructor ->
                if (!existingLecturers.contains(instructor.id)) {
                    val fullName = if (instructor.title.isBlank()) instructor.name else "${instructor.title} ${instructor.name}"
                    val username = instructor.email.substringBefore("@").lowercase()
                    firestoreRepository.addLecturer(
                        com.example.unischedule.data.firestore.Lecturer(
                            id = instructor.id,
                            fullName = fullName,
                            departmentId = instructor.departmentId,
                            username = username,
                            passwordHash = instructor.passwordHash.ifEmpty { com.example.unischedule.util.PasswordHasher.sha256("123456") },
                            role = "Lecturer",
                            mustChangePassword = instructor.passwordHash.isEmpty()
                        )
                    )
                    instructorMigratedCount++
                }
            }
            Log.d(TAG, "Migrated $instructorMigratedCount instructors to lecturers")

            Log.d(TAG, "Room → Firestore migration completed successfully")

        } catch (e: Exception) {
            Log.w(TAG, "Migration failed (this is OK if Firestore already has data or no Room data exists)", e)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
