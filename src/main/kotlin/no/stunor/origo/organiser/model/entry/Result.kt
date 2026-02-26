package no.stunor.origo.organiser.model.entry

data class Result(
    var time: Int? = null,
    var timeBehind: Int? = null,
    val position: Int? = null,
    val status: ResultStatus = ResultStatus.OK
)

