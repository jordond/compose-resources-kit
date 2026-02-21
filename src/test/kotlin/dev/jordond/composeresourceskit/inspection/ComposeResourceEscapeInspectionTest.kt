package dev.jordond.composeresourceskit.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ComposeResourceEscapeInspectionTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ComposeResourceEscapeInspection())
  }

  private fun addComposeResource(
    path: String,
    content: String,
  ) {
    myFixture.addFileToProject("composeResources/$path", content)
  }

  private fun configureAndHighlight(path: String = "composeResources/values/strings.xml") =
    myFixture.run {
      val file = findFileInTempDir(path)!!
      configureFromExistingVirtualFile(file)
      doHighlighting()
    }

  private fun escapeWarnings() =
    configureAndHighlight().filter {
      it.description?.contains("Unnecessary Android escape") == true ||
        it.description?.contains("unnecessary Android escapes") == true
    }

  // ── Unnecessary escapes that SHOULD be flagged ─────────────────────

  fun testEscapedApostrophe() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="apos">Let\'s go</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue(
      "Escaped apostrophe should be flagged",
      warnings.any { it.description?.contains("\\'") == true },
    )
  }

  fun testEscapedDoubleQuote() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="dquote">Say \"hello\"</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue(
      "Escaped double quotes should be flagged",
      warnings.any { it.description?.contains("\\\"") == true },
    )
  }

  fun testEscapedAtSign() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="at">\@string/ref</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue(
      "Escaped @ should be flagged",
      warnings.any { it.description?.contains("\\@") == true },
    )
  }

  fun testEscapedQuestionMark() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="question">Is it\?</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue(
      "Escaped ? should be flagged",
      warnings.any { it.description?.contains("\\?") == true },
    )
  }

  fun testMultipleUnnecessaryEscapes() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="multi">Let\'s say \"hello\"</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    // Individual per-escape warnings + one "fix all" warning
    assertTrue(
      "Multiple unnecessary escapes should produce individual warnings",
      warnings.count { it.description?.contains("Unnecessary Android escape '\\") == true } >= 3,
    )
    assertTrue(
      "Should offer a 'fix all' warning",
      warnings.any { it.description?.contains("unnecessary Android escapes") == true },
    )
  }

  fun testEscapesInPlurals() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <plurals name="items">
              <item quantity="one">%1${'$'}d item\'s total</item>
              <item quantity="other">%1${'$'}d items\'s total</item>
          </plurals>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue(
      "Escapes in plural items should be flagged",
      warnings.any { it.description?.contains("\\'") == true },
    )
  }

  // ── Valid content that should NOT be flagged ───────────────────────

  fun testPlainApostropheNotFlagged() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="plain">Let's go</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue("Plain apostrophe should not be flagged", warnings.isEmpty())
  }

  fun testValidNewlineEscape() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="newline">Line1\nLine2</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue("Valid \\n escape should not be flagged", warnings.isEmpty())
  }

  fun testValidTabEscape() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="tab">Col1\tCol2</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue("Valid \\t escape should not be flagged", warnings.isEmpty())
  }

  fun testValidEscapedBackslash() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="backslash">Back\\slash</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue("Valid \\\\ escape should not be flagged", warnings.isEmpty())
  }

  fun testValidUnicodeEscape() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="unicode">Heart \u2764</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue("Valid unicode escape should not be flagged", warnings.isEmpty())
  }

  fun testXmlEntitiesNotFlagged() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="entities">Tom &amp; Jerry &lt;3 &gt; &apos;hi&apos;</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue("XML entities should not be flagged", warnings.isEmpty())
  }

  fun testEscapedBackslashFollowedByApostrophe() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="dblback">\\'quote</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue(
      "\\\\' (escaped backslash + literal apostrophe) should not be flagged",
      warnings.isEmpty(),
    )
  }

  fun testPlainStringNotFlagged() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="plain">Hello world</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue("Plain text without escapes should not be flagged", warnings.isEmpty())
  }

  fun testEmptyStringNotFlagged() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="empty"></string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue("Empty string should not be flagged", warnings.isEmpty())
  }

  // ── Scoping ────────────────────────────────────────────────────────

  fun testIgnoresFilesOutsideComposeResources() {
    myFixture.addFileToProject(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="apos">Let\'s go</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("values/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)
    val highlights = myFixture.doHighlighting()
    val warnings = highlights.filter {
      it.description?.contains("Unnecessary Android escape") == true
    }

    assertTrue("Should not inspect XML outside composeResources", warnings.isEmpty())
  }

  fun testIgnoresNonValuesDirectory() {
    addComposeResource(
      "drawable/icon.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="apos">Let\'s go</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/drawable/icon.xml")!!
    myFixture.configureFromExistingVirtualFile(file)
    val highlights = myFixture.doHighlighting()
    val warnings = highlights.filter {
      it.description?.contains("Unnecessary Android escape") == true
    }

    assertTrue("Should not inspect XML in non-values directories", warnings.isEmpty())
  }

  // ── Edge cases ─────────────────────────────────────────────────────

  fun testEscapeAtStartOfString() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="start">\'hello</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue(
      "Escape at start of string should be flagged",
      warnings.any { it.description?.contains("\\'") == true },
    )
  }

  fun testEscapeAtEndOfString() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="end">hello\'</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue(
      "Escape at end of string should be flagged",
      warnings.any { it.description?.contains("\\'") == true },
    )
  }

  fun testOnlyEscapeCharacter() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="only">\'</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue(
      "String containing only an escape should be flagged",
      warnings.any { it.description?.contains("\\'") == true },
    )
  }

  fun testMixedValidAndInvalidEscapes() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="mixed">Line1\nLet\'s go</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue(
      "Should flag \\' but not \\n",
      warnings.any { it.description?.contains("\\'") == true },
    )
    assertTrue(
      "Should not flag \\n",
      warnings.none { it.description?.contains("\\n") == true },
    )
  }

  fun testAdjacentUnnecessaryEscapes() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="adjacent">\'\"</string>
      </resources>
      """.trimIndent(),
    )

    val warnings = escapeWarnings()
    assertTrue(
      "Both adjacent escapes should be flagged",
      warnings.any { it.description?.contains("\\'") == true },
    )
    assertTrue(
      "Both adjacent escapes should be flagged",
      warnings.any { it.description?.contains("\\\"") == true },
    )
  }

  fun testLocalizedValuesDirectory() {
    addComposeResource(
      "values-fr/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="greeting">C\'est la vie</string>
      </resources>
      """.trimIndent(),
    )

    val file = myFixture.findFileInTempDir("composeResources/values-fr/strings.xml")!!
    myFixture.configureFromExistingVirtualFile(file)
    val highlights = myFixture.doHighlighting()
    val warnings = highlights.filter {
      it.description?.contains("Unnecessary Android escape") == true
    }

    assertTrue(
      "Should detect escapes in localized values directories",
      warnings.isNotEmpty(),
    )
  }

  // ── Quick fixes ────────────────────────────────────────────────────

  fun testQuickFixRemovesSingleEscape() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="fix_me">Let\'s go</string>
      </resources>
      """.trimIndent(),
    )

    configureAndHighlight()

    val fix = myFixture.getAllQuickFixes().firstOrNull {
      it.text.contains("Remove unnecessary escape") && it.text.contains("\\'")
    }
    assertNotNull("Expected quick fix to remove escape", fix)
    myFixture.launchAction(fix!!)

    val text = myFixture.editor.document.text
    assertTrue("Should contain unescaped apostrophe", text.contains("Let's go"))
    assertFalse("Should not contain escaped apostrophe", text.contains("Let\\'s go"))
  }

  fun testQuickFixRemovesAllEscapes() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="fix_all">Let\'s say \"hello\"</string>
      </resources>
      """.trimIndent(),
    )

    configureAndHighlight()

    val fix = myFixture.getAllQuickFixes().firstOrNull {
      it.text.contains("Remove all unnecessary")
    }
    assertNotNull("Expected 'fix all' quick fix", fix)
    myFixture.launchAction(fix!!)

    val text = myFixture.editor.document.text
    assertTrue("Should contain unescaped text", text.contains("Let's say \"hello\""))
  }

  fun testQuickFixPreservesValidEscapes() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="preserve">Line1\nLet\'s go\\path</string>
      </resources>
      """.trimIndent(),
    )

    configureAndHighlight()

    val fix = myFixture.getAllQuickFixes().firstOrNull {
      it.text.contains("Remove unnecessary escape") && it.text.contains("\\'")
    }
    assertNotNull("Expected quick fix", fix)
    myFixture.launchAction(fix!!)

    val text = myFixture.editor.document.text
    assertTrue("Should preserve \\n", text.contains("\\n"))
    assertTrue("Should preserve \\\\", text.contains("\\\\"))
    assertTrue("Should remove \\'", text.contains("Let's go"))
  }

  // ── Unit tests for findUnnecessaryEscapes ──────────────────────────

  fun testParserFindsApostropheEscape() {
    val result = ComposeResourceEscapeInspection.findUnnecessaryEscapes("Let\\'s go")
    assertEquals(1, result.size)
    assertEquals('\'', result[0].character)
    assertEquals(3, result[0].position)
  }

  fun testParserFindsAllFourEscapeTypes() {
    val result = ComposeResourceEscapeInspection.findUnnecessaryEscapes("\\'\\\"\\@\\?")
    assertEquals(4, result.size)
    assertEquals(listOf('\'', '"', '@', '?'), result.map { it.character })
  }

  fun testParserSkipsValidEscapes() {
    val result = ComposeResourceEscapeInspection.findUnnecessaryEscapes("\\n\\t\\\\\\u0027")
    assertTrue("Valid escapes should not be detected", result.isEmpty())
  }

  fun testParserHandlesEscapedBackslashBeforeApostrophe() {
    // \\\' = escaped backslash + literal apostrophe (not an escape)
    val result = ComposeResourceEscapeInspection.findUnnecessaryEscapes("\\\\'")
    assertTrue("\\\\' should not flag the apostrophe", result.isEmpty())
  }

  fun testParserHandlesTextWithNoEscapes() {
    val result = ComposeResourceEscapeInspection.findUnnecessaryEscapes("Hello world")
    assertTrue(result.isEmpty())
  }

  fun testParserHandlesEmptyString() {
    val result = ComposeResourceEscapeInspection.findUnnecessaryEscapes("")
    assertTrue(result.isEmpty())
  }
}
