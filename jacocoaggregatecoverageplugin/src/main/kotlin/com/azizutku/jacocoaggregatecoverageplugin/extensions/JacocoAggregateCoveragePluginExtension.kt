package com.azizutku.jacocoaggregatecoverageplugin.extensions

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

interface JacocoAggregateCoveragePluginExtension {
    @get:Deprecated(
        "Configure reportTaskName in each participating project with jacocoAggregateReport.",
    )
    val jacocoTestReportTask: Property<String>

    @get:Deprecated(
        "Configure custom HTML output in the producing project with jacocoAggregateReport.",
    )
    val configuredCustomReportsDirectory: Property<String>

    @get:Deprecated(
        "Configure custom HTML output in the producing project with jacocoAggregateReport.",
    )
    val configuredCustomHtmlOutputLocation: Property<String>

    val aggregatedReportDirectory: DirectoryProperty
}
