import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("com.gradle.plugin-publish")
    id("dev.detekt")
    `maven-publish`
}

dependencies {
    detektPlugins(libs.bundles.detekt)
    implementation(libs.jsoup)

    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("android-integration")
    }
}

tasks.register<Test>("androidIntegrationTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the Android and JVM consumer integration test"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("android-integration")
    }
    shouldRunAfter(tasks.test)
}

gradlePlugin {
    website.set("https://github.com/azizutku/jacoco-aggregate-coverage-plugin")
    vcsUrl.set("https://github.com/azizutku/jacoco-aggregate-coverage-plugin.git")
    plugins {
        create("JacocoAggregateCoveragePlugin") {
            id = "com.azizutku.jacocoaggregatecoverageplugin"
            displayName = "JaCoCo Aggregate Coverage Plugin"
            description = "Creates an aggregate HTML coverage dashboard from existing JaCoCo " +
                "reports in multi-module Android and JVM builds."
            implementationClass =
                "com.azizutku.jacocoaggregatecoverageplugin.JacocoAggregateCoveragePlugin"
            tags.set(
                listOf(
                    "jacoco", "coverage", "code-coverage", "report", "aggregation",
                    "dashboard", "multi-module", "test-coverage", "aggregated-test-coverage",
                    "android", "jvm", "kotlin"
                )
            )
            compatibility {
                features {
                    configurationCache = true
                    isolatedProjects = true
                }
            }
        }
    }
}

configure<DetektExtension> {
    source.setFrom("src/main/kotlin")
    buildUponDefaultConfig = true
    allRules = false
}

group = "com.azizutku.jacocoaggregatecoverageplugin"
version = providers.gradleProperty("pluginVersion").getOrElse("0.2.0")
