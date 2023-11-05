package com.azizutku.jacocoaggregatecoverageplugin.models

private const val MAXIMUM_WIDTH_FOR_PROGRESS_BARS = 120

/**
 * Represents a row in the aggregated coverage report, detailing module coverage metrics.
 */
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

        /**
         * Generates a coverage row for a given module, including progress bars.
         *
         * @param moduleName Name of the module.
         * @param moduleCoverage Coverage metrics for the module.
         * @param maxInstructionTotal The highest number of total instructions across all modules.
         * @param maxBranchesTotal The highest number of total branches across all modules.
         * @param subprojectToCoverageMap Map of module names to their coverage metrics.
         * @return A [ModuleCoverageRow] containing coverage data and HTML progress bar elements.
         */
        @Suppress("LongMethod")
        fun generateModuleCoverageRow(
            moduleName: String,
            moduleCoverage: CoverageMetrics,
            maxInstructionTotal: Int,
            maxBranchesTotal: Int,
            subprojectToCoverageMap: Map<String, CoverageMetrics>,
        ): ModuleCoverageRow = ModuleCoverageRow(
            instructionsCoverage = CoverageMetrics.calculateCoveragePercentage(
                missed = moduleCoverage.instructionsMissed,
                total = moduleCoverage.instructionsTotal,
            ),
            branchesCoverage = CoverageMetrics.calculateCoveragePercentage(
                missed = moduleCoverage.branchesMissed,
                total = moduleCoverage.branchesTotal,
            ),
            moduleNameOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) {
                moduleName
            },
            instructionsMissedOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) {
                it.instructionsMissed
            },
            instructionMissedRedProgressBar = getProgressBarHtml(
                value = moduleCoverage.instructionsMissed,
                maxValue = maxInstructionTotal,
                color = "red",
            ),
            instructionMissedGreenProgressBar = getProgressBarHtml(
                value = moduleCoverage.instructionsTotal - moduleCoverage.instructionsMissed,
                maxValue = maxInstructionTotal,
                color = "green",
            ),
            instructionsCoverageOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) {
                it.instructionsCoverage
            },
            branchesMissedOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) { metrics ->
                metrics.branchesMissed
            },
            branchesMissedRedProgressBar = getProgressBarHtml(
                value = moduleCoverage.branchesMissed,
                maxValue = maxBranchesTotal,
                color = "red",
            ),
            branchesMissedGreenProgressBar = getProgressBarHtml(
                value = moduleCoverage.branchesTotal - moduleCoverage.branchesMissed,
                maxValue = maxBranchesTotal,
                color = "green",
            ),
            branchesCoverageOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) {
                it.branchesCoverage
            },
            complexityMissedOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) {
                it.complexityMissed
            },
            complexityTotalOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) {
                it.complexityTotal
            },
            linesMissedOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) {
                it.linesMissed
            },
            linesTotalOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) {
                it.linesTotal
            },
            methodsMissedOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) {
                it.methodsMissed
            },
            methodsTotalOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) {
                it.methodsTotal
            },
            classesMissedOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) {
                it.classesMissed
            },
            classesTotalOrder = subprojectToCoverageMap.findOrderOfProperty(moduleName) {
                it.classesTotal
            },
        )

        /**
         * Finds the ordinal index of a property in a sorted list of coverage metrics.
         * Used to determine the order of modules based on a specific coverage metric.
         *
         * @param key The key of the coverage metric to find.
         * @param selector A lambda to select the property used for sorting.
         * @return The index of the property in the sorted list.
         */
        private fun <T : Comparable<T>> Map<String, CoverageMetrics>.findOrderOfProperty(
            key: String,
            selector: (CoverageMetrics) -> T
        ): Int {
            val sortedEntries = entries.sortedBy { selector(it.value) }
            return sortedEntries.indexOfFirst { it.key == key }
        }

        /**
         * Generates an HTML snippet for a progress bar.
         * This snippet represents the coverage as a visual bar in the report.
         *
         * @param value The value represented by the progress bar.
         * @param maxValue The maximum possible value for scaling the progress bar width.
         * @param color The color of the progress bar (e.g., "red", "green").
         * @return An HTML string representing the progress bar.
         */
        private fun getProgressBarHtml(value: Int, maxValue: Int, color: String): String {
            val widthPercentage =
                (value.toFloat() / maxValue * MAXIMUM_WIDTH_FOR_PROGRESS_BARS).toInt()
            return "<img src='jacoco-resources/${color}bar.gif' width='$widthPercentage' " +
                "height='10' title='$value' alt='$value' />"
        }
    }
}
