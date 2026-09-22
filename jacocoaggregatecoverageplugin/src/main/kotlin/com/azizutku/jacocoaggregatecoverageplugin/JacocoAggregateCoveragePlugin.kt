package com.azizutku.jacocoaggregatecoverageplugin

import com.azizutku.jacocoaggregatecoverageplugin.extensions.JacocoAggregateCoveragePluginExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.io.File
import java.nio.file.Path

private const val TASK_GROUP = "verification"
private const val TASK_AGGREGATE_JACOCO_REPORTS = "aggregateJacocoReports"
private const val EXTENSION_NAME_PLUGIN = "jacocoAggregateCoverage"
private const val PLUGIN_OUTPUT_PATH = "reports/jacocoAggregated"

internal class JacocoAggregateCoveragePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            EXTENSION_NAME_PLUGIN,
            JacocoAggregateCoveragePluginExtension::class.java,
        ).apply {
            aggregatedReportDirectory.convention(
                project.layout.buildDirectory.dir(PLUGIN_OUTPUT_PATH),
            )
        }

        project.tasks.register(
            TASK_AGGREGATE_JACOCO_REPORTS,
            AggregateJacocoReportsTask::class.java,
        ) { task ->
            task.group = TASK_GROUP
            task.description = "Aggregates JaCoCo HTML reports from all participating subprojects"
            task.outputDirectory.set(extension.aggregatedReportDirectory)

            val reportTaskName = extension.jacocoTestReportTask.orNull
                ?: throw GradleException(
                    "Set jacocoAggregateCoverage.jacocoTestReportTask before running " +
                        "'$TASK_AGGREGATE_JACOCO_REPORTS'.",
                )
            val configuredReportDirectory = extension.getReportDirectory()
            val reports = findReports(project, reportTaskName, configuredReportDirectory)

            if (reports.isEmpty()) {
                throw GradleException(
                    "No '$reportTaskName' tasks were found in the subprojects of ${project.path}.",
                )
            }

            reports.forEach { report ->
                task.moduleReportLocations.put(
                    report.modulePath,
                    reportLocationIdentity(project.rootDir.toPath(), report.directory.asFile.toPath()),
                )
                task.moduleReportDirectories.put(report.modulePath, report.directory)
                task.jacocoReports.from(report.directory)
            }
            task.dependsOn(reports.map(ModuleReport::task))
        }
    }

    private fun findReports(
        project: Project,
        reportTaskName: String,
        configuredReportDirectory: String?,
    ): List<ModuleReport> = project.subprojects.mapNotNull { subproject ->
        val reportTask = subproject.tasks.findByName(reportTaskName) ?: return@mapNotNull null
        val directory = if (reportTask is JacocoReport &&
            extensionDoesNotOverrideReportDirectory(project)
        ) {
            reportTask.reports.html.outputLocation.get()
        } else {
            val path = configuredReportDirectory
                ?: throw GradleException("Unable to determine the JaCoCo HTML report directory.")
            subproject.layout.buildDirectory.dir(path).get()
        }
        ModuleReport(subproject.path, reportTask, directory)
    }

    private fun reportLocationIdentity(rootDirectory: Path, reportDirectory: Path): String =
        runCatching { rootDirectory.relativize(reportDirectory).toString() }
            .getOrElse { reportDirectory.toAbsolutePath().normalize().toString() }
            .replace(File.separatorChar, '/')

    private fun extensionDoesNotOverrideReportDirectory(project: Project): Boolean {
        val extension = project.extensions.getByType(
            JacocoAggregateCoveragePluginExtension::class.java,
        )
        return !extension.configuredCustomReportsDirectory.isPresent &&
            !extension.configuredCustomHtmlOutputLocation.isPresent
    }

    private data class ModuleReport(val modulePath: String, val task: Task, val directory: Directory)
}
