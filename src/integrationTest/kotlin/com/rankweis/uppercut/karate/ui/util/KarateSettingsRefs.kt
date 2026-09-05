package com.rankweis.uppercut.karate.ui.util

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

/**
 * Remote access to the plugin's own settings, so a test can flip the Karate version override without
 * restarting the IDE - the run configuration reads the preference afresh on every launch.
 */
@Remote("com.rankweis.uppercut.settings.KarateSettingsState", plugin = "com.rankweis")
interface KarateSettingsStateRef {
    fun getKarateVersionPreference(): KarateVersionPreferenceRef
    fun setKarateVersionPreference(preference: KarateVersionPreferenceRef)
}

/** Enum values cross the driver boundary as remote references, obtained via Enum.valueOf. */
@Remote("com.rankweis.uppercut.settings.KarateSettingsState\$KarateVersionPreference", plugin = "com.rankweis")
interface KarateVersionPreferenceRef {
    fun valueOf(name: String): KarateVersionPreferenceRef
    fun name(): String
}

fun Driver.karateSettings(): KarateSettingsStateRef = service(KarateSettingsStateRef::class)

/** Sets the override to AUTO, V1 or V2. */
fun Driver.setKarateVersionPreference(value: String) {
    karateSettings().setKarateVersionPreference(
        utility(KarateVersionPreferenceRef::class).valueOf(value)
    )
}
