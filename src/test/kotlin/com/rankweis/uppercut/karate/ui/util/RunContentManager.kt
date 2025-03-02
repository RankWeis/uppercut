import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.ProcessHandlerRef
import com.intellij.driver.sdk.Project
import org.jetbrains.annotations.NotNull

object State {
    val stdout = StringBuilder()
    val stderr = StringBuilder()
}

fun Driver.getRunContentManagerRef(project: Project): RunContentManagerRef =
    service(RunContentManagerRef::class, project, RdTarget.BACKEND)

fun Driver.newProcessListener(project: Project): ProcessListenerRef =
    new(OutputListenerRef::class, State.stdout, State.stderr)
fun Driver.getProcessListener(): OutputListenerRef =
    service(OutputListenerRef::class)

fun Driver.getProcessHandler(project: Project): ProcessHandler =
    service(KillableColoredProcessHandlerRef.SilentRef::class, project, RdTarget.BACKEND)


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
    fun getProcessHandler(): KillableColoredProcessHandlerRef.SilentRef?
}

@Remote("com.intellij.execution.process.ProcessHandler")
interface ProcessHandler  {
    fun isProcessTerminated(): Boolean
    fun isProcessTerminating(): Boolean
    fun waitFor(millis: Long): Boolean
    fun addProcessListener(listener: ProcessListenerRef)
    fun addProcessListener(listener: ProcessListenerRef, parentDisposable: DisposableRef)
}

@Remote("com.intellij.openapi.Disposable")
interface DisposableRef 

@Remote("com.intellij.execution.process.ProcessAdapter")
interface ProcessAdapterRef:  ProcessListenerRef

@Remote("java.util.EventListener")
interface EventListenerRef

@Remote("com.intellij.execution.process.ProcessListener")
interface ProcessListenerRef : EventListenerRef {
    fun onTextAvailable(event: ProcessEventRef, outputType: KeyRef<*>) {
    }

    fun startNotified(event: ProcessEventRef) {
    }

    fun processTerminated(event: ProcessEventRef) {
    }

    fun processWillTerminate(event: ProcessEventRef, willBeDestroyed: Boolean) {
    }

    fun processNotStarted() {}
}

@Remote("com.intellij.execution.process.ProcessEvent")
interface ProcessEventRef  {
    fun getProcessHandler(): ProcessHandlerRef

    fun getText(): String

    fun getExitCode(): Int
}

@Remote("com.intellij.openapi.util.Key")
interface KeyRef<T> 

@Remote("com.intellij.openapi.util.UserDataHolder")
interface UserDataHolderRef

@Remote("com.intellij.execution.OutputListener")
interface OutputListenerRef:  ProcessAdapterRef {
    override fun onTextAvailable(@NotNull event: ProcessEventRef, @NotNull outputType: KeyRef<*>)
    override fun processTerminated(@NotNull event: ProcessEventRef )
    fun getOutput(): OutputRef
}

@Remote("com.intellij.execution.Output")
interface OutputRef  {
    fun getStdout(): String
    fun getStderr(): String
    fun getExitCode(): Int
}

@Remote("java.lang.StringBuilder")
interface StringBuilderRef {
    fun String.substring(startIndex: Int): String;
    fun append(str: String): StringBuilderRef
}


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

@Remote("com.intellij.execution.process.KillableColoredProcessHandler")
interface KillableColoredProcessHandlerRef : ProcessHandler {
    fun readerOptions(): OptionsProviderRef
    @Remote("com.intellij.execution.process.KillableColoredProcessHandler\$Silent")
    interface SilentRef : KillableColoredProcessHandlerRef {
        fun notifyTextAvailable(text: String, outputType: KeyRef<*>)
    }
}
