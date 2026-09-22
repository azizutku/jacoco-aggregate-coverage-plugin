package com.azizutku.jacocoaggregatecoverageplugin.models

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File

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
private const val EXPECTED_CELL_COUNT = 13
private const val ONE_HUNDRED = 100

internal data class CoverageMetrics(
    val instructionsMissed: Long = 0,
    val instructionsTotal: Long = 0,
    val branchesMissed: Long = 0,
    val branchesTotal: Long = 0,
    val complexityMissed: Long = 0,
    val complexityTotal: Long = 0,
    val linesMissed: Long = 0,
    val linesTotal: Long = 0,
    val methodsMissed: Long = 0,
    val methodsTotal: Long = 0,
    val classesMissed: Long = 0,
    val classesTotal: Long = 0,
) {
    val instructionsCoverage: Int
        get() = calculateCoverage(instructionsMissed, instructionsTotal)

    val branchesCoverage: Int
        get() = calculateCoverage(branchesMissed, branchesTotal)

    operator fun plus(other: CoverageMetrics): CoverageMetrics = CoverageMetrics(
        instructionsMissed = instructionsMissed + other.instructionsMissed,
        instructionsTotal = instructionsTotal + other.instructionsTotal,
        branchesMissed = branchesMissed + other.branchesMissed,
        branchesTotal = branchesTotal + other.branchesTotal,
        complexityMissed = complexityMissed + other.complexityMissed,
        complexityTotal = complexityTotal + other.complexityTotal,
        linesMissed = linesMissed + other.linesMissed,
        linesTotal = linesTotal + other.linesTotal,
        methodsMissed = methodsMissed + other.methodsMissed,
        methodsTotal = methodsTotal + other.methodsTotal,
        classesMissed = classesMissed + other.classesMissed,
        classesTotal = classesTotal + other.classesTotal,
    )

    companion object {
        fun calculateCoveragePercentage(missed: Long, total: Long): String =
            if (total == 0L) "n/a" else "${calculateCoverage(missed, total)}%"

        fun parseModuleCoverageMetrics(indexHtmlFile: File): CoverageMetrics? = if (!indexHtmlFile.isFile) {
            null
        } else {
            Jsoup.parse(indexHtmlFile, Charsets.UTF_8.name())
                .selectFirst("tfoot tr")
                ?.let { footer -> parseFooter(footer, indexHtmlFile) }
        }

        private fun parseFooter(footer: Element, indexHtmlFile: File): CoverageMetrics {
            val cells = footer.select("td")
            require(cells.size >= EXPECTED_CELL_COUNT) {
                "Unsupported JaCoCo HTML summary in '$indexHtmlFile': expected at least " +
                    "$EXPECTED_CELL_COUNT cells but found ${cells.size}."
            }
            val instructions = extractPair(cells[INDEX_INSTRUCTIONS], indexHtmlFile)
            val branches = extractPair(cells[INDEX_BRANCHES], indexHtmlFile)
            return CoverageMetrics(
                instructionsMissed = instructions.first,
                instructionsTotal = instructions.second,
                branchesMissed = branches.first,
                branchesTotal = branches.second,
                complexityMissed = extractSingle(cells[INDEX_COMPLEXITIES_MISSED], indexHtmlFile),
                complexityTotal = extractSingle(cells[INDEX_COMPLEXITIES_TOTAL], indexHtmlFile),
                linesMissed = extractSingle(cells[INDEX_LINES_MISSED], indexHtmlFile),
                linesTotal = extractSingle(cells[INDEX_LINES_TOTAL], indexHtmlFile),
                methodsMissed = extractSingle(cells[INDEX_METHODS_MISSED], indexHtmlFile),
                methodsTotal = extractSingle(cells[INDEX_METHODS_TOTAL], indexHtmlFile),
                classesMissed = extractSingle(cells[INDEX_CLASSES_MISSED], indexHtmlFile),
                classesTotal = extractSingle(cells[INDEX_CLASSES_TOTAL], indexHtmlFile),
            )
        }

        private fun calculateCoverage(missed: Long, total: Long): Int =
            if (total == 0L) -1 else (((total - missed).toDouble() / total) * ONE_HUNDRED).toInt()

        private fun extractPair(cell: Element, source: File): Pair<Long, Long> {
            val parts = cell.text().split(Regex("\\s+of\\s+"), limit = 2)
            require(parts.size == 2) {
                "Unsupported JaCoCo counter '${cell.text()}' in '$source'."
            }
            return parseNumber(parts[0], source) to parseNumber(parts[1], source)
        }

        private fun extractSingle(cell: Element, source: File): Long = parseNumber(cell.text(), source)

        private fun parseNumber(value: String, source: File): Long {
            val digits = value.filter(Char::isDigit)
            require(digits.isNotEmpty()) { "Expected a numeric JaCoCo counter in '$source'." }
            return digits.toLong()
        }
    }
}
