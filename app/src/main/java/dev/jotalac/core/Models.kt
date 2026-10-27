package dev.jotalac.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** JSON settings shared by every call into the Go core. Go's json/v2 matches field names case-sensitively. */
val CoreJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** RFC 3339 with mandatory seconds, which Go's time.Time parser requires. */
val Rfc3339: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

fun ZonedDateTime.toRfc3339(): String = format(Rfc3339)

/** Encodes as RFC 3339 and decodes into the device's current time zone. */
object ZonedDateTimeSerializer : KSerializer<ZonedDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("ZonedDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ZonedDateTime) = encoder.encodeString(value.toRfc3339())

    override fun deserialize(decoder: Decoder): ZonedDateTime =
        OffsetDateTime.parse(decoder.decodeString()).atZoneSameInstant(ZoneId.systemDefault())
}

@Serializable
data class Reminder(
    @SerialName("minutes_before") val minutesBefore: Long,
)

/**
 * A calendar event. A null [id] means the event has not been created yet.
 * Child occurrences of a repeating event have [parentId] set and carry the parent's [repeat].
 */
@Serializable
data class Event(
    val id: String? = null,
    val title: String = "",
    val location: String = "",
    val description: String = "",
    @Serializable(ZonedDateTimeSerializer::class) val from: ZonedDateTime,
    @Serializable(ZonedDateTimeSerializer::class) val to: ZonedDateTime,
    val calendar: String = "",
    @SerialName("tag_id") val tagId: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    val repeat: String? = null,
    val reminders: List<Reminder> = emptyList(),
) {
    val isRepeating: Boolean get() = !repeat.isNullOrBlank()
}

@Serializable
data class Tag(
    val id: String? = null,
    val name: String,
    val color: String,
)

@Serializable
data class Calendar(
    val name: String,
    val tags: List<Tag>? = null,
    @SerialName("remote_url") val remoteUrl: String = "",
    @SerialName("ical_url") val icalUrl: String = "",
    val encrypted: Boolean = false,
    val readonly: Boolean = false,
) {
    val tagList: List<Tag> get() = tags.orEmpty()
    val isICal: Boolean get() = icalUrl.isNotEmpty()

    /** iCal subscriptions are read-only too, even though the core reports `readonly` only for Git remotes. */
    val isWritable: Boolean get() = !readonly && !isICal
}

@Serializable
data class Notification(
    val event: Event,
    @Serializable(ZonedDateTimeSerializer::class) val at: ZonedDateTime,
)

@Serializable
data class CalendarFilter(
    @SerialName("hidden_tag_ids") val hiddenTagIds: List<String> = emptyList(),
    @SerialName("hide_untagged") val hideUntagged: Boolean = false,
)

/** Which occurrences of a repeating event an update or removal applies to. Values match core.UpdateStrategy. */
enum class UpdateStrategy(val value: Long) {
    Current(0), Following(1), All(2),
}
