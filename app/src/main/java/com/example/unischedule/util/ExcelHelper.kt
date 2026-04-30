package com.example.unischedule.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.firestore.Course as FirestoreCourse
import com.example.unischedule.data.firestore.Department
import com.example.unischedule.data.entity.Course
import com.example.unischedule.data.entity.Schedule
import com.example.unischedule.data.imports.ImportedLecturerAccount
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.util.PasswordHasher
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

data class LecturerImportRow(
    val lecturer: Lecturer,
    val initialPassword: String
)

object ExcelHelper {

    fun importLecturersFromExcel(inputStream: InputStream): List<LecturerImportRow> {
        val lecturers = mutableListOf<LecturerImportRow>()
        try {
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val fullName = row.getCell(0)?.toString()?.trim().orEmpty()
                if (fullName.isBlank()) continue

                val departmentId = row.getCell(1)?.toString()?.trim()?.replace(".0", "")?.toLongOrNull() ?: 0L
                val initialPassword = StringUtil.generateInitialPassword()
                val username = StringUtil.generateUsername(fullName)

                lecturers.add(
                    LecturerImportRow(
                        lecturer = Lecturer(
                            fullName = fullName,
                            departmentId = departmentId,
                            username = username,
                            passwordHash = PasswordHasher.sha256(initialPassword),
                            role = "Lecturer",
                            mustChangePassword = true
                        ),
                        initialPassword = initialPassword
                    )
                )
            }

            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return lecturers
    }

