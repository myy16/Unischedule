package com.example.unischedule.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.unischedule.data.dao.UniversityDao
import com.example.unischedule.data.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Admin::class,
        Faculty::class,
        Department::class,
        Instructor::class,
        Classroom::class,
        Course::class,
        InstructorAvailability::class,
        Schedule::class
    ],
    version = 4,
    exportSchema = false
)
abstract class UniversityDatabase : RoomDatabase() {

    abstract fun universityDao(): UniversityDao

    companion object {
        @Volatile
        private var INSTANCE: UniversityDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): UniversityDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UniversityDatabase::class.java,
                    "university_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(UniversityDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class UniversityDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val dao = database.universityDao()
                    if (dao.getAdminByUsername("admin") == null) {
                        populateDatabase(dao)
                    }
                }
            }
        }

        suspend fun populateDatabase(dao: UniversityDao) {
            dao.insertAdmin(Admin(username = "admin", passwordHash = "admin123"))

            val engineeringId = dao.insertFaculty(Faculty(name = "Faculty of Engineering"))
            val agricultureId = dao.insertFaculty(Faculty(name = "Faculty of Agriculture"))

            val deptCNG = dao.insertDepartment(Department(facultyId = engineeringId, name = "Computer Engineering"))
            val deptMCE = dao.insertDepartment(Department(facultyId = engineeringId, name = "Mechanical Engineering"))
            val deptASE = dao.insertDepartment(Department(facultyId = engineeringId, name = "Aerospace Engineering"))
            val deptPLP = dao.insertDepartment(Department(facultyId = agricultureId, name = "Plant Protection"))
            val deptBSE = dao.insertDepartment(Department(facultyId = agricultureId, name = "Biosystems Engineering"))

            dao.insertInstructor(Instructor(departmentId = deptCNG, name = "Halit Bakır", email = "halit.bakir", title = "Assoc. Prof.", passwordHash = "pass123"))
            dao.insertInstructor(Instructor(departmentId = deptCNG, name = "Rezan Bakır", email = "rezan.bakir", title = "Assist. Prof.", passwordHash = "pass123"))
            dao.insertInstructor(Instructor(departmentId = deptCNG, name = "Porkodi Sivaram", email = "porkodi.s", title = "Assist. Prof.", passwordHash = "pass123"))
            dao.insertInstructor(Instructor(departmentId = deptASE, name = "Nurbanu Güzey", email = "nurbanu.g", title = "Assist. Prof.", passwordHash = "pass123"))
            dao.insertInstructor(Instructor(departmentId = deptMCE, name = "Metin Zontul", email = "metin.zontul", title = "Prof. Dr.", passwordHash = "pass123"))
            dao.insertInstructor(Instructor(departmentId = deptPLP, name = "Sivaram Murugan", email = "sivaram.m", title = "Prof. Dr.", passwordHash = "pass123"))

            dao.insertClassroom(Classroom(name = "Amphi-A", capacity = 100, isLab = false))
            dao.insertClassroom(Classroom(name = "Computer Lab-1", capacity = 30, isLab = true))
            dao.insertClassroom(Classroom(name = "Tech-Room 202", capacity = 40, isLab = false))
            dao.insertClassroom(Classroom(name = "General Class 105", capacity = 60, isLab = false))

            dao.insertCourse(Course(departmentId = deptCNG, code = "CNG 101", name = "Intro to Computer Eng.", year = 1, semester = 1, isMandatory = true))
            dao.insertCourse(Course(departmentId = deptCNG, code = "CNG 103", name = "Intro to Programming", year = 1, semester = 1, isMandatory = true))
            dao.insertCourse(Course(departmentId = deptMCE, code = "MCE 102", name = "Statics", year = 1, semester = 1, isMandatory = true))
            dao.insertCourse(Course(departmentId = deptCNG, code = "CNG 346", name = "Mobile Programming", year = 3, semester = 5, isMandatory = false))
            dao.insertCourse(Course(departmentId = deptASE, code = "ASE 204", name = "Digital System Design", year = 2, semester = 3, isMandatory = true))
            dao.insertCourse(Course(departmentId = deptPLP, code = "PLP 372", name = "Internet of Things (Agri)", year = 4, semester = 7, isMandatory = false))
        }
    }
}
