package com.azizutku.jacocoaggregatecoverageplugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@CacheableTask
internal abstract class PrepareJacocoReportArtifactTask : DefaultTask() {

    @get:Input
    abstract val modulePath: Property<String>

    @get:Input
    abstract val reportEnabled: Property<Boolean>

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reportFiles: ConfigurableFileCollection

    @get:Internal
    abstract val reportDirectoryPath: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun prepareArtifact() {
        val output = outputDirectory.get().asFile
        fileSystemOperations.delete { spec -> spec.delete(output) }
        output.mkdirs()
        File(output, REPORT_MODULE_PATH_FILE).writeText(
            modulePath.get(),
            StandardCharsets.UTF_8,
        )

        validateReportEnabled()
        val report = File(reportDirectoryPath.get())
        validateReport(report)

        fileSystemOperations.copy { spec ->
            spec.from(report)
            spec.into(File(output, REPORT_ARTIFACT_DIRECTORY))
        }
    }

    private fun validateReportEnabled() {
        if (!reportEnabled.get()) {
            throw GradleException(
                "The selected JaCoCo report for ${modulePath.get()} has HTML output disabled.",
            )
        }
    }

    private fun validateReport(report: File) {
        if (!File(report, "index.html").isFile) {
            throw GradleException(
                "The selected JaCoCo report for ${modulePath.get()} did not generate " +
                    "'$report/index.html'.",
            )
        }
        val resources = File(report, "jacoco-resources")
        val missingResources = REQUIRED_JACOCO_RESOURCE_FILES.filterNot { resource ->
            File(resources, resource).isFile
        }
        if (missingResources.isNotEmpty()) {
            throw GradleException(
                "The selected JaCoCo report for ${modulePath.get()} is missing required " +
                    "resource(s): ${missingResources.joinToString()}.",
            )
        }
    }
}
