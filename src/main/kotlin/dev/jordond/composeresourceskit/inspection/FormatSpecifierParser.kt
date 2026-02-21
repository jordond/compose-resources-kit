package dev.jordond.composeresourceskit.inspection

/**
 * Parses and validates Compose Multiplatform format specifiers.
 *
 * Compose Multiplatform only supports `%N$s` and `%N$d` (1-indexed positional specifiers).
 * Unlike Java's String.format(), unpositioned `%s`/`%d`, width/precision modifiers, and
 * other conversion types (`%f`, `%x`, `%b`, etc.) are NOT supported.
 */
object FormatSpecifierParser {
  /** Matches valid Compose format specifiers: %1$s, %2$d, etc. */
  private val VALID_SPECIFIER = Regex("""%(\d+)\$([sd])""")

  /** Matches any %-sequence that looks like a format specifier (valid or not). */
  private val ANY_PERCENT_SEQUENCE = Regex("""%(?!%)[\d$\w.*-+#(]*[a-zA-Z]""")

  /** Matches literal %% (escaped percent). */
  private val ESCAPED_PERCENT = Regex("""%%""")

  data class SpecifierInfo(
    val position: Int,
    val type: Char,
    val fullMatch: String,
    val range: IntRange,
  )

  sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class InvalidSpecifier(
      val specifier: String,
      val range: IntRange,
    ) : ValidationResult

    data class NonSequentialNumbering(
      val expected: List<Int>,
      val actual: List<Int>,
    ) : ValidationResult

    data class InconsistentPluralsSpecifiers(
      val variant: String,
      val variantCount: Int,
      val expectedCount: Int,
      val expectedPositions: List<Int>,
    ) : ValidationResult
  }

  /**
   * Extracts all valid format specifiers from a string.
   */
  fun extractSpecifiers(text: String): List<SpecifierInfo> =
    VALID_SPECIFIER
      .findAll(text)
      .map { match ->
        SpecifierInfo(
          position = match.groupValues[1].toInt(),
          type = match.groupValues[2][0],
          fullMatch = match.value,
          range = match.range,
        )
      }.toList()

  /**
   * Counts the number of distinct format argument positions in a string.
   * e.g., "Hello %1$s, you have %2$d items" -> 2
   */
  fun countFormatArguments(text: String): Int = extractSpecifiers(text).maxOfOrNull { it.position } ?: 0

  /**
   * Validates format specifiers in a single string value.
   * Returns a list of validation issues found.
   */
  fun validate(text: String): List<ValidationResult> {
    val issues = mutableListOf<ValidationResult>()

    // Find all %-sequences and check if they're valid
    val validRanges = VALID_SPECIFIER.findAll(text).map { it.range }.toSet()
    val escapedRanges = ESCAPED_PERCENT.findAll(text).map { it.range }.toSet()

    ANY_PERCENT_SEQUENCE.findAll(text).forEach { match ->
      val isValid = validRanges.any { it.first == match.range.first }
      val isEscaped = escapedRanges.any { match.range.first in it }
      if (!isValid && !isEscaped) {
        issues.add(ValidationResult.InvalidSpecifier(match.value, match.range))
      }
    }

    // Check sequential numbering
    val specifiers = extractSpecifiers(text)
    if (specifiers.isNotEmpty()) {
      val positions = specifiers.map { it.position }.distinct().sorted()
      val expected = (1..positions.max()).toList()
      if (positions != expected) {
        issues.add(ValidationResult.NonSequentialNumbering(expected, positions))
      }
    }

    return issues
  }

  /**
   * Validates that all plural variants have consistent format specifiers.
   * Takes a map of quantity -> text content (e.g., "one" -> "%1$d item", "other" -> "%1$d items").
   * Returns issues if variants have different specifier counts or positions.
   */
  fun validatePluralsConsistency(variants: Map<String, String>): List<ValidationResult> {
    if (variants.size <= 1) return emptyList()

    val issues = mutableListOf<ValidationResult>()
    val variantSpecifiers = variants.mapValues { (_, text) ->
      extractSpecifiers(text).map { it.position }.distinct().sorted()
    }

    // Use the variant with the most specifiers as the reference
    val reference = variantSpecifiers.maxByOrNull { it.value.size } ?: return emptyList()
    val expectedPositions = reference.value
    val expectedCount = expectedPositions.size

    for ((variant, positions) in variantSpecifiers) {
      if (variant == reference.key) continue
      if (positions != expectedPositions) {
        issues.add(
          ValidationResult.InconsistentPluralsSpecifiers(
            variant = variant,
            variantCount = positions.size,
            expectedCount = expectedCount,
            expectedPositions = expectedPositions,
          ),
        )
      }
    }

    return issues
  }
}
