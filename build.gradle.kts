import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "2.3.10"
  id("org.jetbrains.intellij.platform") version "2.11.0"
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
