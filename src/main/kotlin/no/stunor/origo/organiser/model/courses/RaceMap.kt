package no.stunor.origo.organiser.model.courses

import java.util.UUID

data class RaceMap (
    var id: UUID? = null,
    var raceId: UUID = UUID.randomUUID(),
    var name: String? = null,
    var scale: Int? = null,
    var mapArea: MapArea? = null,
    var controls: MutableSet<Control> = mutableSetOf(),
    var courses: MutableSet<Course> = mutableSetOf()
) {
    override fun toString(): String {
        return "RaceMap(raceId=$raceId, id=$id, name='$name')"
    }
}

