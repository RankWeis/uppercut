package com.rankweis.uppercut.karate.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.GutterUiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.PopupItemUiComponent
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.driver.execute
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.openFile
import com.intellij.tools.ide.performanceTesting.commands.waitForCodeAnalysisFinished
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import com.rankweis.uppercut.karate.ui.util.SMTRunnerConsoleViewRef
import com.rankweis.uppercut.karate.ui.util.getRunContentManagerRef
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
        val testCase = TestCase(IdeProductProvider.IU, LocalProjectInfo(projectDir)).useRelease()
        Starter.newContext("karate2Gutter", testCase).apply {
            val pathToPlugin = System.getProperty("path.to.build.plugin")
            PluginConfigurator(this).installPluginFromPath(Path(pathToPlugin))
        }.setupSdk(sdk).runIdeWithDriver().useDriverAndCloseIde {
            execute(
                CommandChain().openFile("src/test/java/spike/users.feature")
                    .waitForCodeAnalysisFinished()
                    .waitForSmartMode()
            )
            takeScreenshot(screenshotDir + "01-editor-open")
            ideFrame {
                clickRunTest(this)
            }
            waitForRunDescriptor(this)
            takeScreenshot(screenshotDir + "02-run-started")
            verifyConsoleResults(this)
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

    private fun clickRunTest(ideaFrameUI: IdeaFrameUI) {
        val firstGutter = ideaFrameUI.xx(
            xQuery { byType("EditorGutterComponentImpl") },
            GutterUiComponent::class.java
        ).list().first()
        val gutter = firstGutter.icons.first {
            it.mark.getTooltipText()?.contains("Run Test") ?: false
        }
        gutter.click()
        val popupItems = ideaFrameUI.xx(
            xQuery { or(byType("ActionMenuItem"), byType("ActionMenu")) },
            PopupItemUiComponent::class.java
        )
        runBlocking { waitFor { popupItems.list().isNotEmpty() } }
        popupItems.list().first().click()
    }

    private fun waitForRunDescriptor(driver: Driver) {
        driver.withContext {
            val d = this
            runBlocking {
                waitFor(timeout = 120.seconds) {
                    val project = d.getOpenProjects().first()
                    d.getRunContentManagerRef(project).getAllDescriptors().isNotEmpty()
                }
            }
        }
    }

    private fun verifyConsoleResults(driver: Driver) {
        driver.withContext {
            val descriptor = getRunContentManagerRef(driver.getOpenProjects().first())
                .getAllDescriptors().first()
            val processHandler = descriptor.getProcessHandler()
            val console = descriptor.getExecutionConsole()
            assertNotNull(console)
            assertNotNull(processHandler)
            runBlocking {
                waitFor(timeout = 4.minutes) { processHandler.isProcessTerminated() }
            }
            assertTrue(processHandler.isProcessTerminated())
            val smConsole = this.cast(console, SMTRunnerConsoleViewRef::class)
            val results = smConsole.getResultsViewer()
            driver.takeScreenshot(screenshotDir + "03-test-results")
            // users.feature: 2 scenarios + 1 called-feature scenario, all passing
            assertEquals(0, results.getFailedTestCount(), "no scenarios should fail")
            assertEquals(3, results.getFinishedTestCount(), "expected all scenarios in the tree")
        }
    }
}
