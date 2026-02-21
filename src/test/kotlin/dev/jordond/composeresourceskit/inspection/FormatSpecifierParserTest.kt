package dev.jordond.composeresourceskit.inspection

import junit.framework.TestCase

class FormatSpecifierParserTest : TestCase() {
  fun testExtractsValidSpecifiers() {
    val specifiers = FormatSpecifierParser.extractSpecifiers("Hello %1\$s, you have %2\$d items")
    assertEquals(2, specifiers.size)
    assertEquals(1, specifiers[0].position)
    assertEquals('s', specifiers[0].type)
    assertEquals(2, specifiers[1].position)
    assertEquals('d', specifiers[1].type)
  }

  fun testNoSpecifiers() {
    val specifiers = FormatSpecifierParser.extractSpecifiers("Hello world")
    assertTrue(specifiers.isEmpty())
  }

  fun testDoesNotExtractInvalidSpecifiers() {
    val specifiers = FormatSpecifierParser.extractSpecifiers("Hello %s, value %f")
    assertTrue("Unpositioned specifiers should not be extracted", specifiers.isEmpty())
  }

  fun testCountsDistinctPositions() {
    assertEquals(2, FormatSpecifierParser.countFormatArguments("Hello %1\$s, you have %2\$d items"))
  }

  fun testCountsReusedPositions() {
    // Same position used twice should still count as 1 argument
    assertEquals(1, FormatSpecifierParser.countFormatArguments("%1\$s and %1\$s again"))
  }

  fun testCountsZeroForNoSpecifiers() {
    assertEquals(0, FormatSpecifierParser.countFormatArguments("Hello world"))
  }

  fun testCountsMaxPosition() {
    // %3$s means 3 arguments are needed (positions 1, 2, 3)
    assertEquals(3, FormatSpecifierParser.countFormatArguments("%1\$s %3\$s"))
  }

  // -- validate --

  fun testValidStringPasses() {
    val issues = FormatSpecifierParser.validate("Hello %1\$s, you have %2\$d items")
    assertTrue(issues.isEmpty())
  }

  fun testDetectsUnpositionedPercentS() {
    val issues = FormatSpecifierParser.validate("Hello %s")
    val invalidSpecifiers = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertEquals(1, invalidSpecifiers.size)
    assertEquals("%s", invalidSpecifiers[0].specifier)
  }

  fun testDetectsUnsupportedPercentF() {
    val issues = FormatSpecifierParser.validate("Value: %1\$f")
    val invalidSpecifiers = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertEquals(1, invalidSpecifiers.size)
  }

  fun testDetectsNonSequentialNumbering() {
    val issues = FormatSpecifierParser.validate("%1\$s and %3\$s")
    val gapIssues = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.NonSequentialNumbering>()
    assertEquals(1, gapIssues.size)
    assertEquals(listOf(1, 2, 3), gapIssues[0].expected)
    assertEquals(listOf(1, 3), gapIssues[0].actual)
  }

  fun testSequentialNumberingPasses() {
    val issues = FormatSpecifierParser.validate("%1\$s and %2\$s")
    val gapIssues = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.NonSequentialNumbering>()
    assertTrue(gapIssues.isEmpty())
  }

  fun testEscapedPercentNotFlagged() {
    val issues = FormatSpecifierParser.validate("100%% complete")
    val invalidSpecifiers = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Escaped %% should not be flagged", invalidSpecifiers.isEmpty())
  }

  // -- Percent and dollar sign edge cases --

  fun testDollarAmountNotFlagged() {
    val issues = FormatSpecifierParser.validate("Price: \$10.00")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Dollar amount should not be flagged", invalid.isEmpty())
  }

  fun testDollarPrefixNotFlagged() {
    val issues = FormatSpecifierParser.validate("\$variable_name")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Dollar prefix should not be flagged", invalid.isEmpty())
  }

