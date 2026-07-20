package com.rankweis.uppercut.karate.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.gutter
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.driver.execute
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.goto
import com.intellij.tools.ide.performanceTesting.commands.openFile
import com.intellij.tools.ide.performanceTesting.commands.waitForCodeAnalysisFinished
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import com.rankweis.uppercut.karate.ui.util.OutputListenerRef
import com.rankweis.uppercut.karate.ui.util.SMTRunnerConsoleViewRef
import com.rankweis.uppercut.karate.ui.util.SMTestProxyRef
import com.rankweis.uppercut.karate.ui.util.getRunContentManagerRef
import com.rankweis.uppercut.karate.ui.util.newProcessListener
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end check of the Karate 2.x path: opens the karate2-spike sample project (karate-junit6 on the
 * classpath), clicks the gutter runner on a feature, and verifies the test tree built from
 * <<UPPERCUT-V2>> RunListener events. Screenshots land in build/reports/integrationTest/screenshots/karate2/.
 */
class Karate2UITest {

    private val screenshotDir = "build/reports/integrationTest/screenshots/karate2/"

    @Test
    fun runGutterTestOnKarate2Project() {
        val projectDir = prepareSpikeProjectCopy()
        val sdk = JdkDownloaderFacade.jdk21.toSdk()
        val testCase = TestCase(IdeInfo.IdeaUltimate, LocalProjectInfo(projectDir)).useRelease()
        Starter.newContext("karate2Gutter", testCase).apply {
            // path.to.build.plugin points at the prepareSandbox plugin DIRECTORY, not a zip
            val pathToPlugin = System.getProperty("path.to.build.plugin")
            PluginConfigurator(this).installPluginFromDir(Path(pathToPlugin))
        }.setupSdk(sdk).runIdeWithDriver().useDriverAndCloseIde {
            execute(
                CommandChain().openFile("src/test/java/spike/users.feature")
                    .waitForCodeAnalysisFinished()
                    .waitForSmartMode()
                    // Caret on the Feature line: the context run action then targets the whole feature
                    // (WHOLE_FILE), not one scenario, so the tree holds both scenarios plus the called one.
                    .goto(1, 1)
            )
            takeScreenshot(screenshotDir + "01-editor-open")
            launchRunFromGutterContext(this)
            val outputListener = waitForRunDescriptor(this)
            takeScreenshot(screenshotDir + "02-run-started")
            verifyConsoleResults(this, outputListener)
        }
    }

    /**
     * Copies karate2-spike into a temp dir and adds the repo's Gradle wrapper so the IDE can import it.
     * The wrapper distribution is already cached in ~/.gradle from the host build.
     */
    private fun prepareSpikeProjectCopy(): Path {
        val repoRoot = Path(System.getProperty("user.dir"))
        val source = repoRoot.resolve("karate2-spike")
        val target = Files.createTempDirectory("karate2-spike-it")
        copyRecursively(source, target)
        copyRecursively(repoRoot.resolve("gradle/wrapper"), target.resolve("gradle/wrapper"))
        Files.copy(repoRoot.resolve("gradlew"), target.resolve("gradlew"), StandardCopyOption.COPY_ATTRIBUTES)
        Files.copy(repoRoot.resolve("gradlew.bat"), target.resolve("gradlew.bat"))
        target.resolve("gradlew").toFile().setExecutable(true)
        return target
    }

