package com.azizutku.jacocoaggregatecoverageplugin.extensions

import org.gradle.api.provider.Property

/**
 * Extension for configuring the JaCoCo Aggregate Coverage Plugin.
 *
 * @property jacocoTestReportTask Specifies the JaCoCo test report task that needs to be executed
 * before aggregation.
 * @property configuredCustomReportsDirectory If set, the plugin uses this directory to locate
 * JaCoCo reports.
 * @property configuredCustomHtmlOutputLocation If set, the plugin uses this directory for the
 * output of the generated HTML report.
 */
interface JacocoAggregateCoveragePluginExtension {
    val jacocoTestReportTask: Property<String>
    val configuredCustomReportsDirectory: Property<String>
    val configuredCustomHtmlOutputLocation: Property<String>

    /**
     * Determines the JaCoCo reports directory path.
     *
     * @return The path to the report directory or `null` if task name is not configured.
     */
    fun getReportDirectory(): String? {
        return when {
            jacocoTestReportTask.orNull == null -> null
            configuredCustomHtmlOutputLocation.orNull != null ->
                configuredCustomHtmlOutputLocation.get()
            configuredCustomReportsDirectory.orNull != null ->
                "${configuredCustomReportsDirectory.get()}/${jacocoTestReportTask.get()}/html"
            else -> "reports/jacoco/${jacocoTestReportTask.get()}/html"
        }
    }
}
