package com.rankweis.uppercut.karate.junit5.ui

import OutputListenerRef
import ProcessListenerRef
import RunContentManagerRef
import State
import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.ui.components.common.GutterIcon
import com.intellij.driver.sdk.ui.components.common.gutter
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.PopupItemUiComponent
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.runner.RemDevTestContainer
import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import com.intellij.testFramework.common.waitUntil
import com.intellij.tools.ide.performanceTesting.commands.*
import com.rankweis.uppercut.karate.junit5.ui.performance.UppercutPerformanceTest
import getRunContentManagerRef
import kotlinx.coroutines.*
import newProcessListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.rmi.registry.LocateRegistry
import java.util.logging.Logger
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


@Disabled
class UppercutUITest {
    object IdeaUltimateCases : TestCaseTemplate(IdeProductProvider.IU) {
        val IntellijKarateTestCase = withProject(
            GitHubProject.fromGithub(branchName = "main", repoRelativeUrl = "RankWeis/uppercutTestProject.git")
        ).useEAP()
    }

    companion object {
        @JvmStatic
        val logger: Logger = Logger.getLogger(UppercutPerformanceTest::class.java.name)
    }

    @Test
    fun runGutterTest() {
        val sdk = JdkDownloaderFacade.jdk21.toSdk()
        LocateRegistry.createRegistry(1099)
        val remDevTestContainer = RemDevTestContainer()
        val context = remDevTestContainer.newContext("openKarateProject", IdeaUltimateCases.IntellijKarateTestCase)
            .prepareProjectCleanImport().apply {
                val pathToPlugin = System.getProperty("path.to.build.plugin")
                PluginConfigurator(this).installPluginFromPath(Path(pathToPlugin))
            }
        remDevTestContainer.setupHooks.add { context }
        context.runIDE(
            commands = CommandChain().setupProjectSdk(sdk).openFile("src/test/java/nested/test.feature")
                .waitForSmartMode().exitApp(),
            runTimeout = 2.minutes,
            launchName = "WARMUP",
            configure = {
                addVMOptionsPatch {
                    addSystemProperty("com.sun.management.jmxremote.rmi.port", "7777")
//                    addSystemProperty("com.sun.management.jmxremote.port", "7777")
//                    addSystemProperty("com.sun.management.jmxremote", "true")
                }
            }
        )
        context.runIdeWithDriver().useDriverAndCloseIde {
            val driver = this
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
            runBlocking {
                val passed = async(Dispatchers.Unconfined) {
                    runTestsAndGetPassed(driver)
                }.await()
                assertEquals(4, passed) { "Expected 4 test to pass, but $passed passed" }
            }
        }
        Thread.sleep(30.minutes.inWholeMilliseconds)
    }

    private suspend fun runTestsAndGetPassed(driver: Driver) {
        var totalPass = 0
        var totalRead = 0
        driver.withContext {
            waitForProjectOpen(5.seconds)
        }
        val project = driver.getOpenProjects().first()
        runBlocking {
            waitUntil {
                driver.getRunContentManagerRef(project).getAllDescriptors().isNotEmpty()
            }
            driver.withContext {
                val newProcessListener = newProcessListener(project)
                val runContentManagerRef = getRunContentManagerRef(project)
                captureConsoleOutput(runContentManagerRef, newProcessListener)
            }
        }
        withTimeout(1.minutes) {
            while (totalPass < 4) {
                delay(1.seconds)
                State.stdout.substring(totalRead)
                    .also {
                        totalRead += it.length
                        if (it.isNotEmpty()) {
                            println("LOGS $it")
                            totalPass += it.split("\n").count {
                                it.contains("Scenario name: Test with edge-case data inputs, featureFileName: nested/test.feature")
                                        && it.endsWith("FINISH")
                            }
                        }
                    }
            }
            currentCoroutineContext().cancel()
        }
        logger.info("Total passed: $totalPass")
    }

    // Helper to attach a listener to the current console output
    fun captureConsoleOutput(
        runContentManager: RunContentManagerRef,
        listener: ProcessListenerRef
    ) {
        val descriptors = runContentManager.getAllDescriptors()
        descriptors.map { descriptor ->
            val processHandler = descriptor.getProcessHandler()
            if (processHandler == null) {
                throw RuntimeException("ProcessHandler is null for descriptor: ${descriptor.getDisplayName()}")
            }
            try {
                processHandler.addProcessListener(listener)
            } catch (e: Exception) {
                logger.severe("Error attaching listener to process handler for ${descriptor.getDisplayName()}: ${e.message}")
                throw RuntimeException(e)
            }
            return@map (listener as OutputListenerRef).getOutput()
        }
    }
}