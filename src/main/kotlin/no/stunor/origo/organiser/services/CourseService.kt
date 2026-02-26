package no.stunor.origo.organiser.services

import no.stunor.origo.organiser.data.*
import no.stunor.origo.organiser.exception.EventNotFoundException
import no.stunor.origo.organiser.model.courses.*
import no.stunor.origo.organiser.model.event.EventClass
import org.iof.ClassCourseAssignment
import org.iof.CourseData
import org.iof.RaceCourseData
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
open class CourseService {

    @Autowired
    private lateinit var eventRepository: EventRepository

    @Autowired
    private lateinit var raceRepository: RaceRepository

    @Autowired
    private lateinit var mapRepository: MapRepository

    @Autowired
    private lateinit var controlRepository: ControlRepository

    @Autowired
    private lateinit var courseRepository: CourseRepository

    @Autowired
    private lateinit var legRepository: LegRepository

    @Autowired
    private lateinit var classCourseRepository: ClassCourseRepository

    @Autowired
    private lateinit var legControlRepository: LegControlRepository

    @Transactional
    open fun saveCourse(raceId: UUID, name: String, courseData: CourseData) {

        require(!(courseData.raceCourseData.isNullOrEmpty())) {
            "CourseData must contain at least one RaceCourseData element."
        }

        // Get the race from database
        val race = raceRepository.findById(raceId) ?: throw EventNotFoundException()

        // Get event from database to match classes
        val event = eventRepository.findById(race.eventId) ?: throw EventNotFoundException()

        // Get the IOF event data from the XML (contains class definitions)
        val iofEvent = courseData.event

        // Map IOF classes to database EventClass IDs (handle null/empty class list)
        val classMapping = if (iofEvent?.clazz != null && iofEvent.clazz.isNotEmpty()) {
            mapIofClassesToDatabase(iofEvent.clazz, event.classes.toList())
        } else {
            emptyMap()
        }

        // Get controls and courses data
        val raceData = courseData.raceCourseData.first()

        val controlsData = getControlsData(raceData)
        val coursesData = getCoursesData(raceData, classMapping)

        // Create and save the map
        val mapData = courseData.raceCourseData.first().map.firstOrNull()
        val map = RaceMap(
            raceId = race.id,
            name = name,
            scale = mapData?.scale?.toInt(),
            mapArea = getMapArea(mapData)
        )
        val savedMap = mapRepository.save(map)

        // Save controls with reference to the map
        val savedControls = mutableMapOf<String, Control>()
        controlsData.forEach { control ->
            control.mapId = savedMap.id
            val savedControl = controlRepository.save(control)
            savedControls[savedControl.controlCode] = savedControl
        }

        // Save courses with their variants and legs
        coursesData.forEach { courseDataItem ->
            courseDataItem.course.mapId = savedMap.id
            val savedCourse = courseRepository.save(courseDataItem.course)

            // Save variants and legs
            courseDataItem.variants.forEach { variantData ->
                variantData.variant.courseId = savedCourse.id
                val savedVariant = courseRepository.save(Course(
                    id = savedCourse.id,
                    name = savedCourse.name,
                    mapId = savedCourse.mapId,
                    variants = mutableSetOf(variantData.variant)
                )).variants.first()

                // Save legs and link to controls
                variantData.legs.forEach { legData ->
                    legData.leg.courseVariantId = savedVariant.id
                    val savedLeg = legRepository.save(legData.leg)

                    // Link leg to controls via join table
                    legData.controlCodes.forEach { controlCode ->
                        val control = savedControls[controlCode]
                        if (control != null && control.id != null && savedLeg.id != null) {
                            legControlRepository.linkLegToControl(savedLeg.id!!, control.id!!)
                        }
                    }
                }
            }

            // Link course to classes via join table
            courseDataItem.classIds.forEach { classId ->
                if (savedCourse.id != null) {
                    classCourseRepository.linkClassToCourse(classId, savedCourse.id!!)
                }
            }
        }
    }

    /**
     * Maps IOF XML classes to database EventClass IDs by matching class names
     */
    private fun mapIofClassesToDatabase(
        iofClasses: List<org.iof.Class>,
        dbClasses: List<EventClass>
    ): Map<String, UUID> {
        val mapping = mutableMapOf<String, UUID>()

        iofClasses.forEach { iofClass ->
            val className = iofClass.name
            val matchingDbClass = dbClasses.find { it.name.equals(className, ignoreCase = true) }

            if (matchingDbClass != null) {
                mapping[className] = matchingDbClass.id
                println("Mapped class: '$className' -> ${matchingDbClass.id}")
            } else {
                println("Warning: Class '$className' from XML not found in database")
            }
        }

        return mapping
    }

    private data class CourseDataItem(
        val course: Course,
        val variants: List<VariantData>,
        val classIds: List<UUID>
    )

    private data class VariantData(
        val variant: CourseVariant,
        val legs: List<LegData>
    )

    private data class LegData(
        val leg: Leg,
        val controlCodes: List<String>
    )