    /**
     * Imports courses from an Excel (.xlsx) file.
     * Updated Columns: Code, Name, Year, Semester, IsMandatory
     */
    fun importCoursesFromExcel(inputStream: InputStream): List<Course> {
        val courses = mutableListOf<Course>()
        try {
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            
            for (i in 1..sheet.lastRowNum) { // Skip header
                val row = sheet.getRow(i) ?: continue
                val code = row.getCell(0)?.toString() ?: ""
                val name = row.getCell(1)?.toString() ?: ""
                val year = row.getCell(2)?.numericCellValue?.toInt() ?: 1
                val semester = row.getCell(3)?.numericCellValue?.toInt() ?: 1
                val isMandatoryStr = row.getCell(4)?.toString() ?: "true"
                val isMandatory = isMandatoryStr.lowercase() == "true" || isMandatoryStr == "1"

                courses.add(Course(
                    departmentId = 1, // Default or handle based on logic
                    code = code,
                    name = name,
                    year = year,
                    semester = semester,
                    isMandatory = isMandatory
                ))
            }
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return courses
    }

    /**
     * Imports courses from Excel AND resolves Department name (Col 0) to a Firestore
     * departmentId. If the department doesn't exist, it is created first.
     *
     * Expected columns: Department, Code, Name, Year, Semester, IsMandatory
     */
    suspend fun importCoursesWithDepartment(
        inputStream: InputStream,
        repository: FirestoreRepository
    ): List<FirestoreCourse> {
        val courses = mutableListOf<FirestoreCourse>()
        try {
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            // Cache existing departments so we don't query per row
            val deptCache = mutableMapOf<String, Long>() // name → id

            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val deptName = row.getCell(0)?.toString()?.trim().orEmpty()
                val code = row.getCell(1)?.toString()?.trim().orEmpty()
                val name = row.getCell(2)?.toString()?.trim().orEmpty()
                if (code.isBlank() && name.isBlank()) continue

                val year = try { row.getCell(3)?.numericCellValue?.toInt() ?: 1 } catch (_: Exception) { 1 }
                val semester = try { row.getCell(4)?.numericCellValue?.toInt() ?: 1 } catch (_: Exception) { 1 }
                val isMandatoryStr = row.getCell(5)?.toString()?.trim() ?: "true"
                val isMandatory = isMandatoryStr.lowercase() == "true" || isMandatoryStr == "1"

                // Resolve departmentId
                val departmentId: Long = if (deptName.isNotBlank()) {
                    deptCache.getOrPut(deptName.lowercase()) {
                        // Try to find existing department
                        val existing = repository.findDepartmentByName(deptName)
                        if (existing != null) {
                            existing.id
                        } else {
                            // Create new department
                            val newId = repository.getNextIdForCollection("departments")
                            val newDept = Department(id = newId, name = deptName)
                            repository.addDepartment(newDept)
                            newId
                        }
                    }
                } else 0L

                courses.add(FirestoreCourse(
                    id = (1000L..99999L).random(),
                    departmentId = departmentId,
                    code = code,
                    name = name,
                    year = year,
                    semester = semester,
                    isMandatory = isMandatory
                ))
            }
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return courses
    }

    /**
     * Exports the schedule to an Excel file and returns the URI for sharing.
     */
    fun exportScheduleToExcel(context: Context, schedules: List<Schedule>): Uri? {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Timetable")

        // Create Header
        val header = sheet.createRow(0)
        header.createCell(0).setCellValue("Day")
        header.createCell(1).setCellValue("Start Time")
        header.createCell(2).setCellValue("End Time")
        header.createCell(3).setCellValue("Course ID")
        header.createCell(4).setCellValue("Instructor ID")
        header.createCell(5).setCellValue("Classroom ID")

        // Fill Data
        schedules.forEachIndexed { index, schedule ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(getDayName(schedule.dayOfWeek))
            row.createCell(1).setCellValue(schedule.startTime)
            row.createCell(2).setCellValue(schedule.endTime)
            row.createCell(3).setCellValue(schedule.courseId.toDouble())
            row.createCell(4).setCellValue(schedule.instructorId.toDouble())
            row.createCell(5).setCellValue(schedule.classroomId.toDouble())
        }

        return try {
            val fileName = "University_Timetable.xlsx"
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            workbook.write(outputStream)
            outputStream.close()
            workbook.close()

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareExcelFile(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Timetable via"))
    }

    // ═══════════════════════════════════════════════════════════════
    // Export courses to Downloads
    // ═══════════════════════════════════════════════════════════════

    data class CourseExportRow(
        val department: String,
        val courseName: String,
        val courseCode: String,
        val instructor: String,
        val timeSlot: String,
        val classroom: String
    )

    fun exportCoursesToDownloads(context: Context, rows: List<CourseExportRow>, fileName: String = "courses_export.xlsx"): Boolean {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Courses")

        val headerStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }

        val header = sheet.createRow(0)
        val headers = listOf("Department", "Course Name", "Course Code", "Instructor", "Time Slot", "Classroom")
        headers.forEachIndexed { i, h ->
            header.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        rows.forEachIndexed { index, row ->
            val r = sheet.createRow(index + 1)
            r.createCell(0).setCellValue(row.department)
            r.createCell(1).setCellValue(row.courseName)
            r.createCell(2).setCellValue(row.courseCode)
            r.createCell(3).setCellValue(row.instructor)
            r.createCell(4).setCellValue(row.timeSlot)
            r.createCell(5).setCellValue(row.classroom)
        }

        return writeWorkbookToDownloads(context, workbook, fileName)
    }

    // ═══════════════════════════════════════════════════════════════
    // Export schedule to Downloads
    // ═══════════════════════════════════════════════════════════════

    data class ScheduleExportRow(
        val department: String,
        val instructor: String,
        val courseName: String,
        val courseCode: String,
        val classroom: String,
        val day: String,
        val timeSlot: String
    )

    fun exportScheduleToDownloads(context: Context, rows: List<ScheduleExportRow>, fileName: String = "schedule_export.xlsx"): Boolean {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Schedule")

        val headerStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }

        val header = sheet.createRow(0)
        val headers = listOf("Department", "Instructor", "Course Name", "Course Code", "Classroom", "Day", "Time Slot")
        headers.forEachIndexed { i, h ->
            header.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        rows.forEachIndexed { index, row ->
            val r = sheet.createRow(index + 1)
            r.createCell(0).setCellValue(row.department)
            r.createCell(1).setCellValue(row.instructor)
            r.createCell(2).setCellValue(row.courseName)
            r.createCell(3).setCellValue(row.courseCode)
            r.createCell(4).setCellValue(row.classroom)
            r.createCell(5).setCellValue(row.day)
            r.createCell(6).setCellValue(row.timeSlot)
        }

        return writeWorkbookToDownloads(context, workbook, fileName)
    }

    // ═══════════════════════════════════════════════════════════════
    // Sample files
    // ═══════════════════════════════════════════════════════════════

    fun generateCourseImportSample(context: Context): Boolean {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sample")

        val headerStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }

        val headers = listOf("Department", "Lecturer Name", "Course Name", "Course Code",
            "Course Class", "Course Time", "Course Duration", "Classroom Type", "Password", "Lecturer")
        val header = sheet.createRow(0)
        headers.forEachIndexed { i, h ->
            header.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        val sample = sheet.createRow(1)
        sample.createCell(0).setCellValue("Computer Engineering")
        sample.createCell(1).setCellValue("Zeshan Iqbal")
        sample.createCell(2).setCellValue("Computer Networks")
        sample.createCell(3).setCellValue("CNG 386")
        sample.createCell(4).setCellValue("103")
        sample.createCell(5).setCellValue("13:00-16:00")
        sample.createCell(6).setCellValue("3 Hours")
        sample.createCell(7).setCellValue("Lab")
        sample.createCell(8).setCellValue("ZeshanIqbal123")
        sample.createCell(9).setCellValue("Assist. Prof. Zeshan Iqbal")

        return writeWorkbookToDownloads(context, workbook, "sample_import.xlsx")
    }

    fun generateScheduleImportSample(context: Context): Boolean {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sample")

        val headerStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }

        val headers = listOf("Department", "Instructor", "Course Code", "Classroom", "Day", "Time Slot")
        val header = sheet.createRow(0)
        headers.forEachIndexed { i, h ->
            header.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        val sample = sheet.createRow(1)
        sample.createCell(0).setCellValue("Computer Engineering")
        sample.createCell(1).setCellValue("Assist. Prof. Zeshan Iqbal")
        sample.createCell(2).setCellValue("CNG 386")
        sample.createCell(3).setCellValue("103")
        sample.createCell(4).setCellValue("Monday")
        sample.createCell(5).setCellValue("13:00-16:00")

        return writeWorkbookToDownloads(context, workbook, "sample_schedule_import.xlsx")
    }

    // ═══════════════════════════════════════════════════════════════
    // Utility: Write workbook to Downloads (API 26–28 → file, API 29+ → MediaStore)
    // ═══════════════════════════════════════════════════════════════

    private fun writeWorkbookToDownloads(context: Context, workbook: XSSFWorkbook, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+ → MediaStore (no WRITE_EXTERNAL_STORAGE needed)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        workbook.write(outputStream)
                    }
                }
            } else {
                // API 26-28 → Direct file write (requires WRITE_EXTERNAL_STORAGE)
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    workbook.write(outputStream)
                }
            }
            workbook.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            workbook.close()
            false
        }
    }

    private fun getDayName(day: Int): String = when (day) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Unknown"
    }
}
