import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.intellij.platform)
  alias(libs.plugins.spotless)
}

group = "dev.jordond"
version = "1.0.0"

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    intellijIdea("2025.3.2")
    testFramework(TestFrameworkType.Platform)

    bundledPlugin("com.intellij.gradle")
    bundledPlugin("org.jetbrains.kotlin")
  }
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild = "243"
    }

    changeNotes =
      """
      Initial version
      """.trimIndent()
  }

  publishing {
    token = System.getenv("PUBLISH_TOKEN")
  }

  signing {
    certificateChain = System.getenv("SIGNING_CERTIFICATE_CHAIN")
    privateKey = System.getenv("SIGNING_PRIVATE_KEY")
    password = System.getenv("SIGNING_PASSWORD")
  }

  pluginVerification {
    ides {
      recommended()
    }
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
    targetExclude("${layout.buildDirectory}/**/*.kt")
    toggleOffOn()
    endWithNewline()
  }

  flexmark {
    target("**/.*.md")
    targetExclude("${layout.buildDirectory}/**/*.md")
    trimTrailingWhitespace()
    endWithNewline()
  }
}
