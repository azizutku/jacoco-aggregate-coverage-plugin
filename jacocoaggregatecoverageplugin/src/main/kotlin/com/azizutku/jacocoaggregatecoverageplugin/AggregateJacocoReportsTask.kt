package com.azizutku.jacocoaggregatecoverageplugin

import com.azizutku.jacocoaggregatecoverageplugin.models.CoverageMetrics
import com.azizutku.jacocoaggregatecoverageplugin.models.CoverageRankings
import com.azizutku.jacocoaggregatecoverageplugin.models.ModuleCoverageRow
import com.azizutku.jacocoaggregatecoverageplugin.utils.HtmlCodeGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject

private const val TOTAL_COVERAGE_PLACEHOLDER = "TOTAL_COVERAGE_PLACEHOLDER"
private const val LINKED_MODULES_PLACEHOLDER = "LINKED_MODULES_PLACEHOLDER"
private const val TEMPLATE_RESOURCE = "/html/index.html"
private const val UNSIGNED_BYTE_MASK = 0xff
private const val HEX_BYTE_LENGTH = 2
private const val HEX_RADIX = 16
private const val ASCII_LIMIT = 128

@CacheableTask
internal abstract class AggregateJacocoReportsTask : DefaultTask() {

    @get:Input
    abstract val moduleReportLocations: MapProperty<String, String>

    @get:Internal
    abstract val moduleReportDirectories: MapProperty<String, Directory>

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jacocoReports: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun aggregateReports() {
        val availableReports = findAvailableReports()
        if (availableReports.isEmpty()) {
            throw GradleException(
                "No JaCoCo HTML reports were generated. Ensure the configured report task " +
                    "enables its HTML report.",
            )
        }

        val output = outputDirectory.get().asFile
        fileSystemOperations.delete { spec -> spec.delete(output) }
        output.mkdirs()

        val copiedReports = availableReports.associate { report ->
            val moduleDirectory = File(output, moduleOutputPath(report.modulePath))
            fileSystemOperations.copy { spec ->
                spec.from(report.directory)
                spec.into(moduleDirectory)
            }
            report.modulePath to moduleDirectory
        }

        copyRootResources(availableReports.first().directory, output)
        val coverageByModule = copiedReports.mapValues { (_, directory) ->
            CoverageMetrics.parseModuleCoverageMetrics(File(directory, "index.html"))
                ?: throw GradleException("The JaCoCo report at '$directory' has no summary row.")
        }

        copiedReports.forEach { (modulePath, directory) ->
            updateBreadcrumb(File(directory, "index.html"), modulePath)
        }
        writeAggregateIndex(output, coverageByModule)

        logger.lifecycle("Aggregated JaCoCo report: ${File(output, "index.html").absolutePath}")
    }

    private fun findAvailableReports(): List<ModuleReportDirectory> {
        val directories = moduleReportDirectories.get()
        return moduleReportLocations.get().keys.sorted().mapNotNull { modulePath ->
            val directory = directories[modulePath]?.asFile ?: return@mapNotNull null
            if (File(directory, "index.html").isFile) {
                ModuleReportDirectory(modulePath, directory)
            } else {
                logger.warn("Skipping $modulePath: no JaCoCo HTML report at $directory")
                null
            }
        }
    }

    private fun copyRootResources(sourceReport: File, output: File) {
        val resources = File(sourceReport, "jacoco-resources")
        if (!resources.isDirectory) {
            throw GradleException("The JaCoCo report at '$sourceReport' has no jacoco-resources.")
        }
        fileSystemOperations.copy { spec ->
            spec.from(resources)
            spec.into(File(output, "jacoco-resources"))
        }
    }

    private fun updateBreadcrumb(indexFile: File, modulePath: String) {
        val document = Jsoup.parse(indexFile, StandardCharsets.UTF_8.name())
        val reportElement = document.selectFirst("span.el_report") ?: return
        val rootLink = Element("a")
            .addClass("el_report")
            .attr("href", rootLinkFor(modulePath))
            .text("root")
        val module = Element("span").addClass("el_package").text(modulePath)

        reportElement.replaceWith(rootLink)
        rootLink.after(TextNode(" > "))
        rootLink.nextSibling()?.after(module)
        indexFile.writeText(document.outerHtml(), StandardCharsets.UTF_8)
    }

    private fun writeAggregateIndex(output: File, coverageByModule: Map<String, CoverageMetrics>) {
        val maximumInstructionTotal = coverageByModule.values.maxOf(CoverageMetrics::instructionsTotal)
        val maximumBranchesTotal = coverageByModule.values.maxOf(CoverageMetrics::branchesTotal)
        val rankings = CoverageRankings(coverageByModule)
        val htmlGenerator = HtmlCodeGenerator()
        val tableRows = coverageByModule.entries.joinToString("\n") { (modulePath, coverage) ->
            htmlGenerator.generateModuleCoverageTableRowHtml(
                moduleName = modulePath,
                moduleHref = "${moduleOutputPath(modulePath)}/index.html",
                coverageMetrics = coverage,
                moduleCoverageRow = ModuleCoverageRow.create(
                    moduleName = modulePath,
                    moduleCoverage = coverage,
                    maxInstructionTotal = maximumInstructionTotal,
                    maxBranchesTotal = maximumBranchesTotal,
                    rankings = rankings,
                ),
            )
        }
        val total = coverageByModule.values.fold(CoverageMetrics(), CoverageMetrics::plus)
        val template = javaClass.getResourceAsStream(TEMPLATE_RESOURCE)?.bufferedReader()?.use {
            it.readText()
        } ?: throw GradleException("Plugin resource '$TEMPLATE_RESOURCE' is missing.")
        val html = template
            .replace(TOTAL_COVERAGE_PLACEHOLDER, htmlGenerator.createTotalCoverageString(total))
            .replace(LINKED_MODULES_PLACEHOLDER, tableRows)
        File(output, "index.html").writeText(html, StandardCharsets.UTF_8)
    }

    private fun rootLinkFor(modulePath: String): String {
        val depth = moduleSegments(modulePath).size
        return "../".repeat(depth) + "index.html"
    }

    private fun moduleOutputPath(modulePath: String): String =
        moduleSegments(modulePath).joinToString("/") { encodePathSegment(it) }

    private fun moduleSegments(modulePath: String): List<String> = modulePath.split(':').filter(String::isNotEmpty)

    private fun encodePathSegment(segment: String): String {
        if (segment == "." || segment == "..") {
            return segment.map { "%${it.code.toString(HEX_RADIX).uppercase()}" }.joinToString("")
        }
        return segment.toByteArray(StandardCharsets.UTF_8).joinToString("") { byte ->
            val value = byte.toInt() and UNSIGNED_BYTE_MASK
            val character = value.toChar()
            if (value < ASCII_LIMIT && (character.isLetterOrDigit() || character in "-._~")) {
                character.toString()
            } else {
                "%${value.toString(HEX_RADIX).uppercase().padStart(HEX_BYTE_LENGTH, '0')}"
            }
        }
    }

    private data class ModuleReportDirectory(val modulePath: String, val directory: File)
}
