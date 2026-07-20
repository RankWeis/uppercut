Feature: deliberately failing - probes failure event payloads
  Not scanned by the default probe/test runs (lives outside classpath:sample).
  Run with: ../../gradlew -p testProjects/karate-v2 eventProbe -PprobePath=classpath:broken

  Scenario: failing match
    * def actual = { a: 1 }
    * match actual == { a: 2 }

  Scenario: passing sibling
    * def ok = true
    * match ok == true
