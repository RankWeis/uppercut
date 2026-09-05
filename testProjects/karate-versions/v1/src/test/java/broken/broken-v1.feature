Feature: deliberately failing - exercises the v1 failure path
  Lives outside classpath:sample so default runs skip it.

  Scenario: failing match
    * def actual = { a: 1 }
    * match actual == { a: 2 }

  Scenario: passing sibling
    * def ok = true
    * match ok == true
