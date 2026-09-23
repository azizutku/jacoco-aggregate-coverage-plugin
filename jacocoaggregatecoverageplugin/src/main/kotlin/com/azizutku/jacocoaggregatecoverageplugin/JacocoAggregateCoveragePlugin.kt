package com.azizutku.jacocoaggregatecoverageplugin

import com.azizutku.jacocoaggregatecoverageplugin.extensions.JacocoAggregateCoveragePluginExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.VerificationType
import org.gradle.api.provider.Provider

private const val TASK_GROUP = "verification"
private const val TASK_AGGREGATE_JACOCO_REPORTS = "aggregateJacocoReports"
private const val EXTENSION_NAME_PLUGIN = "jacocoAggregateCoverage"
private const val PLUGIN_OUTPUT_PATH = "reports/jacocoAggregated"
private const val REPORTS_CONFIGURATION_NAME = "jacocoAggregateReports"
private const val PARTICIPANTS_CONFIGURATION_NAME = "jacocoAggregationParticipants"

internal class JacocoAggregateCoveragePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        if (project.path != Project.PATH_SEPARATOR) {
            JacocoAggregateReportPlugin().apply(project)
            return
        }

        val extension = project.extensions.create(
            EXTENSION_NAME_PLUGIN,
            JacocoAggregateCoveragePluginExtension::class.java,
        ).apply {
            aggregatedReportDirectory.convention(
                project.layout.buildDirectory.dir(PLUGIN_OUTPUT_PATH),
            )
        }

        @Suppress("DEPRECATION")
        val reportSelection = extension.jacocoTestReportTask.orElse(SELECTED_JACOCO_REPORT)

        val reports = createReportsConfiguration(project, reportSelection)
        val participants = createParticipantsConfiguration(project)

        project.subprojects.forEach { subproject ->
            listOf(REPORTS_CONFIGURATION_NAME, PARTICIPANTS_CONFIGURATION_NAME).forEach { name ->
                project.dependencies.add(
                    name,
                    project.dependencies.project(mapOf("path" to subproject.path)),
                )
            }
        }

        val reportArtifacts = reports.incoming.artifactView { view ->
            view.isLenient = true
        }.files
        val participantArtifacts = participants.incoming.artifactView { view ->
            view.isLenient = true
        }.files

        project.tasks.register(
            TASK_AGGREGATE_JACOCO_REPORTS,
            AggregateJacocoReportsTask::class.java,
        ) { task ->
            validateLegacyConfiguration(extension)
            task.group = TASK_GROUP
            task.description = "Aggregates JaCoCo HTML reports from all participating subprojects"
            task.reportTaskName.set(reportSelection)
            task.reportArtifacts.from(reportArtifacts)
            task.participantArtifacts.from(participantArtifacts)
            task.outputDirectory.set(extension.aggregatedReportDirectory)
        }
    }

    private fun createReportsConfiguration(project: Project, reportSelection: Provider<String>): Configuration =
        project.configurations.create(REPORTS_CONFIGURATION_NAME) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.attributes { attributes ->
                attributes.attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    project.objects.named(Category::class.java, Category.VERIFICATION),
                )
                attributes.attribute(
                    VerificationType.VERIFICATION_TYPE_ATTRIBUTE,
                    project.objects.named(
                        VerificationType::class.java,
                        JACOCO_HTML_REPORT_VERIFICATION_TYPE,
                    ),
                )
                attributes.attributeProvider(JACOCO_REPORT_TASK_ATTRIBUTE, reportSelection)
            }
        }

    private fun createParticipantsConfiguration(project: Project): Configuration =
        project.configurations.create(PARTICIPANTS_CONFIGURATION_NAME) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.attributes { attributes ->
                attributes.attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    project.objects.named(Category::class.java, Category.VERIFICATION),
                )
                attributes.attribute(
                    VerificationType.VERIFICATION_TYPE_ATTRIBUTE,
                    project.objects.named(
                        VerificationType::class.java,
                        JACOCO_AGGREGATION_PARTICIPANT_VERIFICATION_TYPE,
                    ),
                )
            }
        }

    @Suppress("DEPRECATION")
    private fun validateLegacyConfiguration(extension: JacocoAggregateCoveragePluginExtension) {
        if (extension.configuredCustomReportsDirectory.isPresent ||
            extension.configuredCustomHtmlOutputLocation.isPresent
        ) {
            throw GradleException(
                "Custom report locations must be published from the participating project with " +
                    "jacocoAggregateReport.",
            )
        }
    }
}
