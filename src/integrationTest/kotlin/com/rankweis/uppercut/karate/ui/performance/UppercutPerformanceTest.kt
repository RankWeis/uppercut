package com.rankweis.uppercut.karate.ui.performance

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.performanceTesting.commands.*
import com.rankweis.uppercut.karate.ui.util.Metrics.Companion.getMetricsFromSpanAndChildren
import com.rankweis.uppercut.karate.ui.util.Metrics.Companion.writeMetricsToCSV
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.time.Duration.Companion.minutes

@Disabled("Run on demand to check performance")
class UppercutPerformanceTest {
    companion object {
        @JvmStatic
        val uppercutTests = TestCase(
            IdeProductProvider.IU,
            GitHubProject.fromGithub(branchName = "main", repoRelativeUrl = "RankWeis/uppercutTestProject.git")
        ).useRelease()

        @JvmStatic
        val context = Starter.newContext(
            "openKarateProject", uppercutTests
        ).prepareProjectCleanImport().apply {
            val pathToPlugin = System.getProperty("path.to.build.plugin")
            PluginConfigurator(this).installPluginFromFolder(File(pathToPlugin))
        }

        @JvmStatic
        val noPluginContext = Starter.newContext(
            "openKarateProjectNoPlugin", uppercutTests
        ).prepareProjectCleanImport()

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            fun commands(name: String): CommandChain = CommandChain().startProfile(name)
                .importMavenProject()
                .waitForSmartMode()
                .openFile("src/test/java/nested/test.feature")
                .stopProfile()
                .exitApp()
            runTest {
                launchParallel({
                    context.runIDE(
                        commands = commands("warmupPlugin"),
                        runTimeout = 5.minutes,
                        launchName = "warmup"
                    )
                }, {
                    noPluginContext.runIDE(
                        commands = commands("warmupNoPlugin"),
                        runTimeout = 5.minutes,
                        launchName = "warmupNoPlugin"
                    )
                }).onEach {
                    writeMetricsToCSV(it, getMetricsFromSpanAndChildren( it,
                        SpanFilter.nameEquals("performance_test")) )
                }
            }
        }

        private suspend fun <T> launchParallel(pluginContext: () -> T, noPluginContext: () -> T): List<T> {
            return coroutineScope {
                listOf(async { pluginContext() }, async { noPluginContext() }).awaitAll()
            }
        }
    }

    @Test
    fun runPerformanceCheck() = runTest {
        var pathToStats: Path? = null
        val configure: IDERunContext.() -> Unit = {
            addVMOptionsPatch {
                val statsJson: Path = getStartupStatsJson()
                pathToStats = statsJson
                addSystemProperty("idea.record.classloading.stats", true)
                addSystemProperty("idea.log.perf.stats.file", statsJson)
            }
        }

        fun commands(name: String): CommandChain =
            CommandChain().startProfile(name).waitForCodeAnalysisFinished().stopProfile().delay(1000).exitApp()
        launchParallel({
            context.runIDE(
                commands = commands("startupPlugin"),
                runTimeout = 5.minutes,
                launchName = "startup",
                configure = configure
            )
        }, {
            noPluginContext.runIDE(
                commands = commands("startupNoPlugin"),
                runTimeout = 5.minutes,
                launchName = "startupNoPlugin",
                configure = configure
            )
        }).onEach {
            writeMetricsToCSV(it, getMetricsFromSpanAndChildren( it,
                SpanFilter.nameEquals("performance_test")) )
        }
        println("Report at ${pathToStats?.toUri().toString()}")
    }

    private fun IDERunContext.getStartupStatsJson(): Path = reportsDir / "startup" / "startup-stats.json"

}