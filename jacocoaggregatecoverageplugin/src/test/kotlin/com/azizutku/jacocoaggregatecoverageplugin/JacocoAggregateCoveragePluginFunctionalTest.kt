package com.azizutku.jacocoaggregatecoverageplugin

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class JacocoAggregateCoveragePluginFunctionalTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `aggregates JaCoCo 0_8_15 reports and reuses configuration cache`() {
        writeFixture()

        val firstRun = runner().build()
        val secondRun = runner().build()

        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":aggregateJacocoReports")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":aggregateJacocoReports")?.outcome)
        assertTrue(secondRun.output.contains("Reusing configuration cache."), secondRun.output)

        val reportDirectory = projectDirectory.resolve("build/reports/jacocoAggregated")
        assertTrue(reportDirectory.toFile().deleteRecursively())
        val cacheRestoreRun = runner().build()
        assertEquals(TaskOutcome.FROM_CACHE, cacheRestoreRun.task(":aggregateJacocoReports")?.outcome)

        val aggregateIndex = Files.readString(reportDirectory.resolve("index.html"))
        assertTrue(aggregateIndex.contains("href=\"alpha/index.html\""), aggregateIndex)
        assertTrue(aggregateIndex.contains("href=\"feature/beta/index.html\""), aggregateIndex)
        assertTrue(Files.isRegularFile(reportDirectory.resolve("jacoco-resources/report.css")))

        val nestedIndex = Files.readString(reportDirectory.resolve("feature/beta/index.html"))
        assertTrue(nestedIndex.contains("href=\"../../index.html\""), nestedIndex)
        assertTrue(nestedIndex.contains(":feature:beta"), nestedIndex)
    }

    @Test
    fun `remains configuration cache compatible with Gradle 8_4`() {
        writeFixture()

        runner("8.4").build()
        val secondRun = runner("8.4").build()

        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":aggregateJacocoReports")?.outcome)
        assertTrue(secondRun.output.contains("Reusing configuration cache."), secondRun.output)
    }

    private fun runner(gradleVersion: String? = null): GradleRunner {
        val runner = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withPluginClasspath()
            .withArguments(
                "aggregateJacocoReports",
                "--configuration-cache",
                "--configuration-cache-problems=fail",
                "--build-cache",
                "--stacktrace",
            )
        return if (gradleVersion == null) runner else runner.withGradleVersion(gradleVersion)
    }

    private fun writeFixture() {
        write(
            "settings.gradle",
            """
                rootProject.name = 'functional-test'
                include 'alpha', 'feature:beta'

                buildCache {
                    local { directory = new File(rootDir, 'local-build-cache') }
                }
            """.trimIndent(),
        )
        write(
            "build.gradle",
            """
                plugins {
                    id 'com.azizutku.jacocoaggregatecoverageplugin'
                }

                subprojects {
                    apply plugin: 'java'
                    apply plugin: 'jacoco'

                    repositories { mavenCentral() }
                    dependencies { testImplementation 'junit:junit:4.13.2' }

                    jacoco { toolVersion = '0.8.15' }
                    test { useJUnit() }
                    jacocoTestReport {
                        dependsOn test
                        reports {
                            html.required = true
                            xml.required = true
                            html.outputLocation.set(
                                layout.buildDirectory.dir('reports/custom-jacoco/html')
                            )
                        }
                    }
                }

                jacocoAggregateCoverage {
                    jacocoTestReportTask.set('jacocoTestReport')
                }
            """.trimIndent(),
        )
        writeJavaModule("alpha", "Alpha", "sum", "return left + right;")
        writeJavaModule("feature/beta", "Beta", "multiply", "return left * right;")
    }

    private fun writeJavaModule(
        moduleDirectory: String,
        className: String,
        methodName: String,
        methodBody: String,
    ) {
        val packageName = className.lowercase()
        write(
            "$moduleDirectory/src/main/java/example/$packageName/$className.java",
            """
                package example.$packageName;

                public final class $className {
                    public int $methodName(int left, int right) {
                        $methodBody
                    }
                }
            """.trimIndent(),
        )
        write(
            "$moduleDirectory/src/test/java/example/$packageName/${className}Test.java",
            """
                package example.$packageName;

                import org.junit.Test;
                import static org.junit.Assert.assertEquals;

                public final class ${className}Test {
                    @Test public void coversMethod() {
                        assertEquals(6, new $className().$methodName(2, ${if (methodName == "sum") 4 else 3}));
                    }
                }
            """.trimIndent(),
        )
    }

    private fun write(relativePath: String, contents: String) {
        val file = projectDirectory.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, contents)
    }
}
