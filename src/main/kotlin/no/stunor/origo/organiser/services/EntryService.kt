package no.stunor.origo.organiser.services

import no.stunor.origo.organiser.api.EventorService
import no.stunor.origo.organiser.data.CompetitorRepository
import no.stunor.origo.organiser.data.EventRepository
import no.stunor.origo.organiser.data.EventorRepository
import no.stunor.origo.organiser.data.RaceRepository
import no.stunor.origo.organiser.exception.EventNotFoundException
import no.stunor.origo.organiser.exception.EventorNotFoundException
import no.stunor.origo.organiser.model.entry.PersonEntry
import no.stunor.origo.organiser.model.entry.EntryStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
open class EntryService {

    @Autowired
    private lateinit var eventRepository: EventRepository

    @Autowired
    private lateinit var eventorRepository: EventorRepository

    @Autowired
    private lateinit var eventorService: EventorService

    @Autowired
    private lateinit var competitorRepository: CompetitorRepository

    @Autowired
    private lateinit var raceRepository: RaceRepository


    private val logger = LoggerFactory.getLogger(EntryService::class.java)

    /**
     * Fetches event entry list from eventor-api and saves signed up competitors to database
     * 
     * @param eventId The event ID
     * @return Number of entries saved (total across all races - both person and team entries)
     */
    @Transactional
    open fun downloadEntryList(eventId: UUID): Int {
        logger.info("Syncing event entry list for eventId: {}", eventId)

        // Get event from database
        val event = eventRepository.findById(eventId) ?: throw EventNotFoundException()
        val eventor = eventorRepository.findById(event.eventorId) ?: throw EventorNotFoundException(event.eventorId)

        // Get all races for this event
        val races = raceRepository.findByEventId(eventId)
        logger.info("Found {} races for event {}", races.size, event.name)

        if (races.isEmpty()) {
            logger.warn("No races found for event {}", eventId)
            return 0
        }

        // Fetch entries from eventor-api (returns IOF EntryList)
        val iofEntryList = eventorService.getEventEntryList(eventor, event.eventorRef)
        if (iofEntryList == null) {
            logger.warn("No entry list returned from Eventor for event {}", eventId)
            return 0
        }

        val iofPersonEntries = iofEntryList.personEntry ?: emptyList()
        val iofTeamEntries = iofEntryList.teamEntry ?: emptyList()
        logger.info("Fetched {} person entries and {} team entries from Eventor for event {}",
            iofPersonEntries.size, iofTeamEntries.size, event.name)

        var totalSaved = 0

        // Process person entries
        totalSaved += processPersonEntries(iofPersonEntries, races, event.classes.toList())

        // Process team entries
        totalSaved += processTeamEntries(iofTeamEntries, races, event.classes.toList())

        logger.info("Successfully synced {} entries (person + team) across {} races", totalSaved, races.size)
        return totalSaved
    }

    /**
     * Process person entries for all races
     */
    private fun processPersonEntries(
        iofPersonEntries: List<org.iof.PersonEntry>,
        races: List<no.stunor.origo.organiser.model.event.Race>,
        eventClasses: List<no.stunor.origo.organiser.model.event.EventClass>
    ): Int {
        var count = 0

        races.forEach { race ->
            logger.info("Processing person entries for race: {} ({})", race.name, race.id)

            iofPersonEntries.forEach { iofEntry ->
                try {
                    // Convert IOF PersonEntry to our PersonEntry model
                    val entries = convertIofPersonEntry(iofEntry, races, eventClasses)

                    // Filter entries for this specific race
                    entries.filter { it.raceId == race.id.toString() }.forEach { entry ->
                        val entry = convertToPersonCompetitor(entry)
                        competitorRepository.save(entry)
                        count++
                        logger.debug("Saved person competitor: {} {} for race {}",
                            entry.name,
                            entry.personId,
                            race.name)
                    }
                } catch (e: Exception) {
                    logger.error("Error saving person competitor for race {}: {}",
                        race.name,
                        e.message,
                        e)
                }
            }
        }
        
        return count
    }

