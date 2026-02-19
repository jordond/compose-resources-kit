import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("java")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.intellij.platform)
  alias(libs.plugins.spotless)
}

group = "dev.jordond"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    intellijIdea("2025.2.4")
    testFramework(TestFrameworkType.Platform)

    bundledPlugin("com.intellij.gradle")
  }
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild = "241"
    }

    changeNotes =
      """
      Initial version
      """.trimIndent()
  }
}

tasks {
  withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

spotless {
  kotlin {
    ktlint(libs.versions.ktlint.get()).setEditorConfigPath("${project.rootDir}/.editorconfig")
    target("**/*.kt", "**/*.kts")
    targetExclude(
      "${layout.buildDirectory}/**/*.kt",
    )
    toggleOffOn()
    endWithNewline()
  }
}
