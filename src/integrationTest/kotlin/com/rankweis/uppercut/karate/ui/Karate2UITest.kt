package com.rankweis.uppercut.karate.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.GutterUiComponent
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
import com.rankweis.uppercut.karate.ui.util.RunContentDescriptor
import com.rankweis.uppercut.karate.ui.util.SMTRunnerConsoleViewRef
import com.rankweis.uppercut.karate.ui.util.SMTestProxyRef
import com.rankweis.uppercut.karate.ui.util.SMTestRunnerResultsFormRef
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
 * classpath), runs features from the editor, and verifies the test tree built from <<UPPERCUT-V2>>
 * RunListener events.
 *
 * <p>Both a passing and a failing feature run in one IDE session - booting the IDE costs ~40s, every
 * extra assertion after that is nearly free. Running twice also covers converter state leaking between
 * runs. Screenshots land under the IDE's log/screenshots directory (they capture the whole desktop).
 */
class Karate2UITest {

    private val screenshotDir = "build/reports/integrationTest/screenshots/karate2/"

    @Test
    fun runGutterTestOnKarate2Project() {
        val projectDir = prepareSpikeProjectCopy()
        val sdk = JdkDownloaderFacade.jdk21.toSdk()
        val testCase = TestCase(IdeInfo.IdeaUltimate, LocalProjectInfo(projectDir)).useRelease()
        val context = Starter.newContext("karate2Gutter", testCase).apply {
            // path.to.build.plugin points at the prepareSandbox plugin DIRECTORY, not a zip
            val pathToPlugin = System.getProperty("path.to.build.plugin")
            PluginConfigurator(this).installPluginFromDir(Path(pathToPlugin))
        }.setupSdk(sdk)
        context.runIdeWithDriver().useDriverAndCloseIde {
            openFeature(this, "src/test/java/spike/users.feature")
            takeScreenshot(screenshotDir + "01-editor-open")
            launchRunFromGutterContext(this)
            val passingRun = waitForRunDescriptor(this, "users.feature")
            takeScreenshot(screenshotDir + "02-run-started")
            verifyPassingRun(this, passingRun)

            // Second run in the same session: a failing feature. Exercises the testFailed mapping and
            // proves the converter starts clean rather than carrying node ids over from the first run.
            openFeature(this, "src/test/java/spike2/broken.feature")
            launchRunFromGutterContext(this)
            val failingRun = waitForRunDescriptor(this, "broken.feature")
            verifyFailingRun(this, failingRun)
            takeScreenshot(screenshotDir + "04-failure-results")
        }
        assertNoPluginErrorsLogged(context.paths.testHome.resolve("log").resolve("idea.log"))
    }

