plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.7.0"
}

group = "co.elastic.elasticsearch.listener.visualizer"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2025.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // plugin dependencies
        bundledPlugin("com.intellij.java")
        plugin("EclipseCodeFormatter:23.5.241.000.0-Eclipse_2024-09")
        plugin("CheckStyle-IDEA:5.109.2")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}
