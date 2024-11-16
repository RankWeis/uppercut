package com.rankweis.uppercut.karate.psi

import com.intellij.psi.formatter.FormatterTestCase

class KarateFormattingTest : FormatterTestCase() {

    fun testFormatter() {
        doTest()
    }

    override fun getBasePath(): String {
        return "testData"
    }

    override fun getTestDataPath(): String {
        return "src/test"
    }

    override fun getFileExtension(): String {
        return "feature"
    }
}