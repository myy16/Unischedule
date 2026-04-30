package com.example.unischedule.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.entity.Course
import com.example.unischedule.data.entity.Schedule
import com.example.unischedule.data.imports.ImportedLecturerAccount
import com.example.unischedule.util.PasswordHasher
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
