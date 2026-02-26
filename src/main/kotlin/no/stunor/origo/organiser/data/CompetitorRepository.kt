package no.stunor.origo.organiser.data

import no.stunor.origo.organiser.model.competitor.CompetitorStatus
import no.stunor.origo.organiser.model.competitor.PersonCompetitor
import no.stunor.origo.organiser.model.competitor.ResultStatus
import no.stunor.origo.organiser.model.entry.EntryStatus
import no.stunor.origo.organiser.model.entry.PersonEntry
import no.stunor.origo.organiser.model.person.Gender
import no.stunor.origo.organiser.model.person.PersonName
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.*

@Repository
open class CompetitorRepository(private val jdbcTemplate: JdbcTemplate) {

    private val personEntryRowMapper = RowMapper { rs: ResultSet, _: Int ->
        PersonEntry(
            id = rs.getString("id"),
            raceId = UUID.fromString(rs.getString("race_id")).toString(),
            classId = UUID.fromString(rs.getString("class_id")).toString(),
            eventorRef = rs.getString("person_eventor_ref"),
            name = PersonName(
                given = rs.getString("given_name") ?: "",
                family = rs.getString("family_name") ?: ""
            ),
            organisation = null, // Organizations are in separate table entry_organisation
            birthYear = rs.getLong("birth_year").takeIf { !rs.wasNull() }?.toInt(),
            nationality = rs.getString("nationality"),
            gender = rs.getString("gender")?.let { 
                try { Gender.valueOf(it) } catch (e: Exception) { Gender.Other }
            } ?: Gender.Other,
            punchingUnit = null, // Punching units are in separate table punching_unit_entry
            bib = rs.getString("bib"),
            status = rs.getString("status")?.let { 
                try {
                    EntryStatus.valueOf(it) } catch (e: Exception) {
                    EntryStatus.NotActivated }
            } ?: EntryStatus.NotActivated,
            startTime = rs.getTimestamp("start_time"),
            finishTime = rs.getTimestamp("finish_time"),
            result = rs.getString("result_status")?.let { statusStr ->
                rs.getLong("time").takeIf { !rs.wasNull() }?.let { time ->
                    no.stunor.origo.organiser.model.competitor.Result(
                        time = time.toInt(),
                        timeBehind = rs.getLong("time_behind").takeIf { !rs.wasNull() }?.toInt() ?: 0,
                        position = rs.getLong("position").takeIf { !rs.wasNull() }?.toInt() ?: 0,
                        status = try { ResultStatus.valueOf(statusStr) } catch (e: Exception) { ResultStatus.Inactive }
                    )
                }
            }
        )
    }

    open fun save(competitor: PersonCompetitor): PersonCompetitor {
        val id = competitor.id?.let { UUID.fromString(it) } ?: UUID.randomUUID()
        
        val exists = try {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM person_entry WHERE id = ?::uuid",
                Int::class.java,
                id.toString()
            )!! > 0
        } catch (_: Exception) {
            false
        }

        val raceId = UUID.fromString(competitor.raceId)
        val classId = UUID.fromString(competitor.eventClassId)

