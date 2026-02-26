package no.stunor.origo.organiser.data

import no.stunor.origo.organiser.model.courses.Leg
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.*

@Repository
open class LegRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        Leg(
            id = UUID.fromString(rs.getString("id")),
            sequenceNumber = rs.getInt("sequence_number"),
            courseVariantId = rs.getString("course_variant_id")?.let { UUID.fromString(it) },
            length = rs.getObject("length") as? Double
        )
    }

    open fun save(leg: Leg): Leg {
        if (leg.id == null) {
            leg.id = UUID.randomUUID()
        }

        val exists = (jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM leg WHERE id = ?",
            Int::class.java,
            leg.id
        ) ?: 0) > 0

        if (exists) {
            jdbcTemplate.update(
                """
                UPDATE leg 
                SET sequence_number = ?, course_variant_id = ?, length = ? 
                WHERE id = ?
                """,
                leg.sequenceNumber,
                leg.courseVariantId,
                leg.length,
                leg.id
            )
        } else {
            jdbcTemplate.update(
                """
                INSERT INTO leg (id, sequence_number, course_variant_id, length) 
                VALUES (?, ?, ?, ?)
                """,
                leg.id,
                leg.sequenceNumber,
                leg.courseVariantId,
                leg.length
            )
        }

        return leg
    }

}
