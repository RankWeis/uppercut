package com.rankweis.uppercut.karate.psi

import com.intellij.testFramework.ParsingTestCase

class MyPluginTest : ParsingTestCase("", "feature", GherkinParserDefinition()) {

    fun testParser() {
        doTest(true)
    }
    
    override fun getTestDataPath(): String {
        return "src/test/testData"
    }

    override fun includeRanges(): Boolean {
        return true;
    }
}