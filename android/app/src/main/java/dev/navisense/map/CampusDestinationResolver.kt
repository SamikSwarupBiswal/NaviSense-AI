package dev.navisense.map

import java.util.Locale

sealed class CampusResolutionResult {
    data class ExactMatch(val poi: MapPOI) : CampusResolutionResult()
    data class DisambiguationRequired(val candidates: List<MapPOI>) : CampusResolutionResult()
    data class OffCampusQuery(val cleanQuery: String) : CampusResolutionResult()
    data class UnknownDestination(val rawQuery: String) : CampusResolutionResult()
}

/**
 * Deterministic destination resolver prioritizing the 11 verified VIT Chennai campus POIs,
 * standard aliases, token disambiguation, and off-campus query detection.
 */
object CampusDestinationResolver {

    private val PREFIXES = listOf(
        "take me to", "i want to go to", "navigate to", "directions to",
        "find route to", "route to", "walk to", "go to", "bring me to", "lead me to"
    )

    private val KNOWN_OFF_CAMPUS_TERMS = setOf(
        "phoenix mall", "mall", "airport", "central station", "marina beach",
        "tambaram", "guindy", "velachery", "vandalur zoo", "chennai central", "besant nagar"
    )

    // Canonical alias mappings for VIT Chennai POIs
    val DEFAULT_CAMPUS_ALIASES: Map<String, List<String>> = mapOf(
        "poi_academic_block_1" to listOf("ab1", "ab 1", "academic block 1", "academic block one", "block 1", "block one", "ab-1"),
        "poi_academic_block_2" to listOf("ab2", "ab 2", "academic block 2", "academic block two", "block 2", "block two", "ab-2", "computing labs"),
        "poi_academic_block_3" to listOf("ab3", "ab 3", "academic block 3", "academic block three", "block 3", "block three", "ab-3"),
        "poi_library" to listOf("library", "central library", "vit library", "reading room"),
        "poi_food_court" to listOf("food court", "ambrosia", "canteen", "ambrosia canteen", "cafeteria", "gazebo", "gazebo food court", "mess"),
        "poi_hostel_delta" to listOf("delta", "delta hostel", "delta block", "hostel delta", "boys hostel delta"),
        "poi_hostel_gamma" to listOf("gamma", "gamma hostel", "gamma block", "hostel gamma", "girls hostel gamma"),
        "poi_admin_block" to listOf("admin", "admin block", "administration", "administration block", "admissions", "finance office"),
        "poi_sports_complex" to listOf("sports", "sports complex", "sports ground", "ground", "basketball court", "football ground", "gym"),
        "poi_main_gate" to listOf("main gate", "entrance gate", "front gate", "security gate", "vit gate", "main entrance"),
        "poi_kelambakkam_road" to listOf("bus stop", "kelambakkam", "kelambakkam road", "vandalur road", "kelambakkam bus stop"),
        "poi_health_center" to listOf("health center", "medical", "clinic", "dispensary", "health clinic", "hospital"),
        "poi_auditorium" to listOf("auditorium", "netaji auditorium", "audi", "cultural center", "netaji subhash chandra bose auditorium"),
        "poi_swimming_pool" to listOf("swimming pool", "pool", "indoor gym", "badminton"),
        "poi_sbi_atm" to listOf("atm", "sbi atm", "sbi bank", "bank", "sbi")
    )

    fun cleanQuery(rawQuery: String?): String {
        if (rawQuery.isNullOrBlank()) return ""
        var q = rawQuery.lowercase(Locale.ROOT)
            .replace("'", " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        for (prefix in PREFIXES) {
            if (q.startsWith(prefix)) {
                q = q.removePrefix(prefix).trim()
                break
            }
        }
        return q
    }

