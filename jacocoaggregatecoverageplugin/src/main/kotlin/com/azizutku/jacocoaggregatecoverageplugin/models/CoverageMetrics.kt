package com.azizutku.jacocoaggregatecoverageplugin.models

import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

// Indexes
private const val INDEX_INSTRUCTIONS = 1
private const val INDEX_BRANCHES = 3
private const val INDEX_COMPLEXITIES_MISSED = 5
private const val INDEX_COMPLEXITIES_TOTAL = 6
private const val INDEX_LINES_MISSED = 7
private const val INDEX_LINES_TOTAL = 8
private const val INDEX_METHODS_MISSED = 9
private const val INDEX_METHODS_TOTAL = 10
private const val INDEX_CLASSES_MISSED = 11
private const val INDEX_CLASSES_TOTAL = 12

private const val SPACE = " "
private const val SELECTOR_TD = "td"

private const val NUMBER_HUNDRED = 100

/**
 * Data class representing the metrics of code coverage.
 *
 * @property instructionsMissed The number of missed instructions.
 * @property instructionsTotal The total number of instructions.
 * @property branchesMissed The number of missed branches.
 * @property branchesTotal The total number of branches.
 * @property complexityMissed The number of missed complexities.
 * @property complexityTotal The total number of complexities.
 * @property linesMissed The number of missed lines.
 * @property linesTotal The total number of lines.
 * @property methodsMissed The number of missed methods.
 * @property methodsTotal The total number of methods.
 * @property classesMissed The number of missed classes.
 * @property classesTotal The total number of classes.
 */
data class CoverageMetrics(
    val instructionsMissed: Int = 0,
    val instructionsTotal: Int = 0,
    val branchesMissed: Int = 0,
    val branchesTotal: Int = 0,
    val complexityMissed: Int = 0,
    val complexityTotal: Int = 0,
    val linesMissed: Int = 0,
    val linesTotal: Int = 0,
    val methodsMissed: Int = 0,
    val methodsTotal: Int = 0,
    val classesMissed: Int = 0,
    val classesTotal: Int = 0
) {

    /**
     * The coverage percentage of instructions.
     */
    val instructionsCoverage: Int
        get() = calculateCoverage(instructionsMissed, instructionsTotal)

    /**
     * The coverage percentage of branches.
     */
    val branchesCoverage: Int
        get() = calculateCoverage(branchesMissed, branchesTotal)

    /**
     * Calculates the coverage percentage based on missed and total metrics.
     *
     * @param missed The number of missed elements (instructions, branches, etc.).
     * @param total The total number of elements.
     * @return The coverage percentage as an integer.
     */
    private fun calculateCoverage(missed: Int, total: Int): Int = if (total != 0) {
        ((total - missed) / total.toDouble() * NUMBER_HUNDRED).toInt()
    } else {
        -1
    }

    /**
     * Aggregates coverage metrics from another [CoverageMetrics] instance.
     * This is useful for combining coverage data from multiple modules or tests.
     *
     * @param other Another [CoverageMetrics] instance to combine with this one.
     * @return A new [CoverageMetrics] instance representing the aggregated metrics.
     */
    operator fun plus(other: CoverageMetrics): CoverageMetrics = CoverageMetrics(
        instructionsMissed + other.instructionsMissed,
        instructionsTotal + other.instructionsTotal,
        branchesMissed + other.branchesMissed,
        branchesTotal + other.branchesTotal,
        complexityMissed + other.complexityMissed,
        complexityTotal + other.complexityTotal,
        linesMissed + other.linesMissed,
        linesTotal + other.linesTotal,
        methodsMissed + other.methodsMissed,
        methodsTotal + other.methodsTotal,
        classesMissed + other.classesMissed,
        classesTotal + other.classesTotal
    )

    companion object {
        /**
         * Calculates and formats the coverage percentage as a human-readable string.
         *
         * @param missed The number of missed elements (instructions, branches, etc.).
         * @param total The total number of elements.
         * @return The formatted coverage percentage string.
         */
        fun calculateCoveragePercentage(missed: Int, total: Int): String {
            if (total == 0) return "n/a"
            val coverage = (((total - missed) / total.toDouble()) * NUMBER_HUNDRED).toInt()
            return "$coverage%"
        }

        /**
         * Parses JaCoCo coverage metrics from a module's HTML report.
         *
         * @param indexHtmlFileProvider Provider for the module's index.html file.
         * @return [CoverageMetrics] with parsed data or `null` if the file is missing.
         */
        fun parseModuleCoverageMetrics(
            indexHtmlFileProvider: Provider<RegularFile>,
        ): CoverageMetrics? {
            val indexHtmlFile = indexHtmlFileProvider.get().asFile
            if (indexHtmlFile.exists().not()) {
                return null
            }
            return Jsoup.parse(
                indexHtmlFile,
                Charsets.UTF_8.name(),
            ).select("tfoot tr").firstOrNull()?.let { footer ->
                CoverageMetrics(
                    instructionsMissed = extractMissedValue(footer, INDEX_INSTRUCTIONS),
                    instructionsTotal = extractTotalValue(footer, INDEX_INSTRUCTIONS),
                    branchesMissed = extractMissedValue(footer, INDEX_BRANCHES),
                    branchesTotal = extractTotalValue(footer, INDEX_BRANCHES),
                    complexityMissed = extractSingleValue(footer, INDEX_COMPLEXITIES_MISSED),
                    complexityTotal = extractSingleValue(footer, INDEX_COMPLEXITIES_TOTAL),
                    linesMissed = extractSingleValue(footer, INDEX_LINES_MISSED),
                    linesTotal = extractSingleValue(footer, INDEX_LINES_TOTAL),
                    methodsMissed = extractSingleValue(footer, INDEX_METHODS_MISSED),
                    methodsTotal = extractSingleValue(footer, INDEX_METHODS_TOTAL),
                    classesMissed = extractSingleValue(footer, INDEX_CLASSES_MISSED),
                    classesTotal = extractSingleValue(footer, INDEX_CLASSES_TOTAL),
                )
            }
        }

        /**
         * Extracts a numeric value from a table cell in an HTML document.
         * Used for parsing coverage data from HTML reports.
         *
         * @param element The HTML element representing a table row.
         * @param index The index of the table cell from which to extract the value.
         * @return The extracted value as an integer.
         */
        private fun extractSingleValue(element: Element, index: Int): Int {
            return element.select(SELECTOR_TD).eq(index).text().toIntOrNull() ?: 0
        }

        /**
         * Extracts the missed value from a coverage data cell in an HTML document.
         * Used for parsing the number of missed elements (instructions, branches, etc.) from coverage reports.
         *
         * @param element The HTML element representing a table row.
         * @param index The index of the table cell from which to extract the missed value.
         * @return The number of missed elements as an integer.
         */
        private fun extractMissedValue(element: Element, index: Int): Int {
            val text = element.select(SELECTOR_TD).eq(index).text()
            val values = text.split(SPACE)
            return values[0].filter { it.isDigit() }.toIntOrNull() ?: 0
        }

        /**
         * Extracts the total value from a coverage data cell in an HTML document.
         * Used for parsing the total number of elements (instructions, branches, etc.) from coverage reports.
         *
         * @param element The HTML element representing a table row.
         * @param index The index of the table cell from which to extract the total value.
         * @return The total number of elements as an integer.
         */
        private fun extractTotalValue(element: Element, index: Int): Int {
            val text = element.select(SELECTOR_TD).eq(index).text()
            val values = text.split(SPACE)
            return values[2].filter { it.isDigit() }.toIntOrNull() ?: 0
        }
    }
}
