package no.stunor.origo.organiser.data

import no.stunor.origo.organiser.model.courses.Control
import no.stunor.origo.organiser.model.courses.ControlType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.*

@Repository
open class ControlRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        Control(
            id = UUID.fromString(rs.getString("id")),
            type = rs.getString("type")?.let { ControlType.valueOf(it) },
            controlCode = rs.getString("control_code"),
            mapPosition = no.stunor.origo.organiser.model.courses.MapPosition(
                x = rs.getDouble("map_x"),
                y = rs.getDouble("map_y")
            ),
            geoPosition = if (rs.getObject("geo_lat") != null) {
                no.stunor.origo.organiser.model.courses.GeoPosition(
                    lat = rs.getDouble("geo_lat"),
                    lng = rs.getDouble("geo_lng")
                )
            } else null,
            mapId = rs.getString("map_id")?.let { UUID.fromString(it) }
        )
    }

    open fun save(control: Control): Control {
        if (control.id == null) {
            // Insert new control with enum casting
            control.id = UUID.randomUUID()
            jdbcTemplate.update(
                """
                INSERT INTO control (id, control_code, type, geo_lat, geo_lng, map_x, map_y, map_id) 
                VALUES (?, ?, ?::control_type, ?, ?, ?, ?, ?)
                """,
                control.id,
                control.controlCode,
                control.type?.name,
                control.geoPosition?.lat,
                control.geoPosition?.lng,
                control.mapPosition.x,
                control.mapPosition.y,
                control.mapId
            )
        } else {
            // Update existing control with enum casting
            jdbcTemplate.update(
                """
                UPDATE control 
                SET control_code = ?, type = ?::control_type, geo_lat = ?, geo_lng = ?, 
                    map_x = ?, map_y = ?, map_id = ? 
                WHERE id = ?
                """,
                control.controlCode,
                control.type?.name,
                control.geoPosition?.lat,
                control.geoPosition?.lng,
                control.mapPosition.x,
                control.mapPosition.y,
                control.mapId,
                control.id
            )
        }
        return control
    }

}
