import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.gradle.plugin-publish")
    id("io.gitlab.arturbosch.detekt")
    `kotlin-dsl`
    `maven-publish`
}

dependencies {
    compileOnly(libs.android.gradle.api)
    detektPlugins(libs.bundles.detekt)
    implementation("org.jsoup:jsoup:1.16.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

gradlePlugin {
    website.set("https://github.com/azizutku/jacoco-aggregate-coverage-plugin")
    vcsUrl.set("https://github.com/azizutku/jacoco-aggregate-coverage-plugin.git")
    plugins {
        create("JacocoAggregateCoveragePlugin") {
            id = "com.azizutku.jacocoaggregatecoverageplugin"
            displayName = "Jacoco Aggregate Coverage Plugin"
            description = "The JaCoCo Aggregate Coverage Plugin simplifies the process of " +
                    "generating a unified code coverage report for multi-module Gradle projects. " +
                    "Leveraging the power of JaCoCo, it seamlessly aggregates coverage data " +
                    "across all subprojects, creating a comprehensive overview of your project's " +
                    "test coverage. This plugin is ideal for large-scale projects where insight " +
                    "into overall code quality is essential."
            implementationClass =
                "com.azizutku.jacocoaggregatecoverageplugin.JacocoAggregateCoveragePlugin"
            tags.set(
                listOf(
                    "jacoco", "coverage", "code-coverage", "report", "aggregation",
                    "unified-report", "multi-module", "test-coverage", "aggregated-test-coverage",
                    "unified-test-coverage", "android", "kotlin"
                )
            )
        }
    }
}

configure<DetektExtension> {
    source = project.files("src/main/kotlin")
    buildUponDefaultConfig = true
    allRules = false
    config = files("$rootDir/.detekt/config.yml")
    baseline = file("$rootDir/.detekt/baseline.xml")
}

group = "com.azizutku.jacocoaggregatecoverageplugin"
version = "0.1.0"
