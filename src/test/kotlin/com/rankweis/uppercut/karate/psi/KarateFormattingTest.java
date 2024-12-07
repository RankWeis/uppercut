package com.rankweis.uppercut.karate.psi;

import com.intellij.psi.formatter.FormatterTestCase;

public class KarateFormattingTest extends FormatterTestCase {

//    @BeforeEach
//    public void setUp() throws Exception {
//        super.setUp();
//    }

    public void testComplicated() throws Exception {
        doTest();
    }

    public void testRandom() throws Exception {
        doTest();
    }

    public void testJs() throws Exception {
        doTest();
    }

    @Override
    public String getBasePath() {
        return "testData";
    }

    @Override
    public String getTestDataPath() {
        return "src/test";
    }

    @Override
    public String getFileExtension() {
        return "feature";
    }
}