    /** Opens a feature and parks the caret on the Feature line so the context run targets the whole file. */
    private fun openFeature(driver: Driver, relativePath: String) {
        driver.execute(
            CommandChain().openFile(relativePath)
                .waitForCodeAnalysisFinished()
                .waitForSmartMode()
                .goto(1, 1)
        )
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
        // Waiting on indicators also waits out the Gradle import that the run config's classpath needs.
        // No focus/toFront here: nothing below touches the mouse, so the IDE can stay in the background.
        val frame = driver.ideFrame()
        frame.waitForIndicators(timeout = 10.minutes)
        // Once a run console is open, its editor contributes a second EditorGutterComponentImpl, so the
        // singleton gutter() locator fails. Look for the run marker across all gutters, re-querying while
        // waiting: line markers only appear once the file is analyzed.
        waitFor(
            timeout = 30.seconds,
            errorMessage = { "No run line marker appeared in any editor gutter" }
        ) {
            frame.xx("//div[@class='EditorGutterComponentImpl']", GutterUiComponent::class.java)
                .list()
                .any { gutter ->
                    gutter.icons.any { it.getIconPath().contains("run", ignoreCase = true) }
                }
        }
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

    /**
     * Waits for the run of [configNamePart] and attaches the output listener immediately: attaching it
     * later races with a process that dies early, leaving the failure message with no runner output.
     * Descriptors are matched by name because a second run adds to the list rather than replacing it.
     */
    private fun waitForRunDescriptor(driver: Driver, configNamePart: String): OutputListenerRef {
        return driver.withContext {
            val d = this
            runBlocking {
                waitFor(
                    timeout = 120.seconds,
                    errorMessage = { "no run descriptor named '$configNamePart' appeared" }
                ) { descriptorOrNull(d, configNamePart) != null }
            }
            val listener = d.newProcessListener() as OutputListenerRef
            descriptorOrNull(d, configNamePart)!!.getProcessHandler()?.addProcessListener(listener)
            listener
        }
    }

    private fun descriptorOrNull(driver: Driver, configNamePart: String): RunContentDescriptor? =
        driver.getRunContentManagerRef(driver.getOpenProjects().first())
            .getAllDescriptors()
            .firstOrNull { it.getDisplayName().contains(configNamePart) }

    /** Waits for the process of [configNamePart] to finish and returns the results form plus diagnostics. */
    private fun awaitResults(
        driver: Driver,
        configNamePart: String,
        outputListener: OutputListenerRef,
    ): Pair<SMTestRunnerResultsFormRef, String> {
        val descriptor = descriptorOrNull(driver, configNamePart)!!
        val processHandler = descriptor.getProcessHandler()
        val console = descriptor.getExecutionConsole()
        assertNotNull(console)
        assertNotNull(processHandler)
        runBlocking {
            waitFor(timeout = 4.minutes) { processHandler!!.isProcessTerminated() }
        }
        val results = driver.cast(console, SMTRunnerConsoleViewRef::class).getResultsViewer()
        val tree = describeTree(results.getTestsRootNode())
        println("Karate 2 test tree ($configNamePart):\n$tree")
        val output = outputListener.getOutput()
        val diagnostics = "\n--- run configuration ---\n${descriptor.getDisplayName()}\n--- test tree ---\n$tree" +
            "\n--- runner stdout ---\n${output.getStdout()}\n--- stderr ---\n${output.getStderr()}"
        return results to diagnostics
    }

    private fun verifyPassingRun(driver: Driver, outputListener: OutputListenerRef) {
        driver.withContext {
            val (results, diagnostics) = awaitResults(driver, "users.feature", outputListener)
            driver.takeScreenshot(screenshotDir + "03-test-results")
            val tree = describeTree(results.getTestsRootNode())
            // users.feature has 2 scenarios; the called feature's scenario is reported as a suite, so
            // it shows in the tree (asserted below) without counting toward the run's test total.
            assertEquals(0, results.getFailedTestCount(), "no scenarios should fail$diagnostics")
            assertEquals(2, results.getFinishedTestCount(), "expected both scenarios in the tree$diagnostics")
            // Counts alone would pass on a tree of the right size but the wrong shape.
            listOf("v2 built-ins and a nested call", "second scenario for tree ordering", "called.feature:4")
                .forEach { assertTrue(tree.contains(it), "'$it' missing from the tree$diagnostics") }

            val root = results.getTestsRootNode()
            val scenario = findNode(root, "v2 built-ins and a nested call")
            assertNotNull(scenario, "scenario node not found$diagnostics")
            // locationHint must resolve to the source .feature file (not the copy under build/resources)
            // or gutter navigation from the tree silently breaks - the #321 class of bug.
            val location = scenario!!.getLocationUrl()
            assertNotNull(location, "scenario has no location - locationHint did not resolve$diagnostics")
            assertTrue(
                location!!.contains("users.feature"),
                "scenario location '$location' should point at users.feature$diagnostics"
            )
            val called = findNode(root, "called.feature:4")
            assertTrue(called!!.isSuite(), "called scenario should be a suite, not a test$diagnostics")
        }
    }

    private fun verifyFailingRun(driver: Driver, outputListener: OutputListenerRef) {
        driver.withContext {
            val (results, diagnostics) = awaitResults(driver, "broken.feature", outputListener)
            val tree = describeTree(results.getTestsRootNode())
            assertEquals(1, results.getFailedTestCount(), "exactly the bad match should fail$diagnostics")
            assertEquals(2, results.getFinishedTestCount(), "both scenarios should finish$diagnostics")
            val failed = findNode(results.getTestsRootNode(), "failing match")
            assertNotNull(failed, "failing scenario missing from the tree$diagnostics")
            assertTrue(failed!!.isDefect(), "failing scenario should be marked failed$diagnostics")
            val error = failed.getErrorMessage()
            assertNotNull(error, "failure should carry Karate's error message$diagnostics")
            assertTrue(
                error!!.contains("match failed", ignoreCase = true),
                "error message '$error' should be Karate's match failure$diagnostics"
            )
            val passing = findNode(results.getTestsRootNode(), "passing sibling")
            assertTrue(passing!!.isPassed(), "sibling scenario should still pass$diagnostics")
        }
    }

    private fun findNode(node: SMTestProxyRef, name: String): SMTestProxyRef? {
        if (node.getPresentableName() == name || node.getName() == name) {
            return node
        }
        return node.getChildren().firstNotNullOfOrNull { findNode(it, name) }
    }

    /** The plugin must not have thrown during the session; a stack trace in idea.log fails the test. */
    private fun assertNoPluginErrorsLogged(ideaLog: Path) {
        if (!Files.exists(ideaLog)) {
            return
        }
        val errors = Files.readAllLines(ideaLog)
            .filter { it.contains("ERROR") && it.contains("com.rankweis") }
        assertTrue(errors.isEmpty(), "plugin logged errors during the session:\n${errors.joinToString("\n")}")
    }
}
