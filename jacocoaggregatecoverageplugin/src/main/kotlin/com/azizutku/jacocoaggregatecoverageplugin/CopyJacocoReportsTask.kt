package com.azizutku.jacocoaggregatecoverageplugin

import com.azizutku.jacocoaggregatecoverageplugin.extensions.JacocoAggregateCoveragePluginExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * A Gradle task to copy JaCoCo reports from all subprojects into a single directory.
 * It aggregates all the coverage data into one place for easier access and management.
 * This task is essential for creating a unified view of test coverage across multiple modules.
 */
@CacheableTask
internal abstract class CopyJacocoReportsTask : DefaultTask() {

    /**
     * The source folder containing plugin resources.
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pluginSourceFolder: Property<File>

    /**
     * The set of JaCoCo report files for Gradle's incremental build checks.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jacocoReportsFileCollection: ConfigurableFileCollection

    /**
     * The directory where the required resources will be stored.
     */
    @get:OutputDirectory
    abstract val outputDirectoryResources: DirectoryProperty

    /**
     * The directory for the generated aggregated coverage report.
     */
    @get:Internal
    abstract val aggregatedReportDir: DirectoryProperty

    /**
     * Performs the action of copying required resources and JaCoCo reports from all subprojects.
     * This method is invoked when the task executes. It checks for the existence of
     * JaCoCo reports in each subproject and copies them into a unified directory.
     */
    @TaskAction
    fun copyJacocoReports() {
        val pluginExtension =
            project.extensions.getByType(JacocoAggregateCoveragePluginExtension::class.java)
        val reportDirectory = pluginExtension.getReportDirectory()
        if (reportDirectory == null) {
            logger.error(
                "You need to specify jacocoTestReportTask property of " +
                    "jacocoAggregateCoverage extension block in your root build gradle"
            )
            return
        }
        copyRequiredResources(outputDirectoryResources.get().asFile)

        var foundAny = false
        project.subprojects.forEach { subproject ->
            val jacocoReportDir = subproject.layout.buildDirectory
                .dir(reportDirectory)
                .get()
                .asFile

            if (jacocoReportDir.exists()) {
                project.copy {
                    from(jacocoReportDir)
                    into(aggregatedReportDir.get().dir(subproject.path))
                }
                foundAny = true
            }
        }

        if (foundAny.not()) {
            logger.error(
                "There is no generated test report, you should " +
                    "run `${pluginExtension.jacocoTestReportTask.get()}` task first, " +
                    "then call `aggregateJacocoReports`. Or you can run " +
                    "`generateAndAggregateJacocoReports` task directly."
            )
        }
    }

    /**
     * Copies JaCoCo-related resources to the specified unified report directory.
     *
     * @param unifiedReportDir The target directory to copy resources into.
     */
    private fun copyRequiredResources(unifiedReportDir: File) {
        project.copy {
            from(project.file("${pluginSourceFolder.get().path}/jacoco-resources/"))
            into(unifiedReportDir)
        }
    }
}
