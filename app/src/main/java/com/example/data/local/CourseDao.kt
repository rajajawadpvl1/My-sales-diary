package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY name COLLATE NOCASE ASC")
    fun observeAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllCoursesOnce(): List<CourseEntity>

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun countCourses(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(courses: List<CourseEntity>)

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CourseEntity?

    @Query("UPDATE courses SET name = :newName, price = :newPrice WHERE id = :id")
    suspend fun updateCourse(id: Long, newName: String, newPrice: String)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourseById(id: Long)

    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()

    @Transaction
    suspend fun replaceAllCourses(courses: List<CourseEntity>) {
        deleteAllCourses()
        if (courses.isNotEmpty()) {
            insertAll(courses)
        }
    }
}