    private fun getCoursesData(
        raceData: RaceCourseData,
        classMapping: Map<String, UUID>
    ): List<CourseDataItem> {
        val coursesData = mutableListOf<CourseDataItem>()
        val coursesMap = mutableMapOf<String, CourseDataItem>()

        for (course in raceData.course) {
            val courseFamily = if (!course.courseFamily.isNullOrBlank()) course.courseFamily else course.name

            if (coursesMap.containsKey(courseFamily)) {
                // Add variant to existing course
                val existingCourseData = coursesMap[courseFamily] ?: continue
                val legsData = getLegsData(course.courseControl)
                val variant = CourseVariant(
                    name = course.name,
                    length = course.length,
                    climb = course.climb
                )
                val variantData = VariantData(variant, legsData)

                // Update the course data with additional variant and classes
                val classIds = getClassIds(course, raceData.classCourseAssignment, classMapping)
                val updatedClassIds = (existingCourseData.classIds + classIds).distinct()
                val updatedCourseData = existingCourseData.copy(
                    variants = existingCourseData.variants + variantData,
                    classIds = updatedClassIds
                )
                coursesMap[courseFamily] = updatedCourseData
            } else {
                // Create new course
                val classIds = getClassIds(course, raceData.classCourseAssignment, classMapping)
                val newCourse = Course(
                    name = courseFamily
                )
                val legsData = getLegsData(course.courseControl)
                val variant = CourseVariant(
                    name = if (!course.courseFamily.isNullOrBlank()) course.name else null,
                    length = course.length,
                    climb = course.climb
                )
                val variantData = VariantData(variant, legsData)
                val courseDataItem = CourseDataItem(newCourse, listOf(variantData), classIds)
                coursesMap[courseFamily] = courseDataItem
                coursesData.add(courseDataItem)
            }
        }

        return coursesData
    }

    private fun getClassIds(
        course: org.iof.Course,
        classCourseAssignment: List<ClassCourseAssignment>?,
        classMapping: Map<String, UUID>
    ): List<UUID> {
        // If no class assignments provided, return empty list
        if (classCourseAssignment.isNullOrEmpty()) {
            return emptyList()
        }

        val classNames = classCourseAssignment
            .filter { it.courseName == course.name }
            .map { it.className }

        return classNames.mapNotNull { className ->
            classMapping[className] ?: run {
                null
            }
        }
    }

    private fun getLegsData(
        courseControls: List<org.iof.CourseControl>
    ): List<LegData> {
        val legsData = mutableListOf<LegData>()
        courseControls.forEachIndexed { index, legDef ->
            val controlCodes = legDef.control.toList()
            val leg = Leg(
                sequenceNumber = (legDef.mapText?.toIntOrNull() ?: (index + 1)),
                length = legDef.legLength
            )
            legsData.add(LegData(leg, controlCodes))
        }
        return legsData
    }

    private fun getControlsData(raceData: RaceCourseData): List<Control> {
        val controls = mutableListOf<Control>()
        for (control in raceData.control) {
            controls.add(getControl(control, raceData.course))
        }
        return controls
    }

    private fun getControl(control: org.iof.Control, courses: List<org.iof.Course>): Control {
        return Control(
            type = getControlType(control, courses),
            controlCode = control.id.value,
            geoPosition = getGeoPosition(control.position),
            mapPosition = getPosition(control.mapPosition)
        )
    }

    private fun getControlType(control: org.iof.Control, courses: List<org.iof.Course>): ControlType {
        if (control.type != null) {
            return convertControlType(control.type)
        }
        for (course in courses) {
            for (courseControl in course.courseControl) {
                if (courseControl.control.contains(control.id.value)) {
                    return convertControlType(courseControl.type)
                }
            }
        }
        return ControlType.Control
    }

    private fun convertControlType(type: org.iof.ControlType): ControlType {
        return when (type) {
            org.iof.ControlType.START -> ControlType.Start
            org.iof.ControlType.FINISH -> ControlType.Finish
            org.iof.ControlType.CROSSING_POINT -> ControlType.CrossingPoint
            org.iof.ControlType.END_OF_MARKED_ROUTE -> ControlType.EndOfMarkedRoute
            else -> ControlType.Control
        }
    }

    private fun getGeoPosition(position: org.iof.GeoPosition?): GeoPosition? {
        if (position == null) {
            return null
        }
        return GeoPosition(position.lat, position.lng)
    }

    private fun getMapArea(map: org.iof.Map?): MapArea? {
        if (map == null) {
            return null
        }
        val topLeft = getPosition(map.mapPositionTopLeft)
        val bottomRight = getPosition(map.mapPositionBottomRight)
        return MapArea(
            topLeftX = topLeft.x,
            topLeftY = topLeft.y,
            bottomRightX = bottomRight.x,
            bottomRightY = bottomRight.y
        )
    }

    private fun getPosition(mapPosition: org.iof.MapPosition): MapPosition {
        return MapPosition(
            x = mapPosition.x,
            y = mapPosition.y
        )
    }
}

