package com.azizutku.jacocoaggregatecoverageplugin.extensions

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

interface JacocoAggregateCoveragePluginExtension {
    val jacocoTestReportTask: Property<String>
    val configuredCustomReportsDirectory: Property<String>
    val configuredCustomHtmlOutputLocation: Property<String>
    val aggregatedReportDirectory: DirectoryProperty

    fun getReportDirectory(): String? = when {
        configuredCustomHtmlOutputLocation.orNull != null ->
            configuredCustomHtmlOutputLocation.get()

        configuredCustomReportsDirectory.orNull != null ->
            jacocoTestReportTask.orNull?.let { taskName ->
                "${configuredCustomReportsDirectory.get()}/$taskName/html"
            }

        else -> jacocoTestReportTask.orNull?.let { taskName ->
            "reports/jacoco/$taskName/html"
        }
    }
}
