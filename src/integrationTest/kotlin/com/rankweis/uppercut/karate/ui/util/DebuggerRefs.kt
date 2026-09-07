package com.rankweis.uppercut.karate.ui.util

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.Project

/**
 * Remote access to the running debug session, so a test can see that the plugin's debugger actually
 * suspended a run - the one thing about it that no unit test or headless harness can observe.
 */
@Remote("com.intellij.xdebugger.XDebuggerManager")
interface XDebuggerManagerRef {
    fun getCurrentSession(): XDebugSessionRef?
}

@Remote("com.intellij.xdebugger.XDebugSession")
interface XDebugSessionRef {
    fun isSuspended(): Boolean
    fun isStopped(): Boolean
    fun getCurrentPosition(): XSourcePositionRef?
    fun stepOver(ignoreBreakpoints: Boolean)
    fun resume()
    fun stop()
}

@Remote("com.intellij.xdebugger.XSourcePosition")
interface XSourcePositionRef {
    /** Zero-based, as the platform counts editor lines; Karate reports the same line one-based. */
    fun getLine(): Int
}

fun Driver.debuggerManager(project: Project): XDebuggerManagerRef =
    service(XDebuggerManagerRef::class, project, RdTarget.DEFAULT)
