package no.stunor.origo.organiser.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class EventorNotFoundException(eventorId: String) : RuntimeException("Eventor $eventorId is not found")
