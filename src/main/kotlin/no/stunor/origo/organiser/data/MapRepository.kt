package no.stunor.origo.organiser.data

import no.stunor.origo.organiser.model.courses.MapArea
import no.stunor.origo.organiser.model.courses.RaceMap
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.*

@Repository
open class MapRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        RaceMap(
            id = UUID.fromString(rs.getString("id")),
            raceId = UUID.fromString(rs.getString("race_id")),
            name = rs.getString("name"),
            scale = rs.getObject("scale") as? Int,
            mapArea = if (rs.getObject("top_left_x") != null) {
                MapArea(
                    topLeftX = rs.getDouble("top_left_x"),
                    topLeftY = rs.getDouble("top_left_y"),
                    bottomRightX = rs.getDouble("bottom_right_x"),
                    bottomRightY = rs.getDouble("bottom_right_y")
                )
            } else null
        )
    }

    open fun save(map: RaceMap): RaceMap {
        if (map.id == null) {
            map.id = UUID.randomUUID()
        }

        val exists = (jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM map WHERE id = ?",
            Int::class.java,
            map.id
        ) ?: 0) > 0

        if (exists) {
            jdbcTemplate.update(
                """
                UPDATE map 
                SET race_id = ?, name = ?, scale = ?, 
                    top_left_x = ?, top_left_y = ?, bottom_right_x = ?, bottom_right_y = ? 
                WHERE id = ?
                """,
                map.raceId,
                map.name,
                map.scale,
                map.mapArea?.topLeftX,
                map.mapArea?.topLeftY,
                map.mapArea?.bottomRightX,
                map.mapArea?.bottomRightY,
                map.id
            )
        } else {
            jdbcTemplate.update(
                """
                INSERT INTO map (id, race_id, name, scale, top_left_x, top_left_y, bottom_right_x, bottom_right_y) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                map.id,
                map.raceId,
                map.name,
                map.scale,
                map.mapArea?.topLeftX,
                map.mapArea?.topLeftY,
                map.mapArea?.bottomRightX,
                map.mapArea?.bottomRightY
            )
        }

        return map
    }

}

