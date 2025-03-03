package com.rankweis.uppercut.karate.ui

import SMTRunnerConsoleViewRef
import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.ui.components.common.GutterIcon
import com.intellij.driver.sdk.ui.components.common.gutter
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.PopupItemUiComponent
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.driver.remoteDev.RemDevDriverRunner
import com.intellij.ide.starter.driver.engine.DriverRunner
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.driver.execute
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.runner.RemDevTestContainer
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import com.intellij.testFramework.common.waitUntil
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.openFile
import com.intellij.tools.ide.performanceTesting.commands.waitForCodeAnalysisFinished
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import getRunContentManagerRef
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.kodein.di.DI
import org.kodein.di.bindProvider
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes


class UppercutUITest {

    object IdeaUltimateCases : TestCaseTemplate(IdeProductProvider.IU) {
        val IntellijKarateTestCase = withProject(
            GitHubProject.fromGithub(branchName = "main", repoRelativeUrl = "RankWeis/uppercutTestProject.git")
        ).useEAP()
    }

    @ParameterizedTest(name = "split-mode={0}")
    @ValueSource(booleans = [false, true])
    fun runGutterTest(splitMode: Boolean = false) {
        if (splitMode) {
            di = DI {
                extend(di)
                bindProvider<TestContainer<*>>(overrides = true) { TestContainer.newInstance<RemDevTestContainer>() }
                bindProvider<DriverRunner> { RemDevDriverRunner() }
            }
        }
        val sdk = JdkDownloaderFacade.jdk21.toSdk()
        Starter.newContext(
            "runGutter-" + if (splitMode) "split-mode" else "no-split-mode",
            IdeaUltimateCases.IntellijKarateTestCase
        ).prepareProjectCleanImport().apply {
            val pathToPlugin = System.getProperty("path.to.build.plugin")
            PluginConfigurator(this).installPluginFromPath(Path(pathToPlugin))
        }.setupSdk(sdk).runIdeWithDriver().useDriverAndCloseIde {
            execute(
                CommandChain().openFile("src/test/java/nested/test.feature")
                    .waitForCodeAnalysisFinished()
                    .waitForSmartMode()
            )
            ideFrame {
                val gutter = gutter().getGutterIcons().first { it.getIconPath() == GutterIcon.RERUN.path }
                assertEquals(0, gutter.line)
                gutter.click()
                val popupItems = xx(
                    "//div[@class='ActionMenuItem' or @class='ActionMenu']",
                    PopupItemUiComponent::class.java
                )
                runBlocking { waitUntil { popupItems.list().isNotEmpty() } }
                popupItems
                    .list().first().click()
            }
            runTests(this)
            verifyConsoleResults(this)
        }
    }

    private fun runTests(driver: Driver) {
        driver.withContext {
            val driver2 = this
            runBlocking {
                waitUntil {
                    waitForProjectOpen(1.minutes)
                    val project = driver2.getOpenProjects().first()
                    driver2.getRunContentManagerRef(project).getAllDescriptors().isNotEmpty()
                }
            }
        }
    }

    // Helper to attach a listener to the current console output
    private fun verifyConsoleResults(driver: Driver) {
        driver.withContext {
            val descriptor = getRunContentManagerRef(driver.getOpenProjects().first())
                .getAllDescriptors().first()
            val processHandler = descriptor.getProcessHandler()
            val instance = descriptor.getExecutionConsole()
            assertNotNull(instance)
            assertNotNull(processHandler)
            runBlocking {
                waitUntil(timeout = 1.minutes) { processHandler.isProcessTerminated() }
            }
            val base = this.cast(instance, SMTRunnerConsoleViewRef::class)
            val results = base.getResultsViewer()
            assertEquals(0, results.getFailedTestCount())
            assertEquals(0, results.getIgnoredTestCount())
            assertEquals(6, results.getFinishedTestCount())
        }
    }
}