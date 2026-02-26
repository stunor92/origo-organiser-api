package no.stunor.origo.organiser.model.courses

import java.util.*

data class Leg (
    var id: UUID? = null,
    var sequenceNumber: Int = 0,
    var courseVariantId: UUID? = null,
    var length: Double? = null
) {
    override fun toString(): String {
        return "Leg(sequenceNumber=$sequenceNumber)"
    }
}

