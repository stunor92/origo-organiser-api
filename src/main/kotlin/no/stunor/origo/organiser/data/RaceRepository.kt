package no.stunor.origo.organiser.data

import no.stunor.origo.organiser.model.event.Race
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.*

@Repository
open class RaceRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        Race(
            id = UUID.fromString(rs.getString("id")),
            eventorRef = rs.getString("eventor_ref") ?: "",
            name = rs.getString("name") ?: "",
            date = rs.getTimestamp("date"),
            eventId = UUID.fromString(rs.getString("event_id"))
        )
    }

    open fun findById(id: UUID): Race? {
        return try {
            jdbcTemplate.queryForObject(
                "SELECT * FROM race WHERE id = ?",
                rowMapper,
                id
            )
        } catch (_: Exception) {
            null
        }
    }

    open fun findByEventId(eventId: UUID): List<Race> {
        return jdbcTemplate.query(
            "SELECT * FROM race WHERE event_id = ?",
            rowMapper,
            eventId
        )
    }

}
