package com.azizutku.jacocoaggregatecoverageplugin.utils

import com.azizutku.jacocoaggregatecoverageplugin.models.CoverageMetrics
import groovy.json.JsonSlurper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private typealias JsonObject = Map<String, Any?>

class JsonSummaryGeneratorTest {
    @Test
    fun `generates the versioned summary schema with exact counters`() {
        val alpha = CoverageMetrics(
            instructionsMissed = 2,
            instructionsTotal = 10,
            branchesMissed = 1,
            branchesTotal = 4,
            complexityMissed = 2,
            complexityTotal = 5,
            linesMissed = 3,
            linesTotal = 12,
            methodsMissed = 1,
            methodsTotal = 6,
            classesMissed = 0,
            classesTotal = 2,
        )
        val beta = CoverageMetrics(
            instructionsMissed = 1,
            instructionsTotal = 5,
            linesMissed = 1,
            linesTotal = 3,
        )

        val json = JsonSummaryGenerator().generate(
            totalCoverage = alpha + beta,
            coverageByModule = linkedMapOf(":beta" to beta, ":alpha" to alpha),
            reportPath = { path -> "${path.removePrefix(":")}/index.html" },
        )
        val summary = json.parseJsonObject()

        assertEquals(setOf("schemaVersion", "totals", "modules"), summary.keys)
        assertEquals(1, (summary.getValue("schemaVersion") as Number).toInt())
        assertCounter(summary.counters("totals", "instructions"), missed = 3, covered = 12, total = 15)
        assertCounter(summary.counters("totals", "lines"), missed = 4, covered = 11, total = 15)

        val modules = summary.getValue("modules") as List<*>
        assertEquals(listOf(":alpha", ":beta"), modules.map { it.asObject().getValue("path") })
        val alphaSummary = modules.first().asObject()
        assertEquals(setOf("path", "reportPath", "counters"), alphaSummary.keys)
        assertEquals("alpha/index.html", alphaSummary.getValue("reportPath"))
        assertCounter(alphaSummary.counters("counters", "branches"), missed = 1, covered = 3, total = 4)
        assertCounter(alphaSummary.counters("counters", "classes"), missed = 0, covered = 2, total = 2)
        assertEquals(
            listOf("instructions", "branches", "complexity", "lines", "methods", "classes"),
            (summary.getValue("totals") as Map<*, *>).keys.toList(),
        )
        assertTrue(json.endsWith("\n"), json)
    }

    @Test
    fun `escapes JSON strings and preserves unicode`() {
        val modulePath = ":özellik:\"ödeme\\satırı\n"

        val json = JsonSummaryGenerator().generate(
            totalCoverage = CoverageMetrics(),
            coverageByModule = mapOf(modulePath to CoverageMetrics()),
            reportPath = { "özellik/ödeme/index.html" },
        )
        val module = (json.parseJsonObject().getValue("modules") as List<*>).single().asObject()

        assertEquals(modulePath, module.getValue("path"))
        assertEquals("özellik/ödeme/index.html", module.getValue("reportPath"))
    }

    @Test
    fun `rejects inconsistent counters`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            JsonSummaryGenerator().generate(
                totalCoverage = CoverageMetrics(linesMissed = 2, linesTotal = 1),
                coverageByModule = emptyMap(),
                reportPath = { "unused" },
            )
        }

        assertTrue(error.message.orEmpty().contains("0 <= missed <= total"), error.message)
    }

    private fun assertCounter(counter: JsonObject, missed: Long, covered: Long, total: Long) {
        assertEquals(setOf("missed", "covered", "total"), counter.keys)
        assertEquals(missed, (counter.getValue("missed") as Number).toLong())
        assertEquals(covered, (counter.getValue("covered") as Number).toLong())
        assertEquals(total, (counter.getValue("total") as Number).toLong())
    }

    private fun JsonObject.counters(container: String, counter: String): JsonObject =
        getValue(container).asObject().getValue(counter).asObject()

    private fun String.parseJsonObject(): JsonObject = JsonSlurper().parseText(this).asObject()

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asObject(): JsonObject = this as JsonObject
}
