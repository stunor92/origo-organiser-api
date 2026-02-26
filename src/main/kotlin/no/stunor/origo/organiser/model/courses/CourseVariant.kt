package no.stunor.origo.organiser.model.courses

import java.util.*

data class CourseVariant (
    var id: UUID? = null,
    var name: String?,
    var length: Double?,
    var climb: Double?,
    var courseId: UUID? = null,
    var controls: MutableSet<Leg> = mutableSetOf(),
    var printedMaps: Int? = null
) {
    override fun toString(): String {
        return "CourseVariant(name=$name)"
    }
}