    /**
     * Resolves a destination query against the list of available campus POIs.
     */
    fun resolve(rawQuery: String?, pois: List<MapPOI>): CampusResolutionResult {
        val q = cleanQuery(rawQuery)
        if (q.isBlank()) {
            return CampusResolutionResult.UnknownDestination(rawQuery ?: "")
        }

        // 1. Check known off-campus indicators
        for (offCampus in KNOWN_OFF_CAMPUS_TERMS) {
            if (q == offCampus || q.contains(offCampus)) {
                return CampusResolutionResult.OffCampusQuery(q)
            }
        }

        // 2. Exact POI ID match
        pois.find { it.id.equals(q, ignoreCase = true) }?.let {
            return CampusResolutionResult.ExactMatch(it)
        }

        // 3. Exact POI name match
        pois.find { it.name.lowercase(Locale.ROOT) == q }?.let {
            return CampusResolutionResult.ExactMatch(it)
        }

        // 4. Exact alias match from default mappings or POI aliases
        for (poi in pois) {
            val aliases = DEFAULT_CAMPUS_ALIASES[poi.id].orEmpty() + poi.aliases
            if (aliases.any { it.lowercase(Locale.ROOT) == q }) {
                return CampusResolutionResult.ExactMatch(poi)
            }
        }

        // 5. Tokenized / specific shortcut matching
        val tokens = q.split(" ").filter { it.isNotBlank() }

        // Specific AB matching: "ab1", "ab 1"
        if (q == "ab1" || q == "ab 1" || (tokens.contains("ab") && tokens.contains("1")) || (tokens.contains("block") && tokens.contains("1"))) {
            pois.find { it.id == "poi_academic_block_1" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (q == "ab2" || q == "ab 2" || (tokens.contains("ab") && tokens.contains("2")) || (tokens.contains("block") && tokens.contains("2"))) {
            pois.find { it.id == "poi_academic_block_2" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (q == "ab3" || q == "ab 3" || (tokens.contains("ab") && tokens.contains("3")) || (tokens.contains("block") && tokens.contains("3"))) {
            pois.find { it.id == "poi_academic_block_3" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }

        // Specific single-intent matches
        if (tokens.contains("library")) {
            pois.find { it.id == "poi_library" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (tokens.contains("ambrosia") || tokens.contains("canteen") || (tokens.contains("food") && tokens.contains("court")) || tokens.contains("gazebo")) {
            pois.find { it.id == "poi_food_court" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (tokens.contains("delta")) {
            pois.find { it.id == "poi_hostel_delta" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (tokens.contains("gamma")) {
            pois.find { it.id == "poi_hostel_gamma" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (tokens.contains("admin") || tokens.contains("admissions")) {
            pois.find { it.id == "poi_admin_block" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (tokens.contains("sports") || tokens.contains("ground")) {
            pois.find { it.id == "poi_sports_complex" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (tokens.contains("gate")) {
            pois.find { it.id == "poi_main_gate" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (tokens.contains("kelambakkam") || (tokens.contains("bus") && tokens.contains("stop"))) {
            pois.find { it.id == "poi_kelambakkam_road" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (tokens.contains("health") || tokens.contains("medical") || tokens.contains("clinic") || tokens.contains("dispensary")) {
            pois.find { it.id == "poi_health_center" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (tokens.contains("auditorium") || tokens.contains("audi") || tokens.contains("netaji")) {
            pois.find { it.id == "poi_auditorium" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (tokens.contains("pool") || tokens.contains("swimming") || (tokens.contains("indoor") && tokens.contains("gym"))) {
            pois.find { it.id == "poi_swimming_pool" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }
        if (tokens.contains("atm") || tokens.contains("sbi") || tokens.contains("bank")) {
            pois.find { it.id == "poi_sbi_atm" }?.let { return CampusResolutionResult.ExactMatch(it) }
        }

        // 6. Ambiguity checks (e.g. "hostel" without delta or gamma, "academic block" without number)
        if (tokens.contains("hostel")) {
            val hostels = pois.filter { it.category == "hostel" }
            if (hostels.isNotEmpty()) {
                return CampusResolutionResult.DisambiguationRequired(hostels)
            }
        }
        if (tokens.contains("academic") || tokens.contains("block")) {
            val blocks = pois.filter { it.category == "academic" }
            if (blocks.isNotEmpty()) {
                return CampusResolutionResult.DisambiguationRequired(blocks)
            }
        }

        // 7. Broad substring search on description or name
        val broadMatches = pois.filter { poi ->
            val n = poi.name.lowercase(Locale.ROOT)
            val d = poi.description.lowercase(Locale.ROOT)
            n.contains(q) || d.contains(q)
        }
        if (broadMatches.size == 1) {
            return CampusResolutionResult.ExactMatch(broadMatches.first())
        } else if (broadMatches.size > 1) {
            return CampusResolutionResult.DisambiguationRequired(broadMatches)
        }

        // 8. Fallback classification: if query has multiple words or location terms, treat as external/off-campus
        if (q.contains("road") || q.contains("street") || q.contains("nagar") || q.contains("station") ||
            q.contains("hospital") || q.contains("hotel") || q.contains("park") || q.contains("chennai")) {
            return CampusResolutionResult.OffCampusQuery(q)
        }

        return CampusResolutionResult.UnknownDestination(rawQuery ?: "")
    }
}
