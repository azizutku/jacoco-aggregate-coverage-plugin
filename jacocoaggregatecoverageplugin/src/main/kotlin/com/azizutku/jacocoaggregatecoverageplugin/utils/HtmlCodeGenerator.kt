package com.azizutku.jacocoaggregatecoverageplugin.utils

import com.azizutku.jacocoaggregatecoverageplugin.models.CoverageMetrics
import com.azizutku.jacocoaggregatecoverageplugin.models.ModuleCoverageRow

/**
 * Utility class for generating HTML code for coverage reporting.
 */
internal class HtmlCodeGenerator {

    /**
     * Generates HTML for a table row representing a module's coverage metrics.
     *
     * @param moduleName Name of the module.
     * @param coverageMetrics Coverage metrics associated with the module.
     * @param moduleCoverageRow Detailed coverage data for the module.
     * @return HTML string for the module's coverage table row.
     */
    fun generateModuleCoverageTableRowHtml(
        moduleName: String,
        coverageMetrics: CoverageMetrics,
        moduleCoverageRow: ModuleCoverageRow,
    ): String = with(moduleCoverageRow) {
        return """
            <tr>
                <td id="a$moduleNameOrder"><a href="$moduleName/index.html">$moduleName</a></td>
                <td class="bar" id="b$instructionsMissedOrder">
                    $instructionMissedRedProgressBar$instructionMissedGreenProgressBar
                </td>
                <td class="ctr2" id="c$instructionsCoverageOrder">$instructionsCoverage</td>
                <td class="bar" id="d$branchesMissedOrder">
                    $branchesMissedRedProgressBar$branchesMissedGreenProgressBar
                </td>
                <td class="ctr2" id="e$branchesCoverageOrder">$branchesCoverage</td>
                <td class="ctr1" id="f$complexityMissedOrder">
                    ${coverageMetrics.complexityMissed}
                </td>
                <td class="ctr2" id="g$complexityTotalOrder">${coverageMetrics.complexityTotal}</td>
                <td class="ctr1" id="h$linesMissedOrder">${coverageMetrics.linesMissed}</td>
                <td class="ctr2" id="i$linesTotalOrder">${coverageMetrics.linesTotal}</td>
                <td class="ctr1" id="j$methodsMissedOrder">${coverageMetrics.methodsMissed}</td>
                <td class="ctr2" id="k$methodsTotalOrder">${coverageMetrics.methodsTotal}</td>
                <td class="ctr1" id="l$classesMissedOrder">${coverageMetrics.classesMissed}</td>
                <td class="ctr2" id="m$classesTotalOrder">${coverageMetrics.classesTotal}</td>
            </tr>
        """.trimIndent()
    }

    /**
     * Creates an HTML string representing the total coverage metrics.
     * This string is used to populate the summary section of the unified report.
     *
     * @param metrics The aggregated coverage metrics.
     * @return An HTML string representing the total coverage.
     */
    fun createTotalCoverageString(metrics: CoverageMetrics): String = with(metrics) {
        // Constructing the total coverage string to replace in the HTML template
        val instructionsCoveragePercentage = CoverageMetrics.calculateCoveragePercentage(
            instructionsMissed,
            instructionsTotal,
        )
        val branchesCoveragePercentage = CoverageMetrics.calculateCoveragePercentage(
            branchesMissed,
            branchesTotal,
        )
        """
            <td class="bar">$instructionsMissed of $instructionsTotal</td>
            <td class="ctr2">$instructionsCoveragePercentage</td>
            <td class="bar">$branchesMissed of $branchesTotal</td>
            <td class="ctr2">$branchesCoveragePercentage</td>
            <td class="ctr1">$complexityMissed</td>
            <td class="ctr2">$complexityTotal</td>
            <td class="ctr1">$linesMissed</td>
            <td class="ctr2">$linesTotal</td>
            <td class="ctr1">$methodsMissed</td>
            <td class="ctr2">$methodsTotal</td>
            <td class="ctr1">$classesMissed</td>
            <td class="ctr2">$classesTotal</td>
        """.trimIndent()
    }
}
