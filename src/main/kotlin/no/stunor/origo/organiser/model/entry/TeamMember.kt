package no.stunor.origo.organiser.model.entry

import com.fasterxml.jackson.annotation.JsonProperty
import no.stunor.origo.organiser.model.person.Gender
import no.stunor.origo.organiser.model.person.PersonName
import java.sql.Timestamp

/**
 * Team member model that aligns with team_member database table.
 * Represents an individual member of a relay team.
 */
data class TeamMember(
    @JsonProperty("id")
    var id: String? = null,

    @JsonProperty("personEventorRef")
    var personEventorRef: String? = null,

    @JsonProperty("leg")
    var leg: Int = 1,

    @JsonProperty("givenName")
    var givenName: String = "",

    @JsonProperty("familyName")
    var familyName: String = "",

    @JsonProperty("birthYear")
    var birthYear: Int? = null,

    @JsonProperty("nationality")
    var nationality: String? = null,

    @JsonProperty("gender")
    var gender: Gender? = null,

    @JsonProperty("startTime")
    var startTime: Timestamp? = null,

    @JsonProperty("finishTime")
    var finishTime: Timestamp? = null,

    @JsonProperty("legResult")
    var legResult: Result? = null,

    @JsonProperty("overallResult")
    var overallResult: Result? = null,

    @JsonProperty("punchingUnit")
    var punchingUnit: PunchingUnit? = null,

    @JsonProperty("splitTimes")
    var splitTimes: List<SplitTime> = listOf(),

    @JsonProperty("entryFeeIds")
    var entryFeeIds: List<String> = listOf()
) {
    // Helper property for backward compatibility
    @get:JsonProperty("name")
    val name: PersonName
        get() = PersonName(given = givenName, family = familyName)

    // Helper property for backward compatibility
    @get:JsonProperty("personId")
    val personId: String?
        get() = personEventorRef
}

