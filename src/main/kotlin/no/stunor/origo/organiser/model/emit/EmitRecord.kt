package no.stunor.origo.organiser.model.emit

import no.stunor.origo.organiser.model.competitor.PunchingUnit
import no.stunor.origo.organiser.model.competitor.SplitTime

data class EmitRecord(
        var punchingUnit: PunchingUnit,
        var splitTimes: List<SplitTime> = listOf(),

)