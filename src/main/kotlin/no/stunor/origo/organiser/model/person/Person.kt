package no.stunor.origo.organiser.model.person

data class Person(
        var id: String? = null,
        var eventorId: String = "",
        var personId: String = "",
        var name: PersonName = PersonName(),
        var birthYear: Int = 0,
        var nationality: String = "",
        var gender: Gender = Gender.Other,
        var mobilePhone: String? = null,
        var email: String? = null,
        var memberships: Map<String, MembershipType> = HashMap(),
)
