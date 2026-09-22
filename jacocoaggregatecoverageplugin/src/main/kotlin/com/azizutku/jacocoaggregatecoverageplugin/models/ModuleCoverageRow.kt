package com.azizutku.jacocoaggregatecoverageplugin.models

private const val MAXIMUM_WIDTH_FOR_PROGRESS_BARS = 120

internal data class ModuleCoverageRow(
    val instructionsCoverage: String,
    val branchesCoverage: String,
    val moduleNameOrder: Int,
    val instructionsMissedOrder: Int,
    val instructionMissedRedProgressBar: String,
    val instructionMissedGreenProgressBar: String,
    val branchesMissedRedProgressBar: String,
    val branchesMissedGreenProgressBar: String,
    val instructionsCoverageOrder: Int,
    val branchesMissedOrder: Int,
    val branchesCoverageOrder: Int,
    val complexityMissedOrder: Int,
    val complexityTotalOrder: Int,
    val linesMissedOrder: Int,
    val linesTotalOrder: Int,
    val methodsMissedOrder: Int,
    val methodsTotalOrder: Int,
    val classesMissedOrder: Int,
    val classesTotalOrder: Int,
) {
    companion object {
        fun create(
            moduleName: String,
            moduleCoverage: CoverageMetrics,
            maxInstructionTotal: Long,
            maxBranchesTotal: Long,
            rankings: CoverageRankings,
        ): ModuleCoverageRow {
            val rank = rankings.forModule(moduleName)
            return ModuleCoverageRow(
                instructionsCoverage = CoverageMetrics.calculateCoveragePercentage(
                    moduleCoverage.instructionsMissed,
                    moduleCoverage.instructionsTotal,
                ),
                branchesCoverage = CoverageMetrics.calculateCoveragePercentage(
                    moduleCoverage.branchesMissed,
                    moduleCoverage.branchesTotal,
                ),
                moduleNameOrder = rank.moduleName,
                instructionsMissedOrder = rank.instructionsMissed,
                instructionMissedRedProgressBar = progressBar(
                    moduleCoverage.instructionsMissed,
                    maxInstructionTotal,
                    "red",
                ),
                instructionMissedGreenProgressBar = progressBar(
                    moduleCoverage.instructionsTotal - moduleCoverage.instructionsMissed,
                    maxInstructionTotal,
                    "green",
                ),
                branchesMissedRedProgressBar = progressBar(
                    moduleCoverage.branchesMissed,
                    maxBranchesTotal,
                    "red",
                ),
                branchesMissedGreenProgressBar = progressBar(
                    moduleCoverage.branchesTotal - moduleCoverage.branchesMissed,
                    maxBranchesTotal,
                    "green",
                ),
                instructionsCoverageOrder = rank.instructionsCoverage,
                branchesMissedOrder = rank.branchesMissed,
                branchesCoverageOrder = rank.branchesCoverage,
                complexityMissedOrder = rank.complexityMissed,
                complexityTotalOrder = rank.complexityTotal,
                linesMissedOrder = rank.linesMissed,
                linesTotalOrder = rank.linesTotal,
                methodsMissedOrder = rank.methodsMissed,
                methodsTotalOrder = rank.methodsTotal,
                classesMissedOrder = rank.classesMissed,
                classesTotalOrder = rank.classesTotal,
            )
        }

        private fun progressBar(value: Long, maximum: Long, color: String): String {
            val width = if (maximum == 0L) {
                0
            } else {
                (value.toDouble() / maximum * MAXIMUM_WIDTH_FOR_PROGRESS_BARS).toInt()
            }
            return "<img src=\"jacoco-resources/${color}bar.gif\" width=\"$width\" " +
                "height=\"10\" title=\"$value\" alt=\"$value\" />"
        }
    }
}

internal class CoverageRankings(coverageByModule: Map<String, CoverageMetrics>) {
    private val moduleName = ranks(coverageByModule) { key, _ -> key }
    private val instructionsMissed = ranks(coverageByModule) { _, value -> value.instructionsMissed }
    private val instructionsCoverage = ranks(coverageByModule) { _, value -> value.instructionsCoverage }
    private val branchesMissed = ranks(coverageByModule) { _, value -> value.branchesMissed }
    private val branchesCoverage = ranks(coverageByModule) { _, value -> value.branchesCoverage }
    private val complexityMissed = ranks(coverageByModule) { _, value -> value.complexityMissed }
    private val complexityTotal = ranks(coverageByModule) { _, value -> value.complexityTotal }
    private val linesMissed = ranks(coverageByModule) { _, value -> value.linesMissed }
    private val linesTotal = ranks(coverageByModule) { _, value -> value.linesTotal }
    private val methodsMissed = ranks(coverageByModule) { _, value -> value.methodsMissed }
    private val methodsTotal = ranks(coverageByModule) { _, value -> value.methodsTotal }
    private val classesMissed = ranks(coverageByModule) { _, value -> value.classesMissed }
    private val classesTotal = ranks(coverageByModule) { _, value -> value.classesTotal }

    fun forModule(moduleName: String): ModuleRanks = ModuleRanks(
        moduleName = moduleName.rankIn(this.moduleName),
        instructionsMissed = moduleName.rankIn(instructionsMissed),
        instructionsCoverage = moduleName.rankIn(instructionsCoverage),
        branchesMissed = moduleName.rankIn(branchesMissed),
        branchesCoverage = moduleName.rankIn(branchesCoverage),
        complexityMissed = moduleName.rankIn(complexityMissed),
        complexityTotal = moduleName.rankIn(complexityTotal),
        linesMissed = moduleName.rankIn(linesMissed),
        linesTotal = moduleName.rankIn(linesTotal),
        methodsMissed = moduleName.rankIn(methodsMissed),
        methodsTotal = moduleName.rankIn(methodsTotal),
        classesMissed = moduleName.rankIn(classesMissed),
        classesTotal = moduleName.rankIn(classesTotal),
    )

    private fun String.rankIn(ranking: Map<String, Int>): Int =
        checkNotNull(ranking[this]) { "No coverage ranking for module '$this'." }

    private fun <T : Comparable<T>> ranks(
        coverageByModule: Map<String, CoverageMetrics>,
        selector: (String, CoverageMetrics) -> T,
    ): Map<String, Int> = coverageByModule.entries
        .sortedWith(
            compareBy<Map.Entry<String, CoverageMetrics>> { selector(it.key, it.value) }
                .thenBy { it.key },
        )
        .mapIndexed { index, entry -> entry.key to index }
        .toMap()
}

internal data class ModuleRanks(
    val moduleName: Int,
    val instructionsMissed: Int,
    val instructionsCoverage: Int,
    val branchesMissed: Int,
    val branchesCoverage: Int,
    val complexityMissed: Int,
    val complexityTotal: Int,
    val linesMissed: Int,
    val linesTotal: Int,
    val methodsMissed: Int,
    val methodsTotal: Int,
    val classesMissed: Int,
    val classesTotal: Int,
)