  fun testTrailingPercentNotFlagged() {
    val issues = FormatSpecifierParser.validate("100%")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Trailing percent with no letter after should not be flagged", invalid.isEmpty())
  }

  fun testPercentSpaceWordNotFlagged() {
    val issues = FormatSpecifierParser.validate("50% off")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Percent followed by space then word should not be flagged", invalid.isEmpty())
  }

  fun testEscapedPercentFollowedByTextNotFlagged() {
    val issues = FormatSpecifierParser.validate("50%%off")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Escaped %% immediately followed by text should not be flagged", invalid.isEmpty())
  }

  fun testMixedDollarAndEscapedPercentNotFlagged() {
    val issues = FormatSpecifierParser.validate("Save \$5 (50%% off)")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Mixed dollar and escaped percent should not be flagged", invalid.isEmpty())
  }

  fun testDollarBeforeValidSpecifierNotFlagged() {
    val issues = FormatSpecifierParser.validate("\$%1\$s")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Dollar before valid specifier should not produce extra errors", invalid.isEmpty())
  }

  fun testDollarAfterValidSpecifierNotFlagged() {
    val issues = FormatSpecifierParser.validate("Price is %1\$s\$")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Dollar after valid specifier should not be flagged", invalid.isEmpty())
  }

  fun testMixedEverythingWithValidSpecifier() {
    val issues = FormatSpecifierParser.validate("100%% of \$50 spent on %1\$s")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Mixed content with valid specifier should not produce false positives", invalid.isEmpty())
  }

  fun testPercentDollarLetterFlagged() {
    val issues = FormatSpecifierParser.validate("%\$s")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertFalse("Malformed %\$s should be flagged as invalid", invalid.isEmpty())
  }

  fun testPercentImmediatelyFollowedByWordFlagged() {
    val issues = FormatSpecifierParser.validate("50%off")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertFalse("Percent immediately followed by word should be flagged", invalid.isEmpty())
  }

  fun testTriplePercentThenText() {
    // %%% = escaped %% then lone %, the lone % + text could match
    val issues = FormatSpecifierParser.validate("%%%s")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertFalse("Third percent in %%%s should be flagged as unpositioned %s", invalid.isEmpty())
  }

  fun testLonePercentNotFlagged() {
    val issues = FormatSpecifierParser.validate("%")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Lone percent at end of string should not be flagged", invalid.isEmpty())
  }

  fun testLoneDollarNotFlagged() {
    val issues = FormatSpecifierParser.validate("\$")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Lone dollar sign should not be flagged", invalid.isEmpty())
  }

  fun testPercentInParenthesesNotFlagged() {
    val issues = FormatSpecifierParser.validate("(50%) discount")
    val invalid = issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InvalidSpecifier>()
    assertTrue("Percent followed by ) should not be flagged", invalid.isEmpty())
  }

  // -- validatePluralsConsistency --

  fun testConsistentPlurals() {
    val variants = mapOf(
      "one" to "%1\$d item",
      "other" to "%1\$d items",
    )
    val issues = FormatSpecifierParser.validatePluralsConsistency(variants)
    assertTrue(issues.isEmpty())
  }

  fun testInconsistentPlurals() {
    val variants = mapOf(
      "one" to "%1\$d item",
      "other" to "%1\$d items by %2\$s",
    )
    val issues = FormatSpecifierParser.validatePluralsConsistency(variants)
    val inconsistencies =
      issues.filterIsInstance<FormatSpecifierParser.ValidationResult.InconsistentPluralsSpecifiers>()
    assertEquals(1, inconsistencies.size)
    assertEquals("one", inconsistencies[0].variant)
    assertEquals(1, inconsistencies[0].variantCount)
    assertEquals(2, inconsistencies[0].expectedCount)
  }

  fun testSingleVariantSkipsConsistencyCheck() {
    val variants = mapOf("other" to "%1\$d items")
    val issues = FormatSpecifierParser.validatePluralsConsistency(variants)
    assertTrue(issues.isEmpty())
  }
}
