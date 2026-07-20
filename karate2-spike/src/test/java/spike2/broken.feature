Feature: deliberately failing - probes failure event payloads
  Not scanned by the default probe/test runs (lives outside classpath:spike).
  Run with: ../gradlew -p karate2-spike eventProbe -PprobePath=classpath:spike2

  Scenario: failing match
    * def actual = { a: 1 }
    * match actual == { a: 2 }

  Scenario: passing sibling
    * def ok = true
    * match ok == true
