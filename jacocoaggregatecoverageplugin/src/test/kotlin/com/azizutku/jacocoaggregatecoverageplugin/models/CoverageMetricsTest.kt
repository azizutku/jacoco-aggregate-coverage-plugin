package com.azizutku.jacocoaggregatecoverageplugin.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CoverageMetricsTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `parses counters with thousands separators from a JaCoCo summary`() {
        val report = temporaryDirectory.resolve("index.html")
        Files.writeString(
            report,
            """
                <html><body><table><tfoot><tr>
                  <td>Total</td><td>1,234 of 2,000</td><td>38%</td>
                  <td>10 of 40</td><td>75%</td>
                  <td>3</td><td>10</td><td>4</td><td>20</td>
                  <td>5</td><td>12</td><td>1</td><td>7</td>
                </tr></tfoot></table></body></html>
            """.trimIndent(),
        )

        val metrics = requireNotNull(CoverageMetrics.parseModuleCoverageMetrics(report.toFile()))

        assertEquals(1_234L, metrics.instructionsMissed)
        assertEquals(2_000L, metrics.instructionsTotal)
        assertEquals(10L, metrics.branchesMissed)
        assertEquals(40L, metrics.branchesTotal)
        assertEquals(3L, metrics.complexityMissed)
        assertEquals(7L, metrics.classesTotal)
        assertEquals("38%", CoverageMetrics.calculateCoveragePercentage(1_234, 2_000))
    }

    @Test
    fun `returns n-a when a counter has no elements`() {
        assertEquals("n/a", CoverageMetrics.calculateCoveragePercentage(0, 0))
    }
}