    /**
     * Process team entries for all races
     */
    private fun processTeamEntries(
        iofTeamEntries: List<org.iof.TeamEntry>,
        races: List<no.stunor.origo.organiser.model.event.Race>,
        eventClasses: List<no.stunor.origo.organiser.model.event.EventClass>
    ): Int {
        var count = 0

        races.forEach { race ->
            logger.info("Processing team entries for race: {} ({})", race.name, race.id)

            iofTeamEntries.forEach { iofEntry ->
                try {
                    // Convert IOF TeamEntry to our TeamCompetitor model
                    val teamCompetitors = convertIofTeamEntry(iofEntry, races, eventClasses)

                    // Filter entries for this specific race
                    teamCompetitors.filter { it.raceId == race.id.toString() }.forEach { teamCompetitor ->
                        competitorRepository.saveTeam(teamCompetitor)
                        count++
                        logger.debug("Saved team competitor: {} for race {}",
                            teamCompetitor.name,
                            race.name)
                    }
                } catch (e: Exception) {
                    logger.error("Error saving team competitor for race {}: {}",
                        race.name,
                        e.message,
                        e)
                }
            }
        }

        return count
    }

    /**
     * Converts IOF TeamEntry from JAXB to our TeamCompetitor model.
     * Creates one entry per race/class combination.
     */
    private fun convertIofTeamEntry(
        iofEntry: org.iof.TeamEntry,
        races: List<no.stunor.origo.organiser.model.event.Race>,
        eventClasses: List<no.stunor.origo.organiser.model.event.EventClass>
    ): List<no.stunor.origo.organiser.model.competitor.TeamCompetitor> {
        val teamCompetitors = mutableListOf<no.stunor.origo.organiser.model.competitor.TeamCompetitor>()

        // Get team name
        val teamName = iofEntry.name ?: "Unknown Team"

        // Get organisations
        val organisations = iofEntry.organisation?.mapNotNull { iofOrg ->
            no.stunor.origo.organiser.model.organisation.Organisation(
                organisationId = iofOrg.id?.value ?: "",
                name = iofOrg.name ?: "",
                type = no.stunor.origo.organiser.model.organisation.OrganisationType.Club,
                country = iofOrg.country?.code ?: ""
            )
        } ?: emptyList()

        // Get assigned fee IDs
        val entryFeeIds = iofEntry.assignedFee?.mapNotNull { assignedFee ->
            assignedFee.fee?.id?.value
        } ?: emptyList()

        // Get team members
        val teamMembers = iofEntry.teamEntryPerson?.mapNotNull { teamPerson ->
            val person = teamPerson.person
            if (person != null) {
                val punchingUnit = teamPerson.controlCard?.firstOrNull()?.let { card ->
                    card.value?.let { cardValue ->
                        no.stunor.origo.organiser.model.competitor.PunchingUnit(
                            id = cardValue,
                            type = when (card.punchingSystem) {
                                "SI" -> no.stunor.origo.organiser.model.competitor.PunchingUnitType.SI
                                "Emit" -> no.stunor.origo.organiser.model.competitor.PunchingUnitType.Emit
                                else -> no.stunor.origo.organiser.model.competitor.PunchingUnitType.Other
                            }
                        )
                    }
                }

                val birthDate = person.birthDate?.toString()
                val birthYear = birthDate?.substring(0, 4)?.toIntOrNull()

                val gender = when (person.sex) {
                    "M" -> no.stunor.origo.organiser.model.person.Gender.Man
                    "F" -> no.stunor.origo.organiser.model.person.Gender.Woman
                    else -> no.stunor.origo.organiser.model.person.Gender.Other
                }

                // Get assigned fee IDs for this team member
                val memberEntryFeeIds = teamPerson.assignedFee?.mapNotNull { assignedFee ->
                    assignedFee.fee?.id?.value
                } ?: emptyList()

                no.stunor.origo.organiser.model.competitor.TeamMemberCompetitor(
                    personId = person.id?.firstOrNull()?.value,
                    name = no.stunor.origo.organiser.model.person.PersonName(
                        given = person.name?.given ?: "",
                        family = person.name?.family ?: ""
                    ),
                    birthYear = birthYear,
                    nationality = person.nationality?.code,
                    gender = gender,
                    punchingUnit = punchingUnit,
                    leg = teamPerson.leg?.toInt() ?: 1,
                    entryFeeIds = memberEntryFeeIds
                )
            } else null
        } ?: emptyList()

        // Process each class the team is entered in
        iofEntry.clazz?.forEach { iofClass ->
            // Match IOF class to our EventClass
            val eventClass = eventClasses.find {
                it.eventorRef == iofClass.id?.value || it.name == iofClass.name
            }

            if (eventClass != null) {
                // Get race numbers - these indicate which races the team is entered in
                val raceNumbers = iofEntry.race?.map { it.toInt() } ?: listOf(1)

                // Create a team entry for each race number
                raceNumbers.forEach { raceNumber ->
                    // Match race number to actual race (raceNumber 1 = first race, 2 = second, etc.)
                    val race = races.getOrNull(raceNumber - 1)

                    if (race != null) {
                        teamCompetitors.add(
                            no.stunor.origo.organiser.model.competitor.TeamCompetitor(
                                id = iofEntry.id?.value ?: UUID.randomUUID().toString(),
                                raceId = race.id.toString(),
                                eventClassId = eventClass.id.toString(),
                                name = teamName,
                                organisations = organisations,
                                teamMembers = teamMembers,
                                entryFeeIds = entryFeeIds,
                                bib = null,
                                status = no.stunor.origo.organiser.model.competitor.CompetitorStatus.NotActivated,
                                startTime = null,
                                finishTime = null,
                                result = null
                            )
                        )
                    }
                }
            }
        }

        return teamCompetitors
    }

