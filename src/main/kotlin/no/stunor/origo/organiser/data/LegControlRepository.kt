package no.stunor.origo.organiser.data

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.*

@Repository
open class LegControlRepository(private val jdbcTemplate: JdbcTemplate) {

    open fun linkLegToControl(legId: UUID, controlId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO leg_control (leg_id, control_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            legId, controlId
        )
    }

}
