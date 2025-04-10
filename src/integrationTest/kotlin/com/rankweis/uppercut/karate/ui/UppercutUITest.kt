package com.rankweis.uppercut.karate.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.ui.components.common.*
import com.intellij.driver.sdk.ui.components.elements.PopupItemUiComponent
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.driver.execute
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import com.intellij.tools.ide.performanceTesting.commands.*
import com.rankweis.uppercut.karate.ui.util.SMTRunnerConsoleViewRef
import com.rankweis.uppercut.karate.ui.util.getRunContentManagerRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class UppercutUITest {

    object IdeaUltimateCases : TestCaseTemplate(IdeProductProvider.IU) {
        val IntellijKarateTestCase = withProject(
            GitHubProject.fromGithub(branchName = "main", repoRelativeUrl = "RankWeis/uppercutTestProject.git")
        ).useRelease()
    }

    @Test
    fun runGutterTest() {
        val sdk = JdkDownloaderFacade.jdk21.toSdk()
        Starter.newContext(
            "runGutter",
            IdeaUltimateCases.IntellijKarateTestCase
        ).prepareProjectCleanImport().apply {
            val pathToPlugin = System.getProperty("path.to.build.plugin")
            PluginConfigurator(this).installPluginFromFolder(File(pathToPlugin))
        }.setupSdk(sdk).runIdeWithDriver().useDriverAndCloseIde {
            execute(
                CommandChain().openFile("src/test/java/nested/test.feature")
                    .waitForCodeAnalysisFinished()
                    .waitForSmartMode()
            )
            ideFrame {
                clickRunTest(this)
            }
            runTests(this)
            verifyConsoleResults(this)
            ideFrame {
                val frame = this;
                this.codeEditor {
                    this.text = this.text.replace("1e9", "1")
                    clickRunTest(frame)
                }
            }
            runTests(this, 3000.milliseconds)
            verifyConsoleResults(this, 1)
            execute(
                CommandChain().openFile("src/test/java/karate-config.js")
            )
            ideFrame {
                this.codeEditor {
                    this.text = this.text.replace("// Java.type", "Java.type")
                }
            }
            execute(
                CommandChain().openFile("src/test/java/nested/test.feature")
            )
            ideFrame {
                val frame = this;
                this.codeEditor {
                    clickRunTest(frame)
                }
            }
            runTests(this, 3000.milliseconds)
            verifyConsoleResults(this, 2, 0, 1)
        }
    }

    private fun clickRunTest(ideaFrameUI: IdeaFrameUI) {
        val firstGutter = ideaFrameUI.xx("//div[@class='EditorGutterComponentImpl']", GutterUiComponent::class.java)
            .list().first();
        val gutter = firstGutter.icons.first {
            it.mark.getTooltipText()?.contains("Run Test") ?: false
        }

        assertEquals(0, gutter.line)
        gutter.click()
        val popupItems = ideaFrameUI.xx(
            "//div[@class='ActionMenuItem' or @class='ActionMenu']",
            PopupItemUiComponent::class.java
        )
        runBlocking { waitFor { popupItems.list().isNotEmpty() } }
        popupItems
            .list().first().click()
    }

    private fun runTests(driver: Driver, delay: Duration = 0.seconds) {
        driver.withContext {
            val driver2 = this
            runBlocking {
                delay(delay)
                waitFor(timeout = 90.seconds) {
                    val project = driver2.getOpenProjects().first()
                    driver2.getRunContentManagerRef(project).getAllDescriptors().isNotEmpty()
                }
            }
        }
    }

    // Helper to attach a listener to the current console output
    private fun verifyConsoleResults(driver: Driver, failedCount: Int = 0, ignoredCount: Int = 0, totalCount: Int = 6) {
        driver.withContext {
            val descriptor = getRunContentManagerRef(driver.getOpenProjects().first())
                .getAllDescriptors().first()
            val processHandler = descriptor.getProcessHandler()
            val instance = descriptor.getExecutionConsole()
            assertNotNull(instance)
            assertNotNull(processHandler)
            runBlocking {
                waitFor(timeout = 2.minutes) { processHandler.isProcessTerminated() }
            }
            assertTrue(processHandler.isProcessTerminated())
            val base = this.cast(instance, SMTRunnerConsoleViewRef::class)
            val results = base.getResultsViewer()
            driver.takeScreenshot("build/reports/tests/screenshots/")
            assertEquals(failedCount, results.getFailedTestCount())
            assertEquals(ignoredCount, results.getIgnoredTestCount())
            assertEquals(totalCount, results.getFinishedTestCount())
        }
    }
}