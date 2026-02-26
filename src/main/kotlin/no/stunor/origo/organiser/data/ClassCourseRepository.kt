package no.stunor.origo.organiser.data

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.*

@Repository
open class ClassCourseRepository(private val jdbcTemplate: JdbcTemplate) {

    open fun linkClassToCourse(classId: UUID, courseId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO class_course (class_id, course_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            classId, courseId
        )
    }

}
