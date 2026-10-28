package com.rankweis.uppercut.karate.ui.util

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.VirtualFile

// From 2026.3 the xdebugger API (and DefaultDebugExecutor) lives in a product module with its own
// classloader, so the driver no longer finds it on the core one ("No such class ... in plugin null")
// unless told which module owns it.
internal const val DEBUGGER_MODULE = "com.intellij/intellij.platform.debugger"

/**
 * Remote access to the running debug session, so a test can see that the plugin's debugger actually
 * suspended a run - the one thing about it that no unit test or headless harness can observe.
 */
@Remote("com.intellij.xdebugger.XDebuggerManager", plugin = DEBUGGER_MODULE)
interface XDebuggerManagerRef {
    fun getCurrentSession(): XDebugSessionRef?

    /**
     * The session behind one Debug tab. Once a run can open two, {@code getCurrentSession} names
     * whichever the IDE last focused - so a test that means "the Java one" has to ask by tab.
     */
    fun getDebugSession(console: ExecutionConsoleRef): XDebugSessionRef?
}

@Remote("com.intellij.xdebugger.XDebugSession", plugin = DEBUGGER_MODULE)
interface XDebugSessionRef {
    fun getSessionName(): String
    fun isSuspended(): Boolean
    fun isStopped(): Boolean
    fun getCurrentPosition(): XSourcePositionRef?
    fun stepOver(ignoreBreakpoints: Boolean)
    fun resume()
    fun stop()
}

@Remote("com.intellij.xdebugger.XSourcePosition", plugin = DEBUGGER_MODULE)
interface XSourcePositionRef {
    /** Zero-based, as the platform counts editor lines; Karate reports the same line one-based. */
    fun getLine(): Int

    /**
     * Which file the run stopped in. A line number alone cannot tell two modules apart: a java
     * breakpoint binds by class name and line, so if both fixture modules declared `sample.Helper`
     * at the same line, a v2 run would show v1's source and the assertion would not notice.
     */
    fun getFile(): VirtualFile?
}

fun Driver.debuggerManager(project: Project): XDebuggerManagerRef =
    service(XDebuggerManagerRef::class, project, RdTarget.DEFAULT)
