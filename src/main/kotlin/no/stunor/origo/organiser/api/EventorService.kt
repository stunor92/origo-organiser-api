package no.stunor.origo.organiser.api

import no.stunor.origo.organiser.model.Eventor
import org.iof.EntryList
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import java.time.format.DateTimeFormatter
import kotlin.jvm.java

@Service
class EventorService {
    private var restTemplate: RestTemplate = RestTemplate()

    companion object {
        private const val TIMEOUT = 6000
    }

    init {
        val rf = restTemplate.requestFactory as SimpleClientHttpRequestFactory
        rf.setReadTimeout(TIMEOUT)
        rf.setConnectTimeout(TIMEOUT)

        val converters: MutableList<HttpMessageConverter<*>> = ArrayList()
        converters.add(Jaxb2RootElementHttpMessageConverter())
        restTemplate.messageConverters = converters
    }

    fun getEventEntryList(eventor: Eventor, eventId: String): EntryList? {
        val headers = HttpHeaders()
        headers["ApiKey"] = eventor.eventorApiKey

        val request = HttpEntity<String>(headers)
        val response = restTemplate.exchange<EntryList>(
            eventor.baseUrl + "api/entries?includePersonElement=true&includeEntryFees=true&eventIds=" + eventId,
            HttpMethod.GET,
            request,
            1
        )
        return response.body
    }

}
