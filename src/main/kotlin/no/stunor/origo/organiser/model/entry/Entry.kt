package no.stunor.origo.organiser.model.entry

import java.sql.Timestamp

/**
 * Base interface for all entry types (person and team).
 * Matches the database entry table structure.
 */
interface Entry {
    var id: String?
    var raceId: String
    var classId: String
    var bib: String?
    var status: EntryStatus
    var startTime: Timestamp?
    var finishTime: Timestamp?
    var result: Result?
}

