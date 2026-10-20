package com.example.netraai.feedback

import com.example.netraai.detection.Direction
import com.example.netraai.detection.Distance

/**
 * Localized string mappings for obstacle alert sentences in English and Hindi.
 */
object AlertStrings {

    private val labelMapEn = mapOf(
        "car" to "Car",
        "bus" to "Bus",
        "truck" to "Truck",
        "motorcycle" to "Motorcycle",
        "bicycle" to "Bicycle",
        "person" to "Person",
        "dog" to "Dog",
        "cow" to "Cow",
        "horse" to "Horse",
        "cat" to "Cat",
        "chair" to "Chair",
        "bench" to "Bench",
        "couch" to "Sofa",
        "sofa" to "Sofa",
        "table" to "Table",
        "dining table" to "Table",
        "potted plant" to "Plant",
        "door" to "Door",
        "traffic light" to "Traffic light",
        "stop sign" to "Stop sign",
        "fire hydrant" to "Fire hydrant",
        "backpack" to "Backpack",
        "umbrella" to "Umbrella",
        "suitcase" to "Suitcase"
    )

    private val labelMapHi = mapOf(
        "car" to "कार",
        "bus" to "बस",
        "truck" to "ट्रक",
        "motorcycle" to "मोटरसाइकिल",
        "bicycle" to "साइकिल",
        "person" to "व्यक्ति",
        "dog" to "कुत्ता",
        "cow" to "गाय",
        "horse" to "घोड़ा",
        "cat" to "बिल्ली",
        "chair" to "कुर्सी",
        "bench" to "बेंच",
        "couch" to "सोफा",
        "sofa" to "सोफा",
        "table" to "मेज़",
        "dining table" to "मेज़",
        "potted plant" to "पौधा",
        "door" to "दरवाज़ा",
        "traffic light" to "ट्रैफिक लाइट",
        "stop sign" to "स्टॉप साइन",
        "fire hydrant" to "फायर हाइड्रेंट",
        "backpack" to "बैग",
        "umbrella" to "छाता",
        "suitcase" to "सूटकेस"
    )

    fun getLabel(rawLabel: String, language: String): String {
        val key = rawLabel.trim().lowercase()
        return if (language.equals("hi", ignoreCase = true)) {
            labelMapHi[key] ?: key
        } else {
            labelMapEn[key] ?: key.replaceFirstChar { it.uppercase() }
        }
    }

    fun getDirection(direction: Direction, language: String): String {
        val isHindi = language.equals("hi", ignoreCase = true)
        return when (direction) {
            Direction.LEFT -> if (isHindi) "बाईं ओर" else "left"
            Direction.AHEAD -> if (isHindi) "सामने" else "ahead"
            Direction.RIGHT -> if (isHindi) "दाईं ओर" else "right"
        }
    }

    fun getDistance(distance: Distance, language: String): String {
        val isHindi = language.equals("hi", ignoreCase = true)
        return when (distance) {
            Distance.VERY_CLOSE -> if (isHindi) "बहुत पास" else "very close"
            Distance.NEAR -> if (isHindi) "पास" else "nearby"
            Distance.FAR -> if (isHindi) "दूर" else "far"
        }
    }

    /**
     * Builds short, clear alert sentences, e.g.:
     * English: "Car ahead, very close."
     * Hindi: "कार सामने, बहुत पास।"
     */
    fun buildAlertSentence(
        rawLabel: String,
        direction: Direction,
        distance: Distance,
        language: String
    ): String {
        val isHindi = language.equals("hi", ignoreCase = true)
        val labelStr = getLabel(rawLabel, language)
        val dirStr = getDirection(direction, language)
        val distStr = getDistance(distance, language)

        return if (isHindi) {
            "$labelStr $dirStr, $distStr।"
        } else {
            "$labelStr $dirStr, $distStr."
        }
    }
}
