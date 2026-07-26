package ru.yavasilek.netpulse.update

object VersionComparator {
    fun isNewer(candidate: String, current: String): Boolean =
        compare(candidate, current) > 0

    fun compare(left: String, right: String): Int {
        val a = Version.parse(left)
        val b = Version.parse(right)
        val maxParts = maxOf(a.parts.size, b.parts.size)
        for (index in 0 until maxParts) {
            val leftPart = a.parts.getOrElse(index) { 0 }
            val rightPart = b.parts.getOrElse(index) { 0 }
            if (leftPart != rightPart) return leftPart.compareTo(rightPart)
        }
        return when {
            a.preRelease == null && b.preRelease != null -> 1
            a.preRelease != null && b.preRelease == null -> -1
            else -> comparePreRelease(a.preRelease, b.preRelease)
        }
    }

    private fun comparePreRelease(left: String?, right: String?): Int {
        if (left == null && right == null) return 0
        val a = left.orEmpty().split('.')
        val b = right.orEmpty().split('.')
        val count = maxOf(a.size, b.size)
        for (index in 0 until count) {
            val leftPart = a.getOrNull(index) ?: return -1
            val rightPart = b.getOrNull(index) ?: return 1
            val leftNumber = leftPart.toIntOrNull()
            val rightNumber = rightPart.toIntOrNull()
            val result = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> leftPart.compareTo(rightPart)
            }
            if (result != 0) return result
        }
        return 0
    }

    private data class Version(
        val parts: List<Int>,
        val preRelease: String?,
    ) {
        companion object {
            fun parse(raw: String): Version {
                val normalized = raw.trim()
                    .removePrefix("v")
                    .substringBefore('+')
                val core = normalized.substringBefore('-')
                val parts = core.split('.').map { part ->
                    part.takeWhile(Char::isDigit).toIntOrNull() ?: 0
                }
                return Version(
                    parts = parts,
                    preRelease = normalized.substringAfter('-', "").ifBlank { null },
                )
            }
        }
    }
}
