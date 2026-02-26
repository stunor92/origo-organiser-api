package no.stunor.origo.organiser.model.event

import java.sql.Timestamp
import java.util.*

data class Event(
    var id: UUID = UUID.randomUUID(),
    var eventorId: String = "",
    var eventorRef: String = "",
    var name: String = "",
    var startDate: Timestamp? = null,
    var finishDate: Timestamp? = null,
    var classes: MutableSet<EventClass> = mutableSetOf()
) {
    override fun toString(): String {
        return "Event(name='$name')"
    }
}

