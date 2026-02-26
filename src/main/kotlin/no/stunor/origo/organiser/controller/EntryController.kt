package no.stunor.origo.organiser.controller

import no.stunor.origo.organiser.services.EntryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/entries/")
internal class EntryController {

    @Autowired
    private lateinit var eventEntryService: EntryService

    /**
     * Syncs event entry list from eventor-api and saves signed up competitors to database
     * 
     * @param eventorId The eventor organization ID
     * @param eventId The event ID  
     * @param raceId The race ID
     * @return Response with the number of competitors synced
     */
    @PostMapping("/{eventId}")
    fun syncEventEntries(
        @PathVariable eventId: UUID,
    ): ResponseEntity<Map<String, Any>> {
        val count = eventEntryService.downloadEntryList(eventId)
        
        return ResponseEntity(
            mapOf(
                "message" to "Successfully synced competitors",
                "count" to count,
                "eventId" to eventId,
            ),
            HttpStatus.OK
        )
    }
}
