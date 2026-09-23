package com.azizutku.jacocoaggregatecoverageplugin

import com.azizutku.jacocoaggregatecoverageplugin.extensions.JacocoAggregateReportPluginExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.VerificationType
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.testing.jacoco.tasks.JacocoReport

private const val REPORT_EXTENSION_NAME = "jacocoAggregateReport"
private const val DEFAULT_REPORT_TASK_NAME = "jacocoTestReport"
private const val SELECTED_REPORT_CONFIGURATION_NAME = "jacocoSelectedHtmlReportElements"
private const val PARTICIPANT_CONFIGURATION_NAME = "jacocoAggregationParticipantElements"
private const val PARTICIPANT_TASK_NAME = "publishJacocoAggregationParticipant"

internal class JacocoAggregateReportPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val publishedTaskNames = mutableSetOf<String>()
        val publishedReports = mutableMapOf<String, TaskProvider<PrepareJacocoReportArtifactTask>>()
        val extension = project.extensions.create(
            REPORT_EXTENSION_NAME,
            JacocoAggregateReportPluginExtension::class.java,
        ).apply {
            reportTaskName.convention(DEFAULT_REPORT_TASK_NAME)
        }

        extension.reports.all { report ->
            publishedReports[report.name] = publishReport(
                project = project,
                reportTaskName = report.name,
                reportDirectory = report.htmlOutputLocation,
                reportEnabled = project.provider { true },
                publishedTaskNames = publishedTaskNames,
            )
        }

        project.tasks.withType(JacocoReport::class.java).all { reportTask ->
            publishedReports[reportTask.name] = publishReport(
                project = project,
                reportTaskName = reportTask.name,
                reportDirectory = reportTask.reports.html.outputLocation,
                reportEnabled = reportTask.reports.html.required,
                publishedTaskNames = publishedTaskNames,
            )
        }

        publishParticipation(project)
        publishSelectedReport(project, extension, publishedReports)
    }

    private fun publishParticipation(project: Project) {
        val participant = project.tasks.register(
            PARTICIPANT_TASK_NAME,
            PublishJacocoAggregationParticipantTask::class.java,
        ) { task ->
            task.modulePath.set(project.path)
            task.markerFile.set(
                project.layout.buildDirectory.file("jacoco-aggregate-artifacts/participant.txt"),
            )
        }
        project.configurations.create(PARTICIPANT_CONFIGURATION_NAME) { configuration ->
            configuration.isCanBeConsumed = true
            configuration.isCanBeResolved = false
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
            configuration.outgoing.artifact(participant.flatMap { it.markerFile }) { artifact ->
                artifact.builtBy(participant)
            }
        }
    }

    private fun publishReport(
        project: Project,
        reportTaskName: String,
        reportDirectory: Provider<Directory>,
        reportEnabled: Provider<Boolean>,
        publishedTaskNames: MutableSet<String>,
    ): TaskProvider<PrepareJacocoReportArtifactTask> {
        if (!publishedTaskNames.add(reportTaskName)) {
            throw GradleException(
                "A JaCoCo aggregate report named '$reportTaskName' is already published by " +
                    "${project.path}.",
            )
        }

        val suffix = reportTaskName.replaceFirstChar(Char::uppercaseChar)
        val prepareTask = project.tasks.register(
            "prepare${suffix}ForJacocoAggregation",
            PrepareJacocoReportArtifactTask::class.java,
        ) { task ->
            task.modulePath.set(project.path)
            task.reportEnabled.set(reportEnabled)
            task.reportFiles.from(project.provider { reportDirectory.get().asFile })
            task.reportDirectoryPath.set(
                project.provider { reportDirectory.get().asFile.absolutePath },
            )
            task.outputDirectory.set(
                project.layout.buildDirectory.dir("jacoco-aggregate-artifacts/$reportTaskName"),
            )
            task.dependsOn(reportTaskName)
        }

        project.configurations.create("jacoco${suffix}HtmlReportElements") { configuration ->
            configuration.isCanBeConsumed = true
            configuration.isCanBeResolved = false
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
                attributes.attribute(JACOCO_REPORT_TASK_ATTRIBUTE, reportTaskName)
            }
            configuration.outgoing.artifact(prepareTask.flatMap { it.outputDirectory }) { artifact ->
                artifact.builtBy(prepareTask)
            }
        }
        return prepareTask
    }

    private fun publishSelectedReport(
        project: Project,
        extension: JacocoAggregateReportPluginExtension,
        publishedReports: Map<String, TaskProvider<PrepareJacocoReportArtifactTask>>,
    ) {
        val selectedReport = extension.reportTaskName.flatMap { taskName ->
            publishedReports[taskName] ?: throw GradleException(
                "No JaCoCo report named '$taskName' is published by ${project.path}.",
            )
        }
        val selectedArtifact = selectedReport.flatMap { task -> task.outputDirectory }

        project.configurations.create(SELECTED_REPORT_CONFIGURATION_NAME) { configuration ->
            configuration.isCanBeConsumed = true
            configuration.isCanBeResolved = false
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
                attributes.attribute(JACOCO_REPORT_TASK_ATTRIBUTE, SELECTED_JACOCO_REPORT)
            }
            configuration.outgoing.artifact(selectedArtifact) { artifact ->
                artifact.builtBy(selectedReport)
            }
        }
    }
}