        if (exists) {
            jdbcTemplate.update(
                """
                UPDATE person_entry SET 
                    race_id = ?::uuid,
                    class_id = ?::uuid,
                    person_eventor_ref = ?,
                    eventor_ref = ?,
                    given_name = ?,
                    family_name = ?,
                    birth_year = ?,
                    nationality = ?,
                    gender = ?::gender,
                    bib = ?,
                    status = ?::competitor_status,
                    start_time = ?,
                    finish_time = ?,
                    time = ?,
                    time_behind = ?,
                    position = ?,
                    result_status = ?::result_status
                WHERE id = ?::uuid
                """.trimIndent(),
                raceId,
                classId,
                competitor.personId,
                competitor.id, // eventor_ref same as id for now
                (competitor.name as PersonName).given,
                (competitor.name as PersonName).family,
                competitor.birthYear?.toLong(),
                competitor.nationality,
                competitor.gender.name,
                competitor.bib,
                competitor.status.name,
                competitor.startTime,
                competitor.finishTime,
                competitor.result?.time?.toLong(),
                competitor.result?.timeBehind?.toLong(),
                competitor.result?.position?.toLong(),
                competitor.result?.status?.name,
                id
            )
        } else {
            jdbcTemplate.update(
                """
                INSERT INTO person_entry (
                    id, race_id, class_id, person_eventor_ref, eventor_ref,
                    given_name, family_name,
                    birth_year, nationality, gender,
                    bib, status, start_time, finish_time,
                    time, time_behind, position, result_status
                ) VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?::gender, ?, ?::competitor_status, ?, ?, ?, ?, ?, ?::result_status)
                """.trimIndent(),
                id,
                raceId,
                classId,
                competitor.personId,
                competitor.id, // eventor_ref same as id for now
                (competitor.name as PersonName).given,
                (competitor.name as PersonName).family,
                competitor.birthYear?.toLong(),
                competitor.nationality,
                competitor.gender.name,
                competitor.bib,
                competitor.status.name,
                competitor.startTime,
                competitor.finishTime,
                competitor.result?.time?.toLong(),
                competitor.result?.timeBehind?.toLong(),
                competitor.result?.position?.toLong(),
                competitor.result?.status?.name
            )
        }

        // Handle punching units in separate table if present
        competitor.punchingUnit?.let { savePunchingUnit(id, it) }

        // Handle organisation in separate table if present
        competitor.organisation?.let { savePersonOrganisation(id, it) }

        // Handle entry fees in separate table if present
        if (competitor.entryFeeIds != null && competitor.entryFeeIds.isNotEmpty()) {
            savePersonEntryFees(id, competitor.entryFeeIds)
        }

