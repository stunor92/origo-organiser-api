package no.stunor.origo.organiser.data

import no.stunor.origo.organiser.model.event.Event
import no.stunor.origo.organiser.model.event.EventClass
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.*

@Repository
open class EventRepository(private val jdbcTemplate: JdbcTemplate) {

    private val eventRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Event(
            id = UUID.fromString(rs.getString("id")),
            eventorId = rs.getString("eventor_id") ?: "",
            eventorRef = rs.getString("eventor_ref") ?: "",
            name = rs.getString("name") ?: "",
            startDate = rs.getTimestamp("start_date"),
            finishDate = rs.getTimestamp("finish_date")
        )
    }

    private val classRowMapper = RowMapper { rs: ResultSet, _: Int ->
        EventClass(
            id = UUID.fromString(rs.getString("id")),
            eventorRef = rs.getString("eventor_ref") ?: "",
            name = rs.getString("name") ?: "",
            shortName = rs.getString("short_name") ?: ""
        )
    }

    open fun findById(id: UUID): Event? {
        val event = try {
            jdbcTemplate.queryForObject(
                "SELECT * FROM event WHERE id = ?",
                eventRowMapper,
                id
            )
        } catch (_: Exception) {
            return null
        }

        // Load classes
        event.classes = jdbcTemplate.query(
            "SELECT * FROM class WHERE event_id = ?",
            classRowMapper,
            id
        ).toMutableSet()

        return event
    }

}
