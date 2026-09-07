package com.rankweis.uppercut.karate.ui.util

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.Project

/**
 * Enough of the run-configuration API to launch one the test has changed.
 *
 * A context run (the gutter's `DebugClass`) builds the configuration itself, so there is no way to
 * tick an option before the first launch. What there is: the configuration it built stays selected,
 * so a test can reach it afterwards, set the option and launch it again - which is also what a user
 * does, having run once and then wanted the JVM debugger too.
 */
@Remote("com.intellij.execution.RunManager")
interface RunManagerRef {
    fun getSelectedConfiguration(): RunnerAndConfigurationSettingsRef?
}

@Remote("com.intellij.execution.RunnerAndConfigurationSettings")
interface RunnerAndConfigurationSettingsRef {
    fun getName(): String

    /**
     * Typed as the plugin's own configuration: driver refs are structural, so this is a proxy over
     * whatever object is really there. Assert the name is a Karate run before calling it.
     */
    fun getConfiguration(): KarateRunConfigurationRef
}

@Remote("com.rankweis.uppercut.karate.run.KarateRunConfiguration", plugin = "com.rankweis")
interface KarateRunConfigurationRef {
    fun isAttachJvmDebugger(): Boolean
    fun setAttachJvmDebugger(attach: Boolean)
}

@Remote("com.intellij.execution.ProgramRunnerUtil")
interface ProgramRunnerUtilRef {
    fun executeConfiguration(settings: RunnerAndConfigurationSettingsRef, executor: ExecutorRef)
}

@Remote("com.intellij.execution.Executor")
interface ExecutorRef

@Remote("com.intellij.execution.executors.DefaultDebugExecutor")
interface DefaultDebugExecutorRef {
    fun getDebugExecutorInstance(): ExecutorRef
}

fun Driver.runManager(project: Project): RunManagerRef =
    service(RunManagerRef::class, project, RdTarget.DEFAULT)

/** Launches a configuration under Debug, the same call the plugin makes for its own second tab. */
fun Driver.debugConfiguration(settings: RunnerAndConfigurationSettingsRef) {
    utility(ProgramRunnerUtilRef::class).executeConfiguration(
        settings,
        utility(DefaultDebugExecutorRef::class).getDebugExecutorInstance()
    )
}
