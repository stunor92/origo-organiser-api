package no.stunor.origo.organiser.services

import no.stunor.origo.organiser.data.CompetitorRepository
import no.stunor.origo.organiser.model.competitor.PersonCompetitor
import no.stunor.origo.organiser.model.entry.EntryStatus
import no.stunor.origo.organiser.model.person.Gender
import no.stunor.origo.organiser.model.person.PersonName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

internal class EventEntryServiceTest {

    private lateinit var competitorRepository: CompetitorRepository
    private lateinit var restTemplate: RestTemplate
    private lateinit var eventEntryService: EntryService

    @BeforeEach
    fun setUp() {
        competitorRepository = mock()
        restTemplate = mock()
        eventEntryService = EntryService()
        
        // Use reflection to set private fields
        val repoField = EntryService::class.java.getDeclaredField("competitorRepository")
        repoField.isAccessible = true
        repoField.set(eventEntryService, competitorRepository)
        
        val restTemplateField = EntryService::class.java.getDeclaredField("restTemplate")
        restTemplateField.isAccessible = true
        restTemplateField.set(eventEntryService, restTemplate)
        
        val baseUrlField = EntryService::class.java.getDeclaredField("eventorApiBaseUrl")
        baseUrlField.isAccessible = true
        baseUrlField.set(eventEntryService, "http://localhost:8081/rest")
    }

    @Test
    fun `syncEventEntryList should save competitors to database`() {
        // Given
        val eventorId = "1"
        val eventId = "123"
        val raceId = "456"
        
        val entry = PersonEntry(
            entryId = "entry-1",
            raceId = raceId,
            name = PersonName(given = "John", family = "Doe"),
            personId = "person-1",
            classId = "class-1",
            status = EntryStatus.Activated,
            gender = Gender.Man
        )
        
        val entries = listOf(entry)
        
        // Mock RestTemplate response
        val responseEntity = ResponseEntity(entries, HttpStatus.OK)
        whenever(
            restTemplate.exchange(
                eq("http://localhost:8081/rest/event/$eventorId/$eventId/entry-list"),
                eq(HttpMethod.GET),
                any(),
                any<ParameterizedTypeReference<List<PersonEntry>>>()
            )
        ).thenReturn(responseEntity)
        
        // Mock repository save
        whenever(competitorRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<PersonCompetitor>(0)
        }
        
        // When
        val count = eventEntryService.downloadEntryList(eventorId, eventId, raceId)
        
        // Then
        assertEquals(1, count)
        verify(competitorRepository, times(1)).save(any())
    }

    @Test
    fun `syncEventEntryList should filter entries by raceId`() {
        // Given
        val eventorId = "1"
        val eventId = "123"
        val raceId = "456"
        
        val entry1 = PersonEntry(
            entryId = "entry-1",
            raceId = raceId,
            name = PersonName(given = "John", family = "Doe"),
            classId = "class-1",
            status = EntryStatus.Activated,
            gender = Gender.Man
        )
        
        val entry2 = PersonEntry(
            entryId = "entry-2",
            raceId = "999", // Different race
            name = PersonName(given = "Jane", family = "Smith"),
            classId = "class-1",
            status = EntryStatus.Activated,
            gender = Gender.Woman
        )
        
        val entries = listOf(entry1, entry2)
        
        // Mock RestTemplate response
        val responseEntity = ResponseEntity(entries, HttpStatus.OK)
        whenever(
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any(),
                any<ParameterizedTypeReference<List<PersonEntry>>>()
            )
        ).thenReturn(responseEntity)
        
        // Mock repository save
        whenever(competitorRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<PersonCompetitor>(0)
        }
        
        // When
        val count = eventEntryService.downloadEntryList(eventorId, eventId, raceId)
        
        // Then
        assertEquals(1, count) // Only 1 entry should match the raceId
        verify(competitorRepository, times(1)).save(any())
    }

    @Test
    fun `syncEventEntryList should handle empty response`() {
        // Given
        val eventorId = "1"
        val eventId = "123"
        val raceId = "456"
        
        val entries = emptyList<PersonEntry>()
        
        // Mock RestTemplate response
        val responseEntity = ResponseEntity(entries, HttpStatus.OK)
        whenever(
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any(),
                any<ParameterizedTypeReference<List<PersonEntry>>>()
            )
        ).thenReturn(responseEntity)
        
        // When
        val count = eventEntryService.downloadEntryList(eventorId, eventId, raceId)
        
        // Then
        assertEquals(0, count)
        verify(competitorRepository, never()).save(any())
    }

    @Test
    fun `syncEventEntryList should handle API errors gracefully`() {
        // Given
        val eventorId = "1"
        val eventId = "123"
        val raceId = "456"
        
        // Mock RestTemplate to throw exception
        whenever(
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any(),
                any<ParameterizedTypeReference<List<PersonEntry>>>()
            )
        ).thenThrow(RuntimeException("API Error"))
        
        // When
        val count = eventEntryService.downloadEntryList(eventorId, eventId, raceId)
        
        // Then
        assertEquals(0, count)
        verify(competitorRepository, never()).save(any())
    }
}
