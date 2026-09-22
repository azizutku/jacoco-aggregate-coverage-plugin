package com.azizutku.jacocoaggregatecoverageplugin

import org.gradle.api.attributes.Attribute

internal const val JACOCO_HTML_REPORT_VERIFICATION_TYPE = "jacoco-html-report"
internal const val JACOCO_AGGREGATION_PARTICIPANT_VERIFICATION_TYPE =
    "jacoco-aggregation-participant"
internal const val SELECTED_JACOCO_REPORT = "com.azizutku.selected-jacoco-report"
internal const val REPORT_ARTIFACT_DIRECTORY = "report"
internal const val REPORT_MODULE_PATH_FILE = "module-path.txt"
internal val REQUIRED_JACOCO_RESOURCE_FILES = listOf(
    "greenbar.gif",
    "redbar.gif",
    "report.css",
    "report.gif",
    "sort.js",
)

internal val JACOCO_REPORT_TASK_ATTRIBUTE: Attribute<String> = Attribute.of(
    "com.azizutku.jacoco-report-task",
    String::class.java,
)
