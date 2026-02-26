package no.stunor.origo.organiser.data

import no.stunor.origo.organiser.model.courses.Course
import no.stunor.origo.organiser.model.courses.CourseVariant
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.*

@Repository
open class CourseRepository(private val jdbcTemplate: JdbcTemplate) {

    private val courseRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Course(
            id = UUID.fromString(rs.getString("id")),
            name = rs.getString("name"),
            mapId = rs.getString("map_id")?.let { UUID.fromString(it) }
        )
    }

    private val variantRowMapper = RowMapper { rs: ResultSet, _: Int ->
        CourseVariant(
            id = UUID.fromString(rs.getString("id")),
            name = rs.getString("name"),
            length = rs.getObject("length") as? Double,
            climb = rs.getObject("climb") as? Double,
            courseId = rs.getString("course_id")?.let { UUID.fromString(it) },
            printedMaps = rs.getObject("printed_maps") as? Int
        )
    }

    open fun save(course: Course): Course {
        if (course.id == null) {
            course.id = UUID.randomUUID()
        }

        val exists = (jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM course WHERE id = ?",
            Int::class.java,
            course.id
        ) ?: 0) > 0

        if (exists) {
            jdbcTemplate.update(
                """
                UPDATE course 
                SET name = ?, map_id = ? 
                WHERE id = ?
                """,
                course.name,
                course.mapId,
                course.id
            )
        } else {
            jdbcTemplate.update(
                """
                INSERT INTO course (id, name, map_id) 
                VALUES (?, ?, ?)
                """,
                course.id,
                course.name,
                course.mapId
            )
        }

        // Save variants
        course.variants.forEach { variant ->
            saveVariant(variant, course.id!!)
        }

        return course
    }

    private fun saveVariant(variant: CourseVariant, courseId: UUID) {
        if (variant.id == null) {
            variant.id = UUID.randomUUID()
        }
        variant.courseId = courseId

        val exists = (jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM course_variant WHERE id = ?",
            Int::class.java,
            variant.id
        ) ?: 0) > 0

        if (exists) {
            jdbcTemplate.update(
                """
                UPDATE course_variant 
                SET name = ?, length = ?, climb = ?, course_id = ?, printed_maps = ? 
                WHERE id = ?
                """,
                variant.name,
                variant.length,
                variant.climb,
                variant.courseId,
                variant.printedMaps,
                variant.id
            )
        } else {
            jdbcTemplate.update(
                """
                INSERT INTO course_variant (id, name, length, climb, course_id, printed_maps) 
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                variant.id,
                variant.name,
                variant.length,
                variant.climb,
                variant.courseId,
                variant.printedMaps
            )
        }
    }

}
