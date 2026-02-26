package no.stunor.origo.organiser.model.entry

/**
 * Entry status enum that aligns with competitor_status database type.
 * Represents the current state of an entry (person or team).
 */
enum class EntryStatus {
    NotActivated,
    Activated,
    Deregistered,
    SignedUp,
    Started,
    Finished,
    OK,
    MissingPunch,
    Disqualified,
    DidNotFinish,
    Overtime,
    NotCompeting,
    SportWithdraw,
    NotStarted,
    DidNotStart,
    Cancelled
}


