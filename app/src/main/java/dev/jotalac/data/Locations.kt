package dev.jotalac.data

import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import dev.jotalac.core.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/** An event's location is free text: an address, a place name or a meeting link. */
object EventLocation {
    private val webLink = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    /** The first web link in [location], such as a Zoom or Meet link, or null. */
    fun meetingUrl(location: String): String? = webLink.find(location)?.value?.trimEnd('.', ',', ';', ')')

    /** Distinct locations of [events], the latest event's first. */
    fun recent(events: List<Event>): List<String> = events
        .sortedByDescending { it.from }
        .map { it.location.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

    /** Up to [limit] of [past] containing [query], in their order; the most recent ones for a blank query. */
    fun matching(past: List<String>, query: String, limit: Int): List<String> {
        val text = query.trim()
        return past.asSequence()
            .filter { text.isEmpty() || (it.contains(text, ignoreCase = true) && !it.equals(text, ignoreCase = true)) }
            .take(limit)
            .toList()
    }

    /** Opens the meeting link in [location], or else searches for the place in the maps app; null when blank. */
    fun intent(location: String): Intent? {
        val text = location.trim()
        if (text.isEmpty()) return null
        val uri = meetingUrl(text)?.toUri() ?: "geo:0,0?q=${Uri.encode(text)}".toUri()
        return Intent(Intent.ACTION_VIEW, uri)
    }
}

/**
 * Address suggestions from Android's Geocoder, the phone's own location service (Google's on most
 * phones). It needs internet; without a geocoder or on errors there are simply no suggestions.
 */
class AddressSearch(private val context: Context) {

    suspend fun search(query: String, limit: Int = 5): List<String> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(query, limit, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(addresses)
                    override fun onError(errorMessage: String?) = continuation.resume(emptyList())
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocationName(query, limit) }.getOrNull().orEmpty()
            }
        }
        return addresses.mapNotNull { it.label() }.distinctBy { it.lowercase() }
    }

    /** The full address, led by the place's name when it is not already part of it ("Eiffel Tower, Av. …"). */
    private fun Address.label(): String? {
        val line = if (maxAddressLineIndex >= 0) getAddressLine(0) else null
        val name = featureName?.takeIf { name -> name.any { it.isLetter() } && line?.contains(name, ignoreCase = true) != true }
        return listOfNotNull(name, line).joinToString(", ").ifBlank { null }
    }
}
