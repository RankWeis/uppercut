package com.rankweis.uppercut.karate.ui.util
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import java.io.Serializable

class CustomProcessListener : ProcessListener, Serializable {
    val sb = StringBuilder()

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        sb.append(event.getText())
        super.onTextAvailable(event, outputType)
    }

    fun getLogs() = sb
}