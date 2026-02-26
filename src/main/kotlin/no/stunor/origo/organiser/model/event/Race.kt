package no.stunor.origo.organiser.model.event

import java.sql.Timestamp
import java.util.UUID

data class Race(
    var id: UUID = UUID.randomUUID(),
    var eventorRef: String = "",
    var name: String = "",
    var date: Timestamp? = null,
    var eventId: UUID
)

