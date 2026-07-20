Feature: used only by the settings-override check - runs under AUTO, must not under a forced V1

  Scenario: trivial passing scenario
    * def ok = true
    * match ok == true
