package org.adaway.update

/**
 * A version name as the releases are tagged: the version, then a suffix such as `-c`. A stage
 * (alpha, beta, rc) or a number in the suffix marks a pre-release of the version, as in
 * `7.0.0-alpha-c` or `7.0.0-beta2-c`, older than the version. A number alone counts as a beta.
 */
class AppVersion(name: String) : Comparable<AppVersion> {
    private val numbers: List<Int>

    /** Index of the pre-release stage in [STAGES], null for a release. */
    private val stage: Int?
    private val preRelease: List<Int>

    init {
        val core = name.trim().removePrefix("v").substringBefore('-')
        val suffix = name.substringAfter('-', missingDelimiterValue = "")
        numbers = numbersIn(core)
        preRelease = numbersIn(suffix)
        val words = WORD.findAll(suffix.lowercase()).map { it.value }.toList()
        stage = STAGES.indexOfFirst { it in words }.takeIf { it >= 0 }
            ?: STAGES.indexOf("beta").takeIf { preRelease.isNotEmpty() }
    }

    val isPreRelease: Boolean get() = stage != null

    override fun compareTo(other: AppVersion): Int {
        compareNumbers(numbers, other.numbers).takeIf { it != 0 }?.let { return it }
        return when {
            stage != null && other.stage != null ->
                stage.compareTo(other.stage).takeIf { it != 0 } ?: compareNumbers(preRelease, other.preRelease)
            isPreRelease -> -1
            other.isPreRelease -> 1
            else -> 0
        }
    }

    private companion object {
        val NUMBER = Regex("\\d+")
        val WORD = Regex("[a-z]+")
        val STAGES = listOf("alpha", "beta", "rc")

        fun numbersIn(text: String) = NUMBER.findAll(text).map { it.value.toIntOrNull() ?: 0 }.toList()

        /** Missing numbers count as zeros: 7.0 is 7.0.0. */
        fun compareNumbers(left: List<Int>, right: List<Int>): Int {
            for (index in 0 until maxOf(left.size, right.size)) {
                val difference = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
                if (difference != 0) return difference
            }
            return 0
        }
    }
}
