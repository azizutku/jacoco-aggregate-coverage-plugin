package com.azizutku.jacocoaggregatecoverageplugin

import com.azizutku.jacocoaggregatecoverageplugin.extensions.JacocoAggregateCoveragePluginExtension
import com.azizutku.jacocoaggregatecoverageplugin.models.CoverageMetrics
import com.azizutku.jacocoaggregatecoverageplugin.models.ModuleCoverageRow
import com.azizutku.jacocoaggregatecoverageplugin.utils.HtmlCodeGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException

private const val TOTAL_COVERAGE_PLACEHOLDER = "TOTAL_COVERAGE_PLACEHOLDER"
private const val LINKED_MODULES_PLACEHOLDER = "LINKED_MODULES_PLACEHOLDER"

/**
 * A Gradle task that generates a unified HTML report from individual JaCoCo report files.
 * This task is responsible for collating the coverage metrics from multiple subprojects
 * and presenting them in a single, easily navigable HTML document.
 */
@CacheableTask
internal abstract class AggregateJacocoReportsTask : DefaultTask() {
    /**
     * The source folder that contains the plugin's resources.
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pluginSourceFolder: Property<File>

    /**
     * The set of JaCoCo report files for Gradle's incremental build checks.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jacocoReportsFileCollection: ConfigurableFileCollection

    /**
     * The directory where the aggregated JaCoCo reports will be generated and stored.
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * Lazily instantiated [HtmlCodeGenerator] for generating report HTML.
     */
    private val htmlCodeGenerator by lazy { HtmlCodeGenerator() }

    /**
     * A lazy-initialized map of subproject paths to their respective coverage metrics.
     * This map is used for generating the aggregated report.
     */
    private val subprojectToCoverageMap: Map<String, CoverageMetrics> by lazy {
        project.subprojects.associate {
            it.path to parseCoverageMetrics(it)
        }.filterValues { it != null }.mapValues { it.value!! }
    }

    /**
     * Executes the task action to create the unified index HTML.
     * This function orchestrates the reading of individual coverage reports,
     * aggregates the coverage data, and produces a single index HTML file
     * that represents the aggregated coverage information.
     */
    @TaskAction
    fun createUnifiedIndexHtml() {
        val maximumInstructionTotal = subprojectToCoverageMap.values.maxOfOrNull {
            it.instructionsTotal
        } ?: 0
        val maximumBranchesTotal = subprojectToCoverageMap.values.maxOfOrNull {
            it.branchesTotal
        } ?: 0

        val tableBodyForModules =
            subprojectToCoverageMap.entries
                .joinToString(separator = "\n") { (moduleName, moduleCoverage) ->
                    createModuleCoverageRow(
                        moduleName = moduleName,
                        moduleCoverage = moduleCoverage,
                        maxInstructionTotal = maximumInstructionTotal,
                        maxBranchesTotal = maximumBranchesTotal,
                    )
                }

        val unifiedMetrics =
            subprojectToCoverageMap.values.fold(CoverageMetrics()) { acc, metrics ->
                acc + metrics
            }

        updateBreadcrumbs()
        buildUnifiedIndexHtml(unifiedMetrics, tableBodyForModules)
    }

    /**
     * Updates the breadcrumbs for report navigation in the generated HTML files.
     * This method modifies the individual index HTML files of the subprojects
     * to include a link back to the root unified report.
     */
    private fun updateBreadcrumbs() {
        project.subprojects.forEach { subproject ->
            val indexHtmlFile = outputDirectory.dir(subproject.path)
                .get().file("index.html").asFile
            if (indexHtmlFile.exists().not()) {
                return@forEach
            }
            val document = Jsoup.parse(indexHtmlFile, Charsets.UTF_8.name())
            val spanElement = document.select("span.el_report").first()

            if (spanElement != null) {
                val newAnchor = document.createElement("a")
                newAnchor.attr("href", "../index.html")
                newAnchor.addClass("el_report")
                newAnchor.text("root")

                val newSpan = document.createElement("span")
                newSpan.addClass("el_package")
                newSpan.text(subproject.path)

                spanElement.before(newAnchor)
                newAnchor.after(newSpan).after(" &gt; ")

                spanElement.remove()
            }

            indexHtmlFile.writeText(document.outerHtml())
        }
    }

    /**
     * Parses coverage metrics from a subproject's JaCoCo report.
     *
     * @param subproject The subproject from which to parse coverage metrics.
     * @return A [CoverageMetrics] containing the parsed coverage data or null
     * if no data is found.
     */
    private fun parseCoverageMetrics(subproject: Project): CoverageMetrics? {
        val pluginExtension =
            project.extensions.getByType(JacocoAggregateCoveragePluginExtension::class.java)
        val generatedReportDirectory = pluginExtension.getReportDirectory()
        val indexHtmlFileProvider = subproject.layout.buildDirectory
            .file("$generatedReportDirectory/index.html")

        return CoverageMetrics.parseModuleCoverageMetrics(indexHtmlFileProvider)
    }

    /**
     * Creates an HTML table row representing the coverage data for a module.
     * This row includes progress bars and percentages for different coverage metrics.
     *
     * @param moduleName The name of the module.
     * @param moduleCoverage The coverage metrics for the module.
     * @param maxInstructionTotal The maximum total instructions for scaling the progress bar.
     * @param maxBranchesTotal The maximum total branches for scaling the progress bar.
     * @return An HTML string representing the table row.
     */
    private fun createModuleCoverageRow(
        moduleName: String,
        moduleCoverage: CoverageMetrics,
        maxInstructionTotal: Int,
        maxBranchesTotal: Int,
    ): String {
        val moduleCoverageRow = ModuleCoverageRow.generateModuleCoverageRow(
            moduleName = moduleName,
            moduleCoverage = moduleCoverage,
            maxInstructionTotal = maxInstructionTotal,
            maxBranchesTotal = maxBranchesTotal,
            subprojectToCoverageMap = subprojectToCoverageMap,
        )
        return htmlCodeGenerator.generateModuleCoverageTableRowHtml(
            moduleName = moduleName,
            coverageMetrics = moduleCoverage,
            moduleCoverageRow = moduleCoverageRow,
        )
    }

    /**
     * Builds the final unified index HTML file using a template and the coverage data.
     * Replaces placeholders in the template with actual coverage data and module links.
     *
     * @param metrics The aggregated coverage metrics.
     * @param linksHtml The HTML string containing links to the individual module reports.
     */
    private fun buildUnifiedIndexHtml(metrics: CoverageMetrics, linksHtml: String) {
        try {
            val templateFile = project.file("${pluginSourceFolder.get().path}/html/index.html")
            val templateText = templateFile.readText(Charsets.UTF_8)

            val totalCoverageString = htmlCodeGenerator.createTotalCoverageString(metrics)
            val newText = templateText
                .replace(TOTAL_COVERAGE_PLACEHOLDER, totalCoverageString)
                .replace(LINKED_MODULES_PLACEHOLDER, linksHtml)

            val destination = outputDirectory.file("index.html").get().asFile
            destination.writeText(newText, Charsets.UTF_8)
            logger.lifecycle("Aggregated report is generated at: ${destination.absolutePath}")
        } catch (exception: IOException) {
            logger.error("Error occurred while aggregating reports: ${exception.message}")
        }
    }
}