    /**
     * Converts IOF PersonEntry from JAXB to our PersonEntry model.
     * Creates one entry per race/class combination.
     */
    private fun convertIofPersonEntry(
        iofEntry: org.iof.PersonEntry,
        races: List<no.stunor.origo.organiser.model.event.Race>,
        eventClasses: List<no.stunor.origo.organiser.model.event.EventClass>
    ): List<PersonEntry> {
        val entries = mutableListOf<PersonEntry>()

        // Get person details
        val person = iofEntry.person
        val givenName = person?.name?.given ?: ""
        val familyName = person?.name?.family ?: ""
        val birthDate = person?.birthDate?.toString()
        val birthYear = birthDate?.substring(0, 4)?.toIntOrNull()

        // Get organisation
        val iofOrg = iofEntry.organisation
        val organisation = if (iofOrg != null) {
            no.stunor.origo.organiser.model.organisation.Organisation(
                organisationId = iofOrg.id?.value ?: "",
                name = iofOrg.name ?: "",
                type = no.stunor.origo.organiser.model.organisation.OrganisationType.Club,
                country = iofOrg.country?.code ?: ""
            )
        } else null

        // Get gender
        val gender = when (person?.sex) {
            "M" -> no.stunor.origo.organiser.model.person.Gender.Man
            "F" -> no.stunor.origo.organiser.model.person.Gender.Woman
            else -> no.stunor.origo.organiser.model.person.Gender.Other
        }

        // Get nationality
        val nationality = person?.nationality?.code

        // Get control cards (punching units)
        val punchingUnits = iofEntry.controlCard?.mapNotNull { card ->
            card.value?.let { cardValue ->
                no.stunor.origo.organiser.model.competitor.PunchingUnit(
                    id = cardValue,
                    type = when (card.punchingSystem) {
                        "SI" -> no.stunor.origo.organiser.model.competitor.PunchingUnitType.SI
                        "Emit" -> no.stunor.origo.organiser.model.competitor.PunchingUnitType.Emit
                        else -> no.stunor.origo.organiser.model.competitor.PunchingUnitType.Other
                    }
                )
            }
        } ?: emptyList()

        // Get assigned fee IDs
        val entryFeeIds = iofEntry.assignedFee?.mapNotNull { assignedFee ->
            assignedFee.fee?.id?.value
        } ?: emptyList()

        // Process each class the person is entered in
        iofEntry.clazz?.forEachIndexed { index, iofClass ->
            // Match IOF class to our EventClass
            val eventClass = eventClasses.find {
                it.eventorRef == iofClass.id?.value || it.name == iofClass.name
            }

            if (eventClass != null) {
                // Get race numbers - these indicate which races the person is entered in
                val raceNumbers = iofEntry.raceNumber?.map { it.toInt() } ?: listOf(1)

                // Create an entry for each race number
                raceNumbers.forEach { raceNumber ->
                    // Match race number to actual race (raceNumber 1 = first race, 2 = second, etc.)
                    val race = races.getOrNull(raceNumber - 1)

                    if (race != null) {
                        entries.add(
                            PersonEntry(
                                entryId = iofEntry.id?.value ?: UUID.randomUUID().toString(),
                                raceId = race.id.toString(),
                                name = no.stunor.origo.organiser.model.person.PersonName(
                                    given = givenName,
                                    family = familyName
                                ),
                                personId = person?.id?.firstOrNull()?.value,
                                organisation = organisation,
                                birthYear = birthYear,
                                nationality = nationality,
                                gender = gender,
                                classId = eventClass.id.toString(),
                                punchingUnits = punchingUnits,
                                entryFeeIds = entryFeeIds,
                                status = no.stunor.origo.organiser.model.entry.EntryStatus.NotActivated,
                                startTime = null,
                                finishTime = null
                            )
                        )
                    }
                }
            }
        }

        return entries
    }

