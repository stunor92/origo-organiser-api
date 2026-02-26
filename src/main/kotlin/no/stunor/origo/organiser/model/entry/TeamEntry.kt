package no.stunor.origo.organiser.model.entry

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import no.stunor.origo.organiser.model.organisation.Organisation
import java.sql.Timestamp

/**
 * Team entry model that aligns with team_entry database table.
 * Represents a relay team's entry in a race.
 */
data class TeamEntry(
    @JsonProperty("id")
    override var id: String? = null,

    @JsonProperty("raceId")
    @JsonIgnore
    override var raceId: String = "",

    @JsonProperty("classId")
    override var classId: String = "",

    // Team-specific fields matching team_entry table
    @JsonProperty("name")
    var name: String = "",

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
    @JsonProperty("organisations")
    var organisations: List<Organisation> = listOf(),

    @JsonProperty("teamMembers")
    var teamMembers: List<TeamMember> = listOf(),

    @JsonProperty("entryFeeIds")
    var entryFeeIds: List<String> = listOf()
) : Entry {

    override fun equals(other: Any?): Boolean {
        return if (other is TeamEntry) {
            raceId == other.raceId && name == other.name
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + raceId.hashCode()
        result = 31 * result + classId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + organisations.hashCode()
        result = 31 * result + teamMembers.hashCode()
        result = 31 * result + (bib?.hashCode() ?: 0)
        result = 31 * result + status.hashCode()
        result = 31 * result + (startTime?.hashCode() ?: 0)
        result = 31 * result + (finishTime?.hashCode() ?: 0)
        result = 31 * result + (result?.hashCode() ?: 0)
        result = 31 * result + entryFeeIds.hashCode()
        return result
    }
}