    private fun copyRecursively(source: Path, target: Path) {
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val dest = target.resolve(source.relativize(path).toString())
                if (path.isDirectory()) {
                    Files.createDirectories(dest)
                } else {
                    Files.createDirectories(dest.parent)
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    /**
     * Asserts the plugin contributes a run line marker, then launches it.
     *
     * The launch deliberately avoids clicking the icon: driver clicks are physical (screen coordinates),
     * so any window on top of the IDE swallows them ("Click was unsuccessful" in idea.log). RunClass is
     * the context-run action the gutter icon itself delegates to, so this exercises the same path:
     * KarateRunConfigurationProducer -> KarateV2TestRunner -> event converter -> test tree.
     */
    private fun launchRunFromGutterContext(driver: Driver) {
        // gutter() is the SDK's own locator; a byType("EditorGutterComponentImpl") xQuery finds nothing.
        // getGutterIcons() waits for the line markers, which only appear once the file is analyzed.
        // Waiting on indicators also waits out the Gradle import that the run config's classpath needs.
        // No focus/toFront here: nothing below touches the mouse, so the IDE can stay in the background.
        val frame = driver.ideFrame()
        frame.waitForIndicators(timeout = 10.minutes)
        val runIcons = frame.gutter().getGutterIcons()
            .filter { it.getIconPath().contains("run", ignoreCase = true) }
        assertTrue(runIcons.isNotEmpty(), "No run line marker in the gutter of users.feature")
        driver.invokeAction("RunClass")
    }

    /**
     * Waits for the run to appear and attaches the output listener immediately: attaching it later races
     * with a process that dies early, which leaves the failure message with no runner output to show.
     */
    /** Renders the SM test tree as indented text, so assertions and failures show its actual shape. */
    private fun describeTree(node: SMTestProxyRef, depth: Int = 0): String {
        val label = node.getPresentableName() ?: node.getName() ?: "<unnamed>"
        val status = when {
            node.isDefect() -> "FAILED"
            node.isPassed() -> "passed"
            else -> "not run"
        }
        val lines = mutableListOf("  ".repeat(depth) + "- $label [$status]")
        node.getChildren().forEach { lines.add(describeTree(it, depth + 1)) }
        return lines.joinToString("\n")
    }

    private fun waitForRunDescriptor(driver: Driver): OutputListenerRef {
        return driver.withContext {
            val d = this
            runBlocking {
                waitFor(timeout = 120.seconds) {
                    val project = d.getOpenProjects().first()
                    d.getRunContentManagerRef(project).getAllDescriptors().isNotEmpty()
                }
            }
            val descriptor = d.getRunContentManagerRef(d.getOpenProjects().first()).getAllDescriptors().first()
            val listener = d.newProcessListener() as OutputListenerRef
            descriptor.getProcessHandler()?.addProcessListener(listener)
            listener
        }
    }

    private fun verifyConsoleResults(driver: Driver, outputListener: OutputListenerRef) {
        driver.withContext {
            val descriptor = getRunContentManagerRef(driver.getOpenProjects().first())
                .getAllDescriptors().first()
            val processHandler = descriptor.getProcessHandler()
            val console = descriptor.getExecutionConsole()
            assertNotNull(console)
            assertNotNull(processHandler)
            val launched = descriptor.getDisplayName()
            runBlocking {
                waitFor(timeout = 4.minutes) { processHandler.isProcessTerminated() }
            }
            assertTrue(processHandler.isProcessTerminated())
            val smConsole = this.cast(console, SMTRunnerConsoleViewRef::class)
            val results = smConsole.getResultsViewer()
            driver.takeScreenshot(screenshotDir + "03-test-results")
            val output = outputListener.getOutput()
            val tree = describeTree(results.getTestsRootNode())
            println("Karate 2 test tree:\n$tree")
            val diagnostics = "\n--- run configuration ---\n$launched\n--- test tree ---\n$tree" +
                "\n--- runner stdout ---\n${output.getStdout()}\n--- stderr ---\n${output.getStderr()}"
            // users.feature has 2 scenarios; the called feature's scenario is reported as a suite, so
            // it shows in the tree (asserted below) without counting toward the run's test total.
            assertEquals(0, results.getFailedTestCount(), "no scenarios should fail$diagnostics")
            assertEquals(2, results.getFinishedTestCount(), "expected both scenarios in the tree$diagnostics")
            assertTrue(
                tree.contains("called.feature:4"),
                "called feature's scenario should still appear in the tree$diagnostics"
            )
            // Counts alone would pass on a tree of the right size but the wrong shape.
            assertTrue(
                tree.contains("v2 built-ins and a nested call"),
                "first scenario missing from the tree$diagnostics"
            )
            assertTrue(
                tree.contains("second scenario for tree ordering"),
                "second scenario missing from the tree$diagnostics"
            )
        }
    }
}
