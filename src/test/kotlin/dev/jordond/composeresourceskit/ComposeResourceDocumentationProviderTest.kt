package dev.jordond.composeresourceskit

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ComposeResourceDocumentationProviderTest : BasePlatformTestCase() {
  private val provider = ComposeResourceDocumentationProvider()

  private fun addComposeResource(
    path: String,
    content: String,
  ) {
    myFixture.addFileToProject("composeResources/$path", content)
  }

  private fun configureKotlin(code: String) {
    myFixture.configureByText("Test.kt", code)
  }

  private fun elementAtCaret() = myFixture.file.findElementAt(myFixture.caretOffset)

  // -- Quick Navigate Info --

  fun testQuickNavigateInfoForString() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="app_name">My App</string>
      </resources>
      """.trimIndent(),
    )
    configureKotlin("val x = Res.string.<caret>app_name")
    val element = elementAtCaret()!!

    val info = provider.getQuickNavigateInfo(element, element)

    assertNotNull(info)
    assertTrue(info!!.contains("My App"))
    assertTrue(info.contains("strings.xml"))
  }

  fun testQuickNavigateInfoForStringArray() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string-array name="colors">
              <item>Red</item>
              <item>Blue</item>
          </string-array>
      </resources>
      """.trimIndent(),
    )
    configureKotlin("val x = Res.array.<caret>colors")
    val element = elementAtCaret()!!

    val info = provider.getQuickNavigateInfo(element, element)

    assertNotNull(info)
    assertTrue(info!!.contains("Red"))
    assertTrue(info.contains("Blue"))
  }

  fun testQuickNavigateInfoForPlurals() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <plurals name="item_count">
              <item quantity="one">%1${'$'}d item</item>
              <item quantity="other">%1${'$'}d items</item>
          </plurals>
      </resources>
      """.trimIndent(),
    )
    configureKotlin("val x = Res.plurals.<caret>item_count")
    val element = elementAtCaret()!!

    val info = provider.getQuickNavigateInfo(element, element)

    assertNotNull(info)
    assertTrue(info!!.contains("one"))
    assertTrue(info.contains("other"))
  }

  // -- Generate Doc --

  fun testGenerateDocForString() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="greeting">Hello World</string>
      </resources>
      """.trimIndent(),
    )
    configureKotlin("val x = Res.string.<caret>greeting")
    val element = elementAtCaret()!!

    val doc = provider.generateDoc(element, element)

    assertNotNull(doc)
    assertTrue(doc!!.contains("String Resource"))
    assertTrue(doc.contains("greeting"))
    assertTrue(doc.contains("Hello World"))
  }

  fun testGenerateDocShowsAllLocales() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="app_name">My App</string>
      </resources>
      """.trimIndent(),
    )
    addComposeResource(
      "values-fr/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="app_name">Mon App</string>
      </resources>
      """.trimIndent(),
    )
    configureKotlin("val x = Res.string.<caret>app_name")
    val element = elementAtCaret()!!

    val doc = provider.generateDoc(element, element)

    assertNotNull(doc)
    assertTrue(doc!!.contains("Locales"))
    assertTrue(doc.contains("default"))
    assertTrue(doc.contains("fr"))
    assertTrue(doc.contains("My App"))
    assertTrue(doc.contains("Mon App"))
  }

  fun testGenerateDocForStringArray() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string-array name="days">
              <item>Monday</item>
              <item>Tuesday</item>
          </string-array>
      </resources>
      """.trimIndent(),
    )
    configureKotlin("val x = Res.array.<caret>days")
    val element = elementAtCaret()!!

    val doc = provider.generateDoc(element, element)

    assertNotNull(doc)
    assertTrue(doc!!.contains("String Array"))
    assertTrue(doc.contains("Monday"))
    assertTrue(doc.contains("Tuesday"))
  }

  fun testGenerateDocForPlurals() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <plurals name="messages">
              <item quantity="one">%1${'$'}d message</item>
              <item quantity="other">%1${'$'}d messages</item>
          </plurals>
      </resources>
      """.trimIndent(),
    )
    configureKotlin("val x = Res.plurals.<caret>messages")
    val element = elementAtCaret()!!

    val doc = provider.generateDoc(element, element)

    assertNotNull(doc)
    assertTrue(doc!!.contains("Plurals"))
    assertTrue(doc.contains("one"))
    assertTrue(doc.contains("other"))
  }

  // -- Negative cases --

  fun testReturnsNullForNonResource() {
    configureKotlin("val x = foo.bar.<caret>baz")
    val element = elementAtCaret()!!

    assertNull(provider.generateDoc(element, element))
    assertNull(provider.getQuickNavigateInfo(element, element))
  }

  fun testReturnsNullForFileResource() {
    addComposeResource("drawable/icon.png", "fake")
    configureKotlin("val x = Res.drawable.<caret>icon")
    val element = elementAtCaret()!!

    // Documentation is only for XML-based resources (strings, arrays, plurals)
    assertNull(provider.generateDoc(element, element))
  }

  fun testReturnsNullForMissingResource() {
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="greeting">Hello</string>
      </resources>
      """.trimIndent(),
    )
    configureKotlin("val x = Res.string.<caret>nonexistent")
    val element = elementAtCaret()!!

    assertNull(provider.generateDoc(element, element))
  }

  fun testTruncatesLongValues() {
    val longValue = "A".repeat(200)
    addComposeResource(
      "values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="long_string">$longValue</string>
      </resources>
      """.trimIndent(),
    )
    configureKotlin("val x = Res.string.<caret>long_string")
    val element = elementAtCaret()!!

    val info = provider.getQuickNavigateInfo(element, element)
    assertNotNull(info)
    assertTrue("Info should be truncated with ellipsis", info!!.contains("..."))
    assertTrue("Info should be shorter than the original value", info.length < 200)
  }

  fun testIgnoresResourcesInBuildFolder() {
    // Add a resource in a "build" directory which should be ignored by the robust path check
    myFixture.addFileToProject(
      "build/composeResources/values/strings.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="build_resource">In Build</string>
      </resources>
      """.trimIndent(),
    )
    configureKotlin("val x = Res.string.<caret>build_resource")
    val element = elementAtCaret()!!

    assertNull("Should not resolve resources inside 'build' folder", provider.getQuickNavigateInfo(element, element))
  }
}