    /**
     * Converts PersonEntry from eventor-api to PersonCompetitor for database storage
     */
    private fun convertToPersonCompetitor(entry: PersonEntry): PersonCompetitor {
        return PersonCompetitor(
            id = entry.entryId,
            raceId = entry.raceId,
            eventClassId = entry.classId,
            personId = entry.personId,
            name = entry.name,
            organisation = entry.organisation,
            birthYear = entry.birthYear,
            nationality = entry.nationality,
            gender = entry.gender,
            punchingUnit = entry.punchingUnits.firstOrNull(),
            bib = entry.bib,
            status = convertEntryStatusToCompetitorStatus(entry.status),
            startTime = entry.startTime,
            finishTime = entry.finishTime,
            entryFeeIds = entry.entryFeeIds
        )
    }

    /**
     * Maps EntryStatus to CompetitorStatus
     */
    private fun convertEntryStatusToCompetitorStatus(entryStatus: EntryStatus): CompetitorStatus {
        return when (entryStatus) {
            EntryStatus.NotActivated -> CompetitorStatus.NotActivated
            EntryStatus.Activated -> CompetitorStatus.Activated
            EntryStatus.Started -> CompetitorStatus.Started
            EntryStatus.Finished -> CompetitorStatus.Finished
            EntryStatus.OK -> CompetitorStatus.Finished
            EntryStatus.MissingPunch -> CompetitorStatus.MissingPunch
            EntryStatus.Disqualified -> CompetitorStatus.Disqualified
            EntryStatus.DidNotFinish -> CompetitorStatus.DidNotFinish
            EntryStatus.Overtime -> CompetitorStatus.Overtime
            EntryStatus.NotCompeting -> CompetitorStatus.NotCompeting
            EntryStatus.SportWithdraw -> CompetitorStatus.SportWithdraw
            EntryStatus.NotStarted -> CompetitorStatus.NotStarted
            EntryStatus.DidNotStart -> CompetitorStatus.DidNotStart
            EntryStatus.Cancelled -> CompetitorStatus.Cancelled
            EntryStatus.Deregistered -> CompetitorStatus.Deregistered
        }
    }
}
