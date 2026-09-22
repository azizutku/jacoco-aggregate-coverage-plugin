package com.azizutku.jacocoaggregatecoverageplugin

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class JacocoAggregateCoveragePluginFunctionalTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `aggregates JaCoCo 0_8_15 reports with isolated projects`() {
        writeFixture()

        val firstRun = runner(isolatedProjects = true).build()
        val secondRun = runner(isolatedProjects = true).build()

        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":aggregateJacocoReports")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":aggregateJacocoReports")?.outcome)
        assertTrue(secondRun.output.contains("Reusing configuration cache."), secondRun.output)

        val reportDirectory = projectDirectory.resolve("build/reports/jacocoAggregated")
        assertTrue(reportDirectory.toFile().deleteRecursively())
        val cacheRestoreRun = runner(isolatedProjects = true).build()
        assertEquals(TaskOutcome.FROM_CACHE, cacheRestoreRun.task(":aggregateJacocoReports")?.outcome)

        val aggregateIndex = Files.readString(reportDirectory.resolve("index.html"))
        assertTrue(aggregateIndex.contains("href=\"alpha/index.html\""), aggregateIndex)
        assertTrue(aggregateIndex.contains("href=\"feature/beta/index.html\""), aggregateIndex)
        assertTrue(Files.isRegularFile(reportDirectory.resolve("jacoco-resources/report.css")))

        val nestedIndex = Files.readString(reportDirectory.resolve("feature/beta/index.html"))
        assertTrue(nestedIndex.contains("href=\"../../index.html\""), nestedIndex)
        assertTrue(nestedIndex.contains(":feature:beta"), nestedIndex)

        val additionalTest = "alpha/src/test/java/example/alpha/AlphaNegativeTest.java"
        write(
            additionalTest,
            """
                package example.alpha;

                import org.junit.Test;
                import static org.junit.Assert.assertEquals;

                public final class AlphaNegativeTest {
                    @Test public void coversNegativeBranch() {
                        assertEquals(4, new Alpha().sum(-1, 4));
                    }
                }
            """.trimIndent(),
        )
        val testAddedRun = runner(isolatedProjects = true).build()
        assertEquals(TaskOutcome.SUCCESS, testAddedRun.task(":aggregateJacocoReports")?.outcome)
        assertTrue(testAddedRun.output.contains("Reusing configuration cache."), testAddedRun.output)
        val reportWithAdditionalTest = Files.readString(reportDirectory.resolve("index.html"))
        assertNotEquals(aggregateIndex, reportWithAdditionalTest)

        Files.delete(projectDirectory.resolve(additionalTest))
        val testRemovedRun = runner(isolatedProjects = true).build()
        assertEquals(TaskOutcome.FROM_CACHE, testRemovedRun.task(":aggregateJacocoReports")?.outcome)
        assertTrue(testRemovedRun.output.contains("Reusing configuration cache."), testRemovedRun.output)
        assertEquals(aggregateIndex, Files.readString(reportDirectory.resolve("index.html")))
    }

    @Test
    fun `remains configuration cache compatible with Gradle 8_4`() {
        writeFixture(rootReportTaskName = "jacocoTestReport")

        runner("8.4").build()
        val secondRun = runner("8.4").build()

        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":aggregateJacocoReports")?.outcome)
        assertTrue(secondRun.output.contains("Reusing configuration cache."), secondRun.output)
    }

    @Test
    fun `selects different report tasks in participating projects`() {
        writeFixture(
            customReportTaskNames = mapOf(
                "alpha" to "alphaCoverageReport",
                "feature/beta" to "betaCoverageReport",
            ),
        )

        val result = runner(isolatedProjects = true).build()
        val secondRun = runner(isolatedProjects = true).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregateJacocoReports")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":aggregateJacocoReports")?.outcome)
        assertTrue(secondRun.output.contains("Reusing configuration cache."), secondRun.output)
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":alpha:prepareAlphaCoverageReportForJacocoAggregation")?.outcome,
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":feature:beta:prepareBetaCoverageReportForJacocoAggregation")?.outcome,
        )
        assertTrue(
            Files.isRegularFile(
                projectDirectory.resolve("build/reports/jacocoAggregated/alpha/index.html"),
            ),
        )
        assertTrue(
            Files.isRegularFile(
                projectDirectory.resolve("build/reports/jacocoAggregated/feature/beta/index.html"),
            ),
        )
    }

    @Test
    fun `fails when a participating project selects an unknown report task`() {
        writeFixture()
        append(
            "alpha/build.gradle",
            """

                jacocoAggregateReport {
                    reportTaskName.set('missingCoverageReport')
                }
            """.trimIndent(),
        )

        val result = runner(isolatedProjects = true).buildAndFail()

        assertTrue(
            result.output.contains(
                "No JaCoCo HTML report was published for their selected JaCoCo reports " +
                    "by participating project(s): :alpha.",
            ),
            result.output,
        )
    }

    @Test
    fun `fails when one participating project disables its HTML report`() {
        writeFixture()
        append(
            "alpha/build.gradle",
            """

                jacocoTestReport {
                    reports.html.required = false
                }
            """.trimIndent(),
        )

        val result = runner(isolatedProjects = true).buildAndFail()

        assertTrue(
            result.output.contains(
                "The selected JaCoCo report for :alpha has HTML output disabled.",
            ),
            result.output,
        )
    }

    @Test
    fun `fails when a selected task does not produce an HTML index`() {
        writeFixture()
        write("alpha/empty-report/readme.txt", "No coverage report")
        append(
            "alpha/build.gradle",
            customCopyReportConfiguration(
                taskName = "emptyCoverageReport",
                sourceDirectory = "empty-report",
                outputDirectory = "reports/empty-coverage/html",
            ),
        )

        val result = runner(isolatedProjects = true).buildAndFail()

        assertTrue(
            result.output.contains("The selected JaCoCo report for :alpha did not generate"),
            result.output,
        )
        assertTrue(result.output.contains("reports/empty-coverage/html/index.html"), result.output)
    }

    @Test
    fun `fails with a clear error for an unsupported JaCoCo summary`() {
        writeFixture()
        write(
            "alpha/malformed-report/index.html",
            "<html><body><table><tfoot><tr><td>Total</td></tr></tfoot></table></body></html>",
        )
        listOf("greenbar.gif", "redbar.gif", "report.css", "report.gif", "sort.js").forEach { resource ->
            write("alpha/malformed-report/jacoco-resources/$resource", "")
        }
        append(
            "alpha/build.gradle",
            customCopyReportConfiguration(
                taskName = "malformedCoverageReport",
                sourceDirectory = "malformed-report",
                outputDirectory = "reports/malformed-coverage/html",
            ),
        )

        val result = runner(isolatedProjects = true).buildAndFail()

        assertTrue(result.output.contains("has an unsupported summary"), result.output)
        assertTrue(result.output.contains("expected at least 13 cells but found 1"), result.output)
    }

    @Test
    fun `fails when a selected HTML report is missing JaCoCo resources`() {
        writeFixture()
        write(
            "alpha/report-without-resources/index.html",
            """
                <html><body><table><tfoot><tr>
                  <td>Total</td><td>1 of 2</td><td>50%</td>
                  <td>0 of 0</td><td>n/a</td>
                  <td>0</td><td>1</td><td>1</td><td>2</td>
                  <td>0</td><td>1</td><td>0</td><td>1</td>
                </tr></tfoot></table></body></html>
            """.trimIndent(),
        )
        append(
            "alpha/build.gradle",
            customCopyReportConfiguration(
                taskName = "resourceLessCoverageReport",
                sourceDirectory = "report-without-resources",
                outputDirectory = "reports/resource-less-coverage/html",
            ),
        )

        val result = runner(isolatedProjects = true).buildAndFail()

        assertTrue(result.output.contains("report for :alpha is missing required resource(s)"), result.output)
        assertTrue(result.output.contains("report.css"), result.output)
    }

    @Test
    fun `supports Kotlin DSL and unicode project paths`() {
        writeKotlinDslFixture()

        val result = runner(isolatedProjects = true).build()
        val secondRun = runner(isolatedProjects = true).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregateJacocoReports")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":aggregateJacocoReports")?.outcome)
        assertTrue(secondRun.output.contains("Reusing configuration cache."), secondRun.output)
        val report = projectDirectory.resolve(
            "build/reports/jacocoAggregated/%C3%B6zellik/%C3%B6deme/index.html",
        )
        assertTrue(Files.isRegularFile(report), report.toString())
        val index = Files.readString(projectDirectory.resolve("build/reports/jacocoAggregated/index.html"))
        assertTrue(index.contains("%C3%B6zellik/%C3%B6deme/index.html"), index)
    }

    @Test
    @Tag("android-integration")
    fun `aggregates Android and JVM reports with isolated projects`() {
        val sdkDirectory = checkNotNull(findAndroidSdkDirectory()) {
            "Android SDK is required to run androidIntegrationTest."
        }
        writeAndroidAndJvmFixture(sdkDirectory)

        val result = runner(isolatedProjects = true).build()
        val secondRun = runner(isolatedProjects = true).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregateJacocoReports")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":aggregateJacocoReports")?.outcome)
        assertTrue(secondRun.output.contains("Reusing configuration cache."), secondRun.output)
        val reportDirectory = projectDirectory.resolve("build/reports/jacocoAggregated")
        assertTrue(Files.isRegularFile(reportDirectory.resolve("androidFeature/index.html")))
        assertTrue(Files.isRegularFile(reportDirectory.resolve("jvmLibrary/index.html")))
    }

    private fun runner(
        gradleVersion: String? = null,
        isolatedProjects: Boolean = false,
    ): GradleRunner {
        val arguments = mutableListOf(
            "aggregateJacocoReports",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
            "--build-cache",
            "--stacktrace",
        )
        if (isolatedProjects) {
            arguments += "--isolated-projects"
        }
        val runner = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withPluginClasspath()
            .withArguments(arguments)
        return if (gradleVersion == null) runner else runner.withGradleVersion(gradleVersion)
    }

    private fun writeFixture(
        rootReportTaskName: String? = null,
        customReportTaskNames: Map<String, String> = emptyMap(),
    ) {
        write(
            "settings.gradle",
            """
                rootProject.name = 'functional-test'
                include 'alpha', 'feature:beta', 'documentation'

                buildCache {
                    local { directory = new File(rootDir, 'local-build-cache') }
                }
            """.trimIndent(),
        )
        write("documentation/build.gradle", "")
        write(
            "build.gradle",
            """
                plugins {
                    id 'com.azizutku.jacocoaggregatecoverageplugin'
                }

                ${rootReportConfiguration(rootReportTaskName)}
            """.trimIndent(),
        )
        writeJavaModule(
            "alpha",
            "Alpha",
            "sum",
            "if (left < 0) return right; return left + right;",
            customReportTaskName = customReportTaskNames["alpha"],
        )
        writeJavaModule(
            "feature/beta",
            "Beta",
            "multiply",
            "return left * right;",
            customReportTaskName = customReportTaskNames["feature/beta"],
        )
    }

    private fun writeJavaModule(
        moduleDirectory: String,
        className: String,
        methodName: String,
        methodBody: String,
        customReportTaskName: String?,
    ) {
        val packageName = className.lowercase()
        write(
            "$moduleDirectory/build.gradle",
            """
                plugins {
                    id 'java'
                    id 'jacoco'
                    id 'com.azizutku.jacocoaggregatecoverageplugin'
                }

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

                ${customReportConfiguration(customReportTaskName)}
            """.trimIndent(),
        )
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

    private fun rootReportConfiguration(taskName: String?): String = taskName?.let {
        """
            jacocoAggregateCoverage {
                jacocoTestReportTask.set('$it')
            }
        """.trimIndent()
    }.orEmpty()

    private fun customReportConfiguration(taskName: String?): String = if (taskName != null) {
        """
            tasks.register('$taskName', Copy) {
                dependsOn jacocoTestReport
                from layout.buildDirectory.dir('reports/custom-jacoco/html')
                into layout.buildDirectory.dir('reports/published-custom/html')
            }

            jacocoAggregateReport {
                reportTaskName.set('$taskName')
                register('$taskName') {
                    htmlOutputLocation.set(
                        layout.buildDirectory.dir('reports/published-custom/html')
                    )
                }
            }
        """.trimIndent()
    } else {
        ""
    }

    private fun customCopyReportConfiguration(
        taskName: String,
        sourceDirectory: String,
        outputDirectory: String,
    ): String =
        """

            tasks.register('$taskName', Copy) {
                from '$sourceDirectory'
                into layout.buildDirectory.dir('$outputDirectory')
            }

            jacocoAggregateReport {
                reportTaskName.set('$taskName')
                register('$taskName') {
                    htmlOutputLocation.set(layout.buildDirectory.dir('$outputDirectory'))
                }
            }
        """.trimIndent()

    private fun writeKotlinDslFixture() {
        write(
            "settings.gradle.kts",
            """
                rootProject.name = "kotlin-dsl-functional-test"
                include(":özellik:ödeme")

                buildCache {
                    local { directory = File(rootDir, "local-build-cache") }
                }
            """.trimIndent(),
        )
        write(
            "build.gradle.kts",
            """
                plugins {
                    id("com.azizutku.jacocoaggregatecoverageplugin")
                }
            """.trimIndent(),
        )
        write(
            "özellik/ödeme/build.gradle.kts",
            """
                plugins {
                    java
                    jacoco
                    id("com.azizutku.jacocoaggregatecoverageplugin")
                }

                repositories { mavenCentral() }

                dependencies {
                    testImplementation("junit:junit:4.13.2")
                }

                jacoco { toolVersion = "0.8.15" }

                tasks.test { useJUnit() }

                tasks.jacocoTestReport {
                    dependsOn(tasks.test)
                    reports.html.required = true
                }

                jacocoAggregateReport {
                    reportTaskName.set("jacocoTestReport")
                }
            """.trimIndent(),
        )
        write(
            "özellik/ödeme/src/main/java/example/payment/Payment.java",
            """
                package example.payment;

                public final class Payment {
                    public int total(int left, int right) {
                        return left + right;
                    }
                }
            """.trimIndent(),
        )
        write(
            "özellik/ödeme/src/test/java/example/payment/PaymentTest.java",
            """
                package example.payment;

                import org.junit.Test;
                import static org.junit.Assert.assertEquals;

                public final class PaymentTest {
                    @Test public void coversTotal() {
                        assertEquals(6, new Payment().total(2, 4));
                    }
                }
            """.trimIndent(),
        )
    }

    private fun writeAndroidAndJvmFixture(sdkDirectory: Path) {
        write(
            "settings.gradle.kts",
            """
                pluginManagement {
                    repositories {
                        google()
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }

                dependencyResolutionManagement {
                    repositories {
                        google()
                        mavenCentral()
                    }
                }

                rootProject.name = "android-jvm-functional-test"
                include(":androidFeature", ":jvmLibrary")
            """.trimIndent(),
        )
        write("local.properties", "sdk.dir=${escapePropertyValue(sdkDirectory.toString())}")
        write(
            "build.gradle.kts",
            """
                plugins {
                    id("com.azizutku.jacocoaggregatecoverageplugin")
                    id("com.android.library") version "9.4.0" apply false
                }
            """.trimIndent(),
        )
        write(
            "androidFeature/build.gradle.kts",
            """
                plugins {
                    id("com.android.library") version "9.4.0"
                    id("com.azizutku.jacocoaggregatecoverageplugin")
                }

                android {
                    namespace = "example.androidfeature"
                    compileSdk = 35
                    buildToolsVersion = "36.0.0"

                    defaultConfig {
                        minSdk = 23
                    }

                    buildTypes {
                        debug {
                            enableUnitTestCoverage = true
                        }
                    }

                    testCoverage {
                        jacocoVersion = "0.8.15"
                    }
                }

                dependencies {
                    testImplementation("junit:junit:4.13.2")
                }

                jacocoAggregateReport {
                    reportTaskName.set("createDebugUnitTestCoverageReport")
                    register("createDebugUnitTestCoverageReport") {
                        htmlOutputLocation.set(
                            layout.buildDirectory.dir("reports/coverage/test/debug")
                        )
                    }
                }
            """.trimIndent(),
        )
        write(
            "androidFeature/src/main/AndroidManifest.xml",
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" />",
        )
        write(
            "androidFeature/src/main/java/example/androidfeature/Feature.java",
            """
                package example.androidfeature;

                public final class Feature {
                    public int sum(int left, int right) {
                        return left + right;
                    }
                }
            """.trimIndent(),
        )
        write(
            "androidFeature/src/test/java/example/androidfeature/FeatureTest.java",
            """
                package example.androidfeature;

                import org.junit.Test;
                import static org.junit.Assert.assertEquals;

                public final class FeatureTest {
                    @Test public void coversSum() {
                        assertEquals(6, new Feature().sum(2, 4));
                    }
                }
            """.trimIndent(),
        )
        writeJavaModule(
            moduleDirectory = "jvmLibrary",
            className = "Library",
            methodName = "multiply",
            methodBody = "return left * right;",
            customReportTaskName = null,
        )
    }

    private fun findAndroidSdkDirectory(): Path? = sequenceOf(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        "${System.getProperty("user.home")}/Library/Android/sdk",
    ).filterNotNull()
        .map(Path::of)
        .firstOrNull(Files::isDirectory)

    private fun escapePropertyValue(value: String): String = value
        .replace("\\", "\\\\")
        .replace(":", "\\:")

    private fun write(relativePath: String, contents: String) {
        val file = projectDirectory.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, contents)
    }

    private fun append(relativePath: String, contents: String) {
        Files.writeString(
            projectDirectory.resolve(relativePath),
            contents,
            StandardOpenOption.APPEND,
        )
    }
}
