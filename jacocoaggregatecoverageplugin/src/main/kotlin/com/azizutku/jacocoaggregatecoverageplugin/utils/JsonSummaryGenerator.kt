package com.azizutku.jacocoaggregatecoverageplugin.utils

import com.azizutku.jacocoaggregatecoverageplugin.models.CoverageMetrics

private const val SCHEMA_VERSION = 1
private const val CONTROL_CHARACTER_LIMIT = 0x20
private const val UNICODE_ESCAPE_LENGTH = 4
private const val HEX_RADIX = 16

internal class JsonSummaryGenerator {
    fun generate(
        totalCoverage: CoverageMetrics,
        coverageByModule: Map<String, CoverageMetrics>,
        reportPath: (String) -> String,
    ): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": $SCHEMA_VERSION,")
        append("  \"totals\": ")
        appendCounters(totalCoverage, "  ")
        appendLine(",")
        appendLine("  \"modules\": [")
        coverageByModule.toSortedMap().entries.forEachIndexed { index, (modulePath, coverage) ->
            appendLine("    {")
            appendLine("      \"path\": ${modulePath.toJsonString()},")
            appendLine("      \"reportPath\": ${reportPath(modulePath).toJsonString()},")
            append("      \"counters\": ")
            appendCounters(coverage, "      ")
            appendLine()
            append("    }")
            if (index != coverageByModule.size - 1) {
                append(',')
            }
            appendLine()
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun StringBuilder.appendCounters(metrics: CoverageMetrics, indent: String) {
        val counters = listOf(
            "instructions" to Counter(metrics.instructionsMissed, metrics.instructionsTotal),
            "branches" to Counter(metrics.branchesMissed, metrics.branchesTotal),
            "complexity" to Counter(metrics.complexityMissed, metrics.complexityTotal),
            "lines" to Counter(metrics.linesMissed, metrics.linesTotal),
            "methods" to Counter(metrics.methodsMissed, metrics.methodsTotal),
            "classes" to Counter(metrics.classesMissed, metrics.classesTotal),
        )
        appendLine("{")
        counters.forEachIndexed { index, (name, counter) ->
            appendLine("$indent  \"$name\": {")
            appendLine("$indent    \"missed\": ${counter.missed},")
            appendLine("$indent    \"covered\": ${counter.covered},")
            appendLine("$indent    \"total\": ${counter.total}")
            append("$indent  }")
            if (index != counters.lastIndex) {
                append(',')
            }
            appendLine()
        }
        append("$indent}")
    }

    private fun String.toJsonString(): String = buildString {
        append('"')
        this@toJsonString.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> appendEscapedControlCharacterOrSelf(character)
            }
        }
        append('"')
    }

    private fun StringBuilder.appendEscapedControlCharacterOrSelf(character: Char) {
        if (character.code < CONTROL_CHARACTER_LIMIT) {
            append("\\u")
            append(character.code.toString(HEX_RADIX).uppercase().padStart(UNICODE_ESCAPE_LENGTH, '0'))
        } else {
            append(character)
        }
    }

    private data class Counter(val missed: Long, val total: Long) {
        init {
            require(missed in 0..total) {
                "Coverage counter must satisfy 0 <= missed <= total, but was $missed of $total."
            }
        }

        val covered: Long = total - missed
    }
}
