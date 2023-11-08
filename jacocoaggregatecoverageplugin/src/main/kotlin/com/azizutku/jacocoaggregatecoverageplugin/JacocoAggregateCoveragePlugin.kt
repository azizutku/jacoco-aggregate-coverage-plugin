package com.azizutku.jacocoaggregatecoverageplugin

import com.azizutku.jacocoaggregatecoverageplugin.extensions.JacocoAggregateCoveragePluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.tasks.Copy

// Tasks
private const val TASK_GROUP = "jacoco aggregate coverage plugin"
private const val TASK_COPY_JACOCO_REPORTS = "copyJacocoReports"
private const val TASK_AGGREGATE_JACOCO_REPORTS = "aggregateJacocoReports"
private const val TASK_UNZIP_PLUGIN = "unzipJacocoAggregateCoveragePlugin"

private const val EXTENSION_NAME_PLUGIN = "jacocoAggregateCoverage"

private const val PLUGIN_OUTPUT_PATH = "reports/jacocoAggregated"

/**
 * A Gradle plugin to aggregate JaCoCo coverage reports from multiple subprojects.
 * This plugin facilitates the collection and unification of code coverage metrics
 * across different modules of a multi-module project.
 */
internal class JacocoAggregateCoveragePlugin : Plugin<Project> {

    /**
     * Applies the plugin to the given project.
     * This method sets up the plugin extension and registers tasks required
     * for aggregating JaCoCo reports.
     *
     * @param project The Gradle project to which the plugin is applied.
     */
    override fun apply(project: Project) {
        val pluginExtension = createPluginExtension(project)

        val unzipTaskProvider = project.tasks.register(TASK_UNZIP_PLUGIN, Copy::class.java) {
            configureUnzipTask(this, project)
        }

        val copyReportsTaskProvider = project.tasks.register(
            TASK_COPY_JACOCO_REPORTS,
            CopyJacocoReportsTask::class.java,
        ) {
            group = TASK_GROUP
            description = "Copy required resources and JaCoCo reports from all subprojects " +
                "into a single directory"
            pluginSourceFolder.set(unzipTaskProvider.map { task -> task.destinationDir })
            jacocoReportsFileCollection.setFrom(getJacocoGeneratedDirs(project, pluginExtension))
            outputDirectoryResources.set(
                project.layout.buildDirectory.dir("$PLUGIN_OUTPUT_PATH/jacoco-resources")
            )
            aggregatedReportDir.set(
                project.layout.buildDirectory.dir(PLUGIN_OUTPUT_PATH)
            )
        }

        project.tasks.register(
            TASK_AGGREGATE_JACOCO_REPORTS,
            AggregateJacocoReportsTask::class.java,
        ).configure {
            group = TASK_GROUP
            description = "Generate a aggregated report for JaCoCo coverage reports"
            if (isJacocoTestReportTaskValueSet(pluginExtension, project).not()) {
                return@configure
            }
            pluginSourceFolder.set(unzipTaskProvider.map { task -> task.destinationDir })
            jacocoReportsFileCollection.setFrom(getJacocoGeneratedDirs(project, pluginExtension))
            outputDirectory.set(
                project.layout.buildDirectory.dir(PLUGIN_OUTPUT_PATH)
            )
            val jacocoTestReportTasks = getJacocoTestReportTasks(project, pluginExtension)
            if (jacocoTestReportTasks.isEmpty()) {
                val jacocoTestReportTask = pluginExtension.jacocoTestReportTask.get()
                project.logger.error(
                    """
                        There are no tasks named '$jacocoTestReportTask' in your project. Please 
                        ensure that you set the `jacocoTestReportTask` property in the plugin 
                        extension to the name of the task you use to generate JaCoCo test reports.
                    """.trimIndent()
                )
                return@configure
            }
            copyReportsTaskProvider.get().mustRunAfter(jacocoTestReportTasks)
            dependsOn(copyReportsTaskProvider)
            dependsOn(jacocoTestReportTasks)
        }
    }

    /**
     * Checks if the 'jacocoTestReportTask' property is set in the plugin extension.
     *
     * @param pluginExtension The plugin extension with configuration.
     * @param project The Gradle project to log errors to.
     * @return True if the property is set, false otherwise.
     */
    private fun isJacocoTestReportTaskValueSet(
        pluginExtension: JacocoAggregateCoveragePluginExtension,
        project: Project
    ): Boolean {
        val jacocoTestReportTask = pluginExtension.jacocoTestReportTask.orNull
        if (jacocoTestReportTask == null) {
            project.logger.error(
                """
                    The 'jacocoTestReportTask' property has not been specified in the 
                    JacocoAggregateCoveragePluginExtension extension. Please ensure this property 
                    is set in the build gradle file of the root project.
                """.trimIndent()
            )
            return false
        }
        return true
    }

    /**
     * Retrieves a list of directories containing JaCoCo reports from all subprojects.
     *
     * @param project The root project.
     * @param pluginExtension The plugin extension containing the configuration.
     * @return List of JaCoCo report directories.
     */
    private fun getJacocoGeneratedDirs(
        project: Project,
        pluginExtension: JacocoAggregateCoveragePluginExtension
    ): List<Directory> {
        val reportDirectory = pluginExtension.getReportDirectory() ?: return emptyList()
        return project.subprojects.map { subproject ->
            subproject.layout.buildDirectory
                .dir(reportDirectory)
                .get()
        }
    }

    /**
     * Collects JaCoCo test report tasks from subprojects.
     *
     * @param project The root project.
     * @param pluginExtension The plugin extension with configuration.
     * @return List of configured JaCoCo report tasks, or empty if none set.
     */
    private fun getJacocoTestReportTasks(
        project: Project,
        pluginExtension: JacocoAggregateCoveragePluginExtension,
    ): List<Task> {
        val jacocoTestReportTask = pluginExtension.jacocoTestReportTask.orNull ?: return emptyList()
        return project.subprojects.mapNotNull { subproject ->
            subproject.tasks.findByName(jacocoTestReportTask)
        }
    }

    /**
     * Configures the task to unzip plugin resources.
     * This task extracts necessary resources from the plugin to the build directory.
     *
     * @param task The task instance that needs to be configured.
     * @param project The project within which the task is being configured.
     */
    private fun configureUnzipTask(task: Copy, project: Project) {
        task.apply {
            group = TASK_GROUP
            description = "Unzip plugin resources into the build directory"
            val codeLocation = this@JacocoAggregateCoveragePlugin.javaClass.protectionDomain
                .codeSource.location.toExternalForm()
            from(project.zipTree(codeLocation)) {
                include("jacoco-resources/**")
                include("html/index.html")
            }
            into(
                project.layout.buildDirectory.dir(
                    "intermediates/jacoco_aggregate_coverage_plugin"
                )
            )
        }
    }

    /**
     * Creates and configures the plugin extension object.
     * The extension is used for configuring the plugin through the Gradle build script.
     *
     * @param project The project to which this extension will be added.
     * @return The created and configured [JacocoAggregateCoveragePluginExtension] instance.
     */
    private fun createPluginExtension(project: Project): JacocoAggregateCoveragePluginExtension {
        return project.extensions.create(
            EXTENSION_NAME_PLUGIN,
            JacocoAggregateCoveragePluginExtension::class.java
        ).apply {
            jacocoTestReportTask.convention(null)
            configuredCustomReportsDirectory.convention(null)
            configuredCustomHtmlOutputLocation.convention(null)
        }
    }
}
