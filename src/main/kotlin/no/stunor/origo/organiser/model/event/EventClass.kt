package no.stunor.origo.organiser.model.event

import java.util.*

data class EventClass(
    var id: UUID = UUID.randomUUID(),
    var eventorRef: String = "",
    var name: String = "",
    var shortName: String = ""
)

