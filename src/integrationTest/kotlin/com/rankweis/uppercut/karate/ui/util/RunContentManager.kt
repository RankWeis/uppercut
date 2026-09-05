package com.rankweis.uppercut.karate.ui.util
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.ProcessHandlerRef
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.ui.remote.AccessibleContextRef
import org.jetbrains.annotations.NotNull

object State {
    @JvmStatic
    val stdout = StringBuilder()

    @JvmStatic
    val stderr = StringBuilder()
}

fun Driver.getRunContentManagerRef(project: Project): RunContentManagerRef =
    service(RunContentManagerRef::class, project, RdTarget.DEFAULT)

fun Driver.newProcessListener(): ProcessListenerRef =
    new(OutputListenerRef::class, State.stdout, State.stderr)


object StateHolder {
    @JvmStatic
    val logs = StringBuilder()
}

@Remote("com.intellij.execution.ui.RunContentManager")
interface RunContentManagerRef {
    fun getAllDescriptors(): List<RunContentDescriptor>
}

@Remote("com.intellij.execution.ui.RunContentDescriptor")
interface RunContentDescriptor {
    fun getDisplayName(): String
    fun getProcessHandler(): ProcessHandler?
    fun getExecutionConsole(): ExecutionConsoleRef?
}

// The test runner classes are no longer in the core classloader: they ship in the implementation-detail
// plugin `intellij.testRunner.plugin`, so @Remote has to name the owning plugin/module or the driver
// answers "No such class ... in plugin null".
private const val TEST_RUNNER_MODULE = "intellij.testRunner.plugin/intellij.platform.testRunner"
private const val SM_RUNNER_MODULE = "intellij.testRunner.plugin/intellij.platform.smRunner"

@Remote("com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView", plugin = TEST_RUNNER_MODULE)
interface BaseTestConsoleViewRef : ExecutionConsoleRef {
    fun getConsole(): ConsoleViewRef
    fun getPrinter(): TestsOutputConsolePrinterRef;
}

@Remote("com.intellij.execution.testframework.ui.TestsOutputConsolePrinter", plugin = TEST_RUNNER_MODULE)
interface TestsOutputConsolePrinterRef {
    fun getConsole(): ConsoleViewRef
}

@Remote("com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView", plugin = SM_RUNNER_MODULE)
interface SMTRunnerConsoleViewRef : ConsoleViewRef {
    fun getConsole(): ConsoleViewRef
    fun getPrinter(): TestsOutputConsolePrinterRef;
    fun getResultsViewer(): SMTestRunnerResultsFormRef
}

@Remote("com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm", plugin = SM_RUNNER_MODULE)
interface SMTestRunnerResultsFormRef {
    fun getIgnoredTestCount(): Int
    fun getFinishedTestCount(): Int
    fun getFailedTestCount(): Int

    /** Root of the test tree; walk [SMTestProxyRef.getChildren] to assert on node names and status. */
    fun getTestsRootNode(): SMTestProxyRef
}

/** A node in the test tree - a feature (suite) or a scenario (test), depending on depth. */
@Remote("com.intellij.execution.testframework.sm.runner.SMTestProxy", plugin = SM_RUNNER_MODULE)
interface SMTestProxyRef {
    fun getName(): String?
    fun getPresentableName(): String?
    fun getChildren(): List<SMTestProxyRef>
    fun isPassed(): Boolean
    fun isDefect(): Boolean
    fun isSuite(): Boolean
    fun getErrorMessage(): String?
    fun getStacktrace(): String?

    /** The locationHint we emitted, resolved by the framework - "file://<path>:<line>" when navigable. */
    fun getLocationUrl(): String?
}

/** Console text, for asserting on what the run actually printed. */
@Remote("com.intellij.execution.impl.ConsoleViewImpl")
interface ConsoleViewImplRef {
    fun getText(): String
}

@Remote("com.intellij.execution.ui.ConsoleView")
interface ConsoleViewRef : ExecutionConsoleRef {
}

@Remote("com.intellij.execution.ui.ExecutionConsole")
interface ExecutionConsoleRef {
    fun getComponent(): JComponentRef;
}

@Remote("javax.swing.JTextArea")
interface JTextAreaRef {
    fun getText(): String;

}

@Remote("javax.swing.JComponent")
interface JComponentRef {
    fun getAccessibleContext(): AccessibleContextRef;
}

@Remote("com.intellij.execution.process.ProcessHandler")
interface ProcessHandler {
    fun isProcessTerminated(): Boolean
    fun isProcessTerminating(): Boolean
    fun waitFor(millis: Long): Boolean
    fun addProcessListener(listener: ProcessListenerRef)
    fun addProcessListener(listener: ProcessListenerRef, parentDisposable: DisposableRef)
}

@Remote("com.intellij.openapi.Disposable")
interface DisposableRef

@Remote("com.intellij.execution.process.ProcessAdapter")
interface ProcessAdapterRef : ProcessListenerRef

@Remote("java.util.EventListener")
interface EventListenerRef

@Remote("com.intellij.execution.process.ProcessListener")
interface ProcessListenerRef : EventListenerRef {
    fun onTextAvailable(event: ProcessEventRef, outputType: KeyRef<*>)

    fun startNotified(event: ProcessEventRef)

    fun processTerminated(event: ProcessEventRef)

    fun processWillTerminate(event: ProcessEventRef, willBeDestroyed: Boolean)

    fun processNotStarted()
}

@Remote("com.intellij.execution.process.ProcessEvent")
interface ProcessEventRef {
    fun getProcessHandler(): ProcessHandlerRef

    fun getText(): String

    fun getExitCode(): Int
}

@Remote("com.intellij.openapi.util.Key")
interface KeyRef<T>

@Remote("com.intellij.openapi.util.UserDataHolder")
interface UserDataHolderRef

@Remote("com.intellij.execution.OutputListener")
interface OutputListenerRef : ProcessAdapterRef {
    override fun onTextAvailable(@NotNull event: ProcessEventRef, @NotNull outputType: KeyRef<*>)
    override fun processTerminated(@NotNull event: ProcessEventRef)
    fun getOutput(): OutputRef
}

@Remote("com.intellij.execution.Output")
interface OutputRef {
    fun getStdout(): String
    fun getStderr(): String
    fun getExitCode(): Int
}
//
//@Remote("java.lang.StringBuilder")
//interface StringBuilderRef {
//    fun String.substring(startIndex: Int): String;
//    fun append(str: String): StringBuilderRef
//}


@Remote("com.intellij.util.io.BaseOutputReader.Options")
interface OptionsProviderRef {
    companion object {
        @JvmStatic
        fun forMostlySilentProcess(): OptionsRef {
            return OptionsRef.BLOCKING
        }
    }
}

// Assuming Options is an enum or sealed class
enum class OptionsRef {
    BLOCKING,
    NON_BLOCKING
}

//@Remote("com.intellij.execution.process.KillableColoredProcessHandler")
//interface KillableColoredProcessHandlerRef : ProcessHandler {
//    fun readerOptions(): OptionsProviderRef
//    @Remote("com.intellij.execution.process.KillableColoredProcessHandler\$Silent")
//    interface SilentRef : KillableColoredProcessHandlerRef {
//        fun notifyTextAvailable(text: String, outputType: KeyRef<*>)
//    }
//}