        return competitor.copy(id = id.toString())
    }

    private fun savePunchingUnit(entryId: UUID, punchingUnit: no.stunor.origo.organiser.model.competitor.PunchingUnit) {
        // Delete existing punching units for this person entry (leg IS NULL for person entries, leg has value for team members)
        jdbcTemplate.update(
            "DELETE FROM punching_unit_entry WHERE entry_id = ?::uuid AND leg IS NULL",
            entryId
        )
        
        // Insert new punching unit
        jdbcTemplate.update(
            """
            INSERT INTO punching_unit_entry (entry_id, leg, id, type)
            VALUES (?::uuid, NULL, ?, ?::punching_unit_type)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            entryId,
            punchingUnit.id,
            punchingUnit.type.name
        )
    }

    private fun savePersonOrganisation(
        entryId: UUID,
        organisation: no.stunor.origo.organiser.model.organisation.Organisation
    ) {
        // Delete existing organisations for this person entry
        jdbcTemplate.update(
            "DELETE FROM entry_organisation WHERE entry_id = ?::uuid",
            entryId
        )

        // Insert new organisation
        jdbcTemplate.update(
            """
            INSERT INTO entry_organisation (entry_id, organisation_id, name, country, type)
            VALUES (?::uuid, ?, ?, ?, ?::organisation_type)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            entryId,
            organisation.organisationId,
            organisation.name,
            organisation.country,
            organisation.type.name
        )
    }

    private fun savePersonEntryFees(entryId: UUID, entryFeeIds: List<String>) {
        // Delete existing entry fees for this person entry
        jdbcTemplate.update(
            "DELETE FROM entry_fee WHERE entry_id = ?::uuid",
            entryId
        )

        // Insert new entry fees
        entryFeeIds.forEach { feeId ->
            jdbcTemplate.update(
                """
                INSERT INTO entry_fee (entry_id, fee_id)
                VALUES (?::uuid, ?)
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                entryId,
                feeId
            )
        }
    }

    open fun findById(id: String): PersonCompetitor? {
        return try {
            jdbcTemplate.queryForObject(
                "SELECT * FROM person_entry WHERE id = ?::uuid",
                personEntryRowMapper,
                id
            )
        } catch (_: Exception) {
            null
        }
    }

    open fun findByRaceId(raceId: String): List<PersonCompetitor> {
        return jdbcTemplate.query(
            "SELECT * FROM person_entry WHERE race_id = ?::uuid",
            personEntryRowMapper,
            raceId
        )
    }

    open fun deleteByRaceId(raceId: String) {
        jdbcTemplate.update("DELETE FROM person_entry WHERE race_id = ?::uuid", raceId)
    }

    /**
     * Saves a team competitor to the database
     */
    open fun saveTeam(teamCompetitor: no.stunor.origo.organiser.model.competitor.TeamCompetitor): no.stunor.origo.organiser.model.competitor.TeamCompetitor {
        val id = teamCompetitor.id?.let { UUID.fromString(it) } ?: UUID.randomUUID()

        val exists = try {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_entry WHERE id = ?::uuid",
                Int::class.java,
                id.toString()
            )!! > 0
        } catch (_: Exception) {
            false
        }

        val raceId = UUID.fromString(teamCompetitor.raceId)
        val classId = UUID.fromString(teamCompetitor.eventClassId)

        if (exists) {
            jdbcTemplate.update(
                """
                UPDATE team_entry SET 
                    race_id = ?::uuid,
                    class_id = ?::uuid,
                    name = ?,
                    bib = ?,
                    status = ?::competitor_status,
                    start_time = ?,
                    finish_time = ?,
                    time = ?,
                    time_behind = ?,
                    position = ?,
                    result_status = ?::result_status
                WHERE id = ?::uuid
                """.trimIndent(),
                raceId,
                classId,
                teamCompetitor.name.toString(),
                teamCompetitor.bib,
                teamCompetitor.status.name,
                teamCompetitor.startTime,
                teamCompetitor.finishTime,
                teamCompetitor.result?.time?.toLong(),
                teamCompetitor.result?.timeBehind?.toLong(),
                teamCompetitor.result?.position?.toLong(),
                teamCompetitor.result?.status?.name,
                id
            )
        } else {
            jdbcTemplate.update(
                """
                INSERT INTO team_entry (
                    id, race_id, class_id, name,
                    bib, status, start_time, finish_time,
                    time, time_behind, position, result_status
                ) VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?::competitor_status, ?, ?, ?, ?, ?, ?::result_status)
                """.trimIndent(),
                id,
                raceId,
                classId,
                teamCompetitor.name.toString(),
                teamCompetitor.bib,
                teamCompetitor.status.name,
                teamCompetitor.startTime,
                teamCompetitor.finishTime,
                teamCompetitor.result?.time?.toLong(),
                teamCompetitor.result?.timeBehind?.toLong(),
                teamCompetitor.result?.position?.toLong(),
                teamCompetitor.result?.status?.name
            )
        }

        // Save organisations to entry_organisation table
        saveTeamOrganisations(id, teamCompetitor.organisations)

        // Save team members to team_member table
        saveTeamMembers(id, teamCompetitor.teamMembers)

        // Save entry fees to entry_fee table
        if (teamCompetitor.entryFeeIds != null && teamCompetitor.entryFeeIds.isNotEmpty()) {
            saveTeamEntryFees(id, teamCompetitor.entryFeeIds)
        }

        return teamCompetitor.copy(id = id.toString())
    }

    private fun saveTeamOrganisations(
        entryId: UUID,
        organisations: List<no.stunor.origo.organiser.model.organisation.Organisation>
    ) {
        // Delete existing organisations for this team
        jdbcTemplate.update(
            "DELETE FROM entry_organisation WHERE entry_id = ?::uuid",
            entryId
        )

        // Insert new organisations
        organisations.forEach { org ->
            jdbcTemplate.update(
                """
                INSERT INTO entry_organisation (entry_id, organisation_id, name, country, type)
                VALUES (?::uuid, ?, ?, ?, ?::organisation_type)
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                entryId,
                org.organisationId,
                org.name,
                org.country,
                org.type.name
            )
        }
    }

    private fun saveTeamEntryFees(entryId: UUID, entryFeeIds: List<String>) {
        // Delete existing entry fees for this team entry
        jdbcTemplate.update(
            "DELETE FROM entry_fee WHERE entry_id = ?::uuid",
            entryId
        )

        // Insert new entry fees
        entryFeeIds.forEach { feeId ->
            jdbcTemplate.update(
                """
                INSERT INTO entry_fee (entry_id, fee_id)
                VALUES (?::uuid, ?)
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                entryId,
                feeId
            )
        }
    }

    private fun saveTeamMembers(
        entryId: UUID,
        teamMembers: List<no.stunor.origo.organiser.model.competitor.TeamMemberCompetitor>
    ) {
        // Delete existing team members for this team
        jdbcTemplate.update(
            "DELETE FROM team_member WHERE entry_id = ?::uuid",
            entryId
        )

        // Insert new team members
        teamMembers.forEach { member ->
            val memberId = UUID.randomUUID()
            jdbcTemplate.update(
                """
                INSERT INTO team_member (
                    id, entry_id, person_eventor_ref, leg,
                    given_name, family_name, birth_year, nationality, gender,
                    start_time, finish_time,
                    leg_time, leg_time_behind, leg_position, leg_result_status,
                    overall_time, overall_time_behind, overall_position, overall_result_status
                ) VALUES (
                    ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?::gender,
                    ?, ?, ?, ?, ?, ?::result_status, ?, ?, ?, ?::result_status
                )
                """.trimIndent(),
                memberId,
                entryId,
                member.personId,
                member.leg,
                member.name?.given,
                member.name?.family,
                member.birthYear?.toLong(),
                member.nationality,
                member.gender?.name,
                member.startTime,
                member.finishTime,
                member.legResult?.time?.toLong(),
                member.legResult?.timeBehind?.toLong(),
                member.legResult?.position?.toLong(),
                member.legResult?.status?.name,
                member.overallResult?.time?.toLong(),
                member.overallResult?.timeBehind?.toLong(),
                member.overallResult?.position?.toLong(),
                member.overallResult?.status?.name
            )

            // Save punching unit for team member if present
            member.punchingUnit?.let { savePunchingUnitForTeamMember(entryId, member.leg, it) }

            // Save entry fees for team member if present
            if (member.entryFeeIds.isNotEmpty()) {
                saveTeamMemberEntryFees(memberId, member.entryFeeIds)
            }
        }
    }

    private fun savePunchingUnitForTeamMember(
        entryId: UUID,
        leg: Int,
        punchingUnit: no.stunor.origo.organiser.model.competitor.PunchingUnit
    ) {
        // Delete existing punching unit for this leg
        jdbcTemplate.update(
            "DELETE FROM punching_unit_entry WHERE entry_id = ?::uuid AND leg = ?",
            entryId,
            leg
        )

        // Insert new punching unit
        jdbcTemplate.update(
            """
            INSERT INTO punching_unit_entry (entry_id, leg, id, type)
            VALUES (?::uuid, ?, ?, ?::punching_unit_type)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            entryId,
            leg,
            punchingUnit.id,
            punchingUnit.type.name
        )
    }

    private fun saveTeamMemberEntryFees(memberId: UUID, entryFeeIds: List<String>) {
        // Delete existing entry fees for this team member
        jdbcTemplate.update(
            "DELETE FROM team_member_fee WHERE member_id = ?::uuid",
            memberId
        )

        // Insert new entry fees
        entryFeeIds.forEach { feeId ->
            jdbcTemplate.update(
                """
                INSERT INTO team_member_fee (member_id, fee_id)
                VALUES (?::uuid, ?)
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                memberId,
                feeId
            )
        }
    }
}


