package com.rankweis.uppercut.karate.inspections

import com.intellij.psi.PsiFile
import com.rankweis.uppercut.karate.BDDFrameworkType

data class CucumberStepDefinitionCreationContext(var psiFile: PsiFile? = null, var frameworkType: BDDFrameworkType? = null)