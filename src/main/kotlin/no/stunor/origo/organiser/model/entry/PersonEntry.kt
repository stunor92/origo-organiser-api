package no.stunor.origo.organiser.model.entry

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import no.stunor.origo.organiser.model.organisation.Organisation
import no.stunor.origo.organiser.model.person.Gender
import no.stunor.origo.organiser.model.person.PersonName
import java.sql.Timestamp

/**
 * Person entry model that aligns with person_entry database table.
 * Represents an individual competitor's entry in a race.
 */
data class PersonEntry(
    @JsonProperty("id")
    override var id: String? = null,

    @JsonProperty("raceId")
    @JsonIgnore
    override var raceId: String = "",

    @JsonProperty("classId")
    override var classId: String = "",

    // Person-specific fields matching person_entry table
    @JsonProperty("eventorRef")
    var eventorRef: String? = null,

    @JsonProperty("personEventorRef")
    var personEventorRef: String? = null,

    @JsonProperty("givenName")
    var givenName: String = "",

    @JsonProperty("familyName")
    var familyName: String = "",

    @JsonProperty("birthYear")
    var birthYear: Int? = null,

    @JsonProperty("nationality")
    var nationality: String? = null,

    @JsonProperty("gender")
    var gender: Gender = Gender.Other,

    // Entry base fields
    @JsonProperty("bib")
    override var bib: String? = null,

    @JsonProperty("status")
    override var status: EntryStatus = EntryStatus.NotActivated,

    @JsonProperty("startTime")
    override var startTime: Timestamp? = null,

    @JsonProperty("finishTime")
    override var finishTime: Timestamp? = null,

    @JsonProperty("result")
    override var result: Result? = null,

    // Additional fields not in base entry table
    @JsonProperty("organisation")
    var organisation: Organisation? = null,

    @JsonProperty("punchingUnit")
    var punchingUnit: PunchingUnit? = null,

    @JsonProperty("splitTimes")
    var splitTimes: List<SplitTime> = listOf(),

    @JsonProperty("entryFeeIds")
    var entryFeeIds: List<String> = listOf()
) : Entry {

    // Helper property for backward compatibility
    @get:JsonProperty("name")
    val name: PersonName
        get() = PersonName(given = givenName, family = familyName)

    // Helper property for backward compatibility
    @get:JsonProperty("personId")
    val personId: String?
        get() = personEventorRef

    override fun equals(other: Any?): Boolean {
        return if (other is PersonEntry) {
            raceId == other.raceId && personEventorRef == other.personEventorRef
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + raceId.hashCode()
        result = 31 * result + classId.hashCode()
        result = 31 * result + (personEventorRef?.hashCode() ?: 0)
        result = 31 * result + givenName.hashCode()
        result = 31 * result + familyName.hashCode()
        result = 31 * result + (organisation?.hashCode() ?: 0)
        result = 31 * result + (birthYear ?: 0)
        result = 31 * result + (nationality?.hashCode() ?: 0)
        result = 31 * result + gender.hashCode()
        result = 31 * result + (punchingUnit?.hashCode() ?: 0)
        result = 31 * result + (bib?.hashCode() ?: 0)
        result = 31 * result + status.hashCode()
        result = 31 * result + (startTime?.hashCode() ?: 0)
        result = 31 * result + (finishTime?.hashCode() ?: 0)
        result = 31 * result + (result?.hashCode() ?: 0)
        result = 31 * result + splitTimes.hashCode()
        result = 31 * result + entryFeeIds.hashCode()
        return result
    }
}

