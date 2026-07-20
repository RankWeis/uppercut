package com.rankweis.uppercut.karate.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.GutterUiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.BackgroundRun
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
import com.intellij.tools.ide.performanceTesting.commands.importGradleProject
import com.intellij.tools.ide.performanceTesting.commands.openFile
import com.intellij.tools.ide.performanceTesting.commands.waitForCodeAnalysisFinished
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import com.rankweis.uppercut.karate.ui.util.OutputListenerRef
import com.rankweis.uppercut.karate.ui.util.ConsoleViewImplRef
import com.rankweis.uppercut.karate.ui.util.RunContentDescriptor
import com.rankweis.uppercut.karate.ui.util.SMTRunnerConsoleViewRef
import com.rankweis.uppercut.karate.ui.util.SMTestProxyRef
import com.rankweis.uppercut.karate.ui.util.SMTestRunnerResultsFormRef
import com.rankweis.uppercut.karate.ui.util.getRunContentManagerRef
import com.rankweis.uppercut.karate.ui.util.setKarateVersionPreference
import com.rankweis.uppercut.karate.ui.util.newProcessListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertNotNull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end check of the run paths, against the testProjects/karate-versions fixture - one project
 * holding a karate-junit6 (v2) module beside a karate-junit5 (v1) one.
 *
 * <p>Three runs share a single IDE session, because booting the IDE costs ~40s while each further run
 * is seconds: the v2 feature (tree shape, nesting, navigation), the v2 failing feature (failure
 * mapping, and no converter state carried over), and the v1 feature (the other runner entirely, which
 * only gets chosen if version detection is scoped to the run's module).
 *
 * <p>Screenshots land under the IDE's log/screenshots directory - they capture the whole desktop.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Karate2UITest {

    companion object {

        private const val SCREENSHOT_DIR = "build/reports/integrationTest/screenshots/karate2/"

        private lateinit var run: BackgroundRun
        private lateinit var ideaLog: Path

        /**
         * One IDE for the whole class - booting costs ~40s while each test's run is seconds. The
         * shared-BackgroundRun-in-companion shape is the pattern the remote-driver README documents
         * for exactly this trade-off.
         */
        @JvmStatic
        @BeforeAll
        fun startIde() {
            val projectDir = prepareSampleProjectCopy()
            val sdk = JdkDownloaderFacade.jdk21.toSdk()
            val testCase = TestCase(IdeInfo.IdeaUltimate, LocalProjectInfo(projectDir)).useRelease()
            val context = Starter.newContext("karate2Gutter", testCase).apply {
                // path.to.build.plugin points at the prepareSandbox plugin DIRECTORY, not a zip
                val pathToPlugin = System.getProperty("path.to.build.plugin")
                PluginConfigurator(this).installPluginFromDir(Path(pathToPlugin))
            }.setupSdk(sdk)
            ideaLog = context.paths.testHome.resolve("log").resolve("idea.log")
            run = context.runIdeWithDriver()
            // The startup auto-import races the project-SDK registration; when it loses, it fails
            // fast with "Invalid Gradle JDK configuration", indicators go quiet, and every run in
            // the session launches against an unimported project (empty classpath, no modules).
            // An explicit import after the IDE is up cannot lose that race - the SDK is registered
            // by now - and re-importing an already-imported project is harmless.
            run.driver.execute(CommandChain().waitForSmartMode().importGradleProject().waitForSmartMode())
        }

        @JvmStatic
        @AfterAll
        fun closeIde() {
            if (Companion::run.isInitialized) {
                run.closeIdeAndWait()
            }
        }

        /**
         * Copies testProjects/karate-versions into a temp dir and adds the repo's Gradle wrapper so
         * the IDE can import it. The wrapper distribution is already cached in ~/.gradle.
         */
        private fun prepareSampleProjectCopy(): Path {
            val repoRoot = Path(System.getProperty("user.dir"))
            val source = repoRoot.resolve("testProjects/karate-versions")
            val target = Files.createTempDirectory("karate-versions-it")
            copyRecursively(source, target)
            copyRecursively(repoRoot.resolve("gradle/wrapper"), target.resolve("gradle/wrapper"))
            // REPLACE_EXISTING: opening the fixture in an IDE by hand leaves a wrapper behind, and
            // without this the copy then fails the whole suite with FileAlreadyExistsException.
            Files.copy(
                repoRoot.resolve("gradlew"), target.resolve("gradlew"),
                StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING
            )
            Files.copy(
                repoRoot.resolve("gradlew.bat"), target.resolve("gradlew.bat"),
                StandardCopyOption.REPLACE_EXISTING
            )
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
    }

    private val screenshotDir = SCREENSHOT_DIR

    @Test
    @Order(1)
    fun v2FeaturePassesWithNavigableTree() {
        val driver = run.driver
        openFeature(driver, "v2/src/test/java/sample/users.feature")
        driver.takeScreenshot(screenshotDir + "01-editor-open")
        launchRunFromGutterContext(driver, settle = true)
        val passingRun = waitForRunDescriptor(driver, "users.feature")
        driver.takeScreenshot(screenshotDir + "02-run-started")
        verifyPassingRun(driver, passingRun)
    }

    /**
     * A failing feature in the same session: exercises the testFailed mapping and proves the
     * converter starts clean rather than carrying node ids over from the first run.
     */
    @Test
    @Order(2)
    fun v2FailingFeatureReportsTheFailure() {
        val driver = run.driver
        openFeature(driver, "v2/src/test/java/broken/broken.feature")
        launchRunFromGutterContext(driver)
        val failingRun = waitForRunDescriptor(driver, "broken.feature")
        verifyFailingRun(driver, failingRun)
        driver.takeScreenshot(screenshotDir + "04-failure-results")
    }

    /**
     * The v1 module of the same project. The v2 jars are on a sibling module's classpath, so this
     * only passes if version detection is scoped to the run's module - a project-wide scan would
     * drive the v1 module with the v2 runner and produce nothing.
     */
    @Test
    @Order(3)
    fun v1ModuleRunsThroughTheV1Runner() {
        val driver = run.driver
        openFeature(driver, "v1/src/test/java/sample/users-v1.feature")
        launchRunFromGutterContext(driver)
        val v1Run = waitForRunDescriptor(driver, "users-v1.feature")
        verifyV1Run(driver, v1Run)
        driver.takeScreenshot(screenshotDir + "05-v1-results")
    }

    /** OUTLINE_ENTER is deliberately unmapped; examples must still surface as individual results. */
    @Test
    @Order(4)
    fun scenarioOutlineExamplesAppearIndividually() {
        val driver = run.driver
        openFeature(driver, "v2/src/test/java/sample/outline.feature")
        launchRunFromGutterContext(driver)
        val outlineRun = waitForRunDescriptor(driver, "outline.feature")
        driver.withContext {
            val (results, diagnostics) = awaitResults(driver, "outline.feature", outlineRun)
            assertEquals(0, results.getFailedTestCount(), "outline examples should pass$diagnostics")
            assertEquals(
                2, results.getFinishedTestCount(),
                "each example row should be its own result$diagnostics"
            )
        }
    }

    /**
     * Editor smoke check: go-to-definition on the argument of call read('called.feature') must open
     * the called feature. This exercises the reference contributors in a real IDE - unit tests cover
     * them with LightPlatformTestCase, but not with the JavaScript plugin actually present.
     */
    @Test
    @Order(5)
    fun goToDefinitionOpensCalledFeature() {
        val driver = run.driver
        // Caret inside 'called.feature' on the call step of users.feature (line 9).
        driver.execute(
            CommandChain().openFile("v2/src/test/java/sample/users.feature")
                .waitForCodeAnalysisFinished()
                .goto(9, 40)
        )
        driver.invokeAction("GotoDeclaration")
        driver.withContext {
            val d = this
            runBlocking {
                waitFor(
                    timeout = 30.seconds,
                    errorMessage = {
                        "GotoDeclaration on 'called.feature' did not open it; current file: " +
                            currentFileName(d)
                    }
                ) { currentFileName(d) == "called.feature" }
            }
        }
    }

    @Test
    @Order(6)
    fun versionOverrideDisplacesDetection() {
        verifySettingsOverrideBeatsDetection(run.driver)
    }

    /** Runs last while the IDE is still up; idea.log is written through continuously. */
    @Test
    @Order(7)
    fun pluginLoggedNoErrors() {
        assertNoPluginErrorsLogged(ideaLog)
    }

    private fun currentFileName(driver: Driver): String? =
        driver.service(FileEditorManager::class, driver.singleProject()).getCurrentFile()?.getName()

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
     * Asserts the plugin contributes a run line marker, then launches it.
     *
     * The launch deliberately avoids clicking the icon: driver clicks are physical (screen coordinates),
     * so any window on top of the IDE swallows them ("Click was unsuccessful" in idea.log). RunClass is
     * the context-run action the gutter icon itself delegates to, so this exercises the same path:
     * KarateRunConfigurationProducer -> KarateV2TestRunner -> event converter -> test tree.
     */
    private fun launchRunFromGutterContext(driver: Driver, settle: Boolean = false) {
        // Waiting on indicators also waits out the Gradle import that the run config's classpath needs.
        // waitSmartLongEnough demands 10 quiet-and-smart seconds before returning - worth it right after
        // the import (indicators flap as indexing follows), pure dead time on every later launch.
        // No focus/toFront here: nothing below touches the mouse, so the IDE can stay in the background.
        val frame = driver.ideFrame()
        driver.waitForIndicators(timeout = 10.minutes, waitSmartLongEnough = settle)
        // Once a run console is open, its editor contributes a second EditorGutterComponentImpl, so the
        // singleton gutter() locator fails. Look for the run marker across all gutters, re-querying while
        // waiting: line markers only appear once the file is analyzed.
        // A short poll interval matters here: waitFor sleeps between checks, so the default 1s adds
        // seconds of dead time after the marker is already on screen.
        waitFor(
            timeout = 30.seconds,
            interval = 100.milliseconds,
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

    /** Everything the run printed, including the per-node output the tree attaches to scenarios. */
    private fun consoleTextOf(driver: Driver, configNamePart: String): String {
        val console = descriptorOrNull(driver, configNamePart)!!.getExecutionConsole()!!
        return driver.cast(
            driver.cast(console, SMTRunnerConsoleViewRef::class).getConsole(),
            ConsoleViewImplRef::class
        ).getText()
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
        val smConsole = driver.cast(console, SMTRunnerConsoleViewRef::class)
        val results = smConsole.getResultsViewer()
        val tree = describeTree(results.getTestsRootNode())
        println("Karate 2 test tree ($configNamePart):\n$tree")
        val output = outputListener.getOutput()
        val diagnostics = "\n--- run configuration ---\n${descriptor.getDisplayName()}\n--- test tree ---\n$tree" +
            "\n--- runner stdout ---\n${output.getStdout()}\n--- stderr ---\n${output.getStderr()}"

        // What the user actually sees in the console pane. Karate's own summary must be there; the
        // <<UPPERCUT-V2>> protocol lines must have been consumed by the converter, not printed.
        val consoleText = driver.cast(smConsole.getConsole(), ConsoleViewImplRef::class).getText()
        assertTrue(
            consoleText.contains("scenarios:"),
            "Karate's summary missing from the visible console$diagnostics\n--- console ---\n$consoleText"
        )
        assertTrue(
            !consoleText.contains("<<UPPERCUT-V2>>"),
            "v2 protocol lines leaked into the visible console$diagnostics\n--- console ---\n$consoleText"
        )
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

            // Step-level detail: v2 reports structurally, so without STEP_EXIT forwarding the tree
            // says only whether a scenario passed. The second scenario prints, and that output has
            // to reach the tree too - it lives on StepResult.getLog(), which toJson() omits.
            val consoleText = consoleTextOf(driver, "users.feature")
            assertTrue(
                consoleText.contains("karate.env"),
                "step text missing from the run output$diagnostics\n--- console ---\n$consoleText"
            )
            assertTrue(
                consoleText.contains("env is"),
                "print output missing from the run output$diagnostics\n--- console ---\n$consoleText"
            )

            val root = results.getTestsRootNode()
            val scenario = findNode(root, "v2 built-ins and a nested call")
            assertNotNull(scenario, "scenario node not found$diagnostics")
            // locationHint must resolve to the source .feature file (not the copy under build/resources)
            // or gutter navigation from the tree silently breaks - the #321 class of bug.
            val location = scenario!!.getLocationUrl()
            assertNotNull(location, "scenario has no location - locationHint did not resolve$diagnostics")
            // The generated copy under build/resources/test also matches a bare "users.feature" check,
            // and navigating there silently discards edits and breakpoints on the next build. Demand
            // the source file.
            assertTrue(
                location!!.contains("src/test/java/sample/users.feature"),
                "scenario location '$location' should point at the source users.feature, " +
                    "not the compiled copy under the build directory$diagnostics"
            )
            // Nesting a called feature under the calling scenario is v2-converter behaviour; the v1
            // converter puts called features at the top level. Together with the feature's use of
            // karate.uuid() (a v2-only builtin, which errors on v1) this identifies the runner that ran.
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

    /**
     * The v1 module of the same project: a different runner (RuntimeHook proxy), a different console
     * converter (regex over Karate's own output), and no <<UPPERCUT-V2>> protocol at all. Passing here
     * means module-scoped version detection sent this run down the v1 path despite v2 jars sitting on
     * a sibling module's classpath.
     */
    private fun verifyV1Run(driver: Driver, outputListener: OutputListenerRef) {
        driver.withContext {
            val (results, diagnostics) = awaitResults(driver, "users-v1.feature", outputListener)
            val tree = describeTree(results.getTestsRootNode())
            assertEquals(0, results.getFailedTestCount(), "no v1 scenarios should fail$diagnostics")
            assertTrue(
                results.getFinishedTestCount() >= 2,
                "expected both v1 scenarios in the tree$diagnostics"
            )
            listOf("v1 built-ins and a nested call", "second scenario for tree ordering")
                .forEach { assertTrue(tree.contains(it), "'$it' missing from the v1 tree$diagnostics") }

            // Positive proof this went down the v1 path rather than merely "not failing": the v1
            // converter builds a distinctly different tree - it adds a karate-config.js node and puts
            // called features at the top level, where the v2 converter nests them under the caller.
            assertTrue(
                tree.contains("karate-config.js"),
                "v1 tree should carry the converter's karate-config.js node$diagnostics"
            )
            assertTrue(
                findNode(results.getTestsRootNode(), "sample/called.feature") != null,
                "v1 tree should list the called feature as a top-level node$diagnostics"
            )
        }
    }

    /**
     * Settings > Tools > Karate can pin the version when detection cannot decide - a module carrying
     * both majors, for instance. The preference is read on every launch, so it takes effect without
     * restarting the IDE.
     *
     * <p>Forcing V1 on a Karate 2 module is a contradiction, and the run is refused before any process
     * starts (see KarateRunConfiguration#checkVersionOverrideMatchesClasspath). Left to run it used to
     * fail deep inside the v1 runner with a bare
     * "NoSuchMethodException: com.intuit.karate.junit5.Karate.<init>()", which says nothing about the
     * setting that caused it. No run appearing at all is the assertion: the same feature runs green
     * under AUTO seconds earlier, so only the override can explain it.
     */
    private fun verifySettingsOverrideBeatsDetection(driver: Driver) {
        driver.setKarateVersionPreference("V1")
        try {
            openFeature(driver, "v2/src/test/java/sample/override.feature")
            launchRunFromGutterContext(driver)
            // Give a run every chance to appear, so this fails loudly if the guard ever stops firing.
            runBlocking { delay(10.seconds) }
            driver.withContext {
                val descriptor = descriptorOrNull(driver, "override.feature")
                assertTrue(
                    descriptor == null,
                    "a run started despite the V1 override contradicting the module's Karate 2 " +
                        "classpath - the mismatch should be refused up front"
                )
            }
        } finally {
            driver.setKarateVersionPreference("AUTO")
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
