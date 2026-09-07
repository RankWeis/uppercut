Feature: java called from a feature - the case an opt-in JVM debugger would serve

  Scenario: call into the user's own Java
    * def seed = 20
    * def Helper = Java.type('sample.Helper')
    * def answer = Helper.compute(seed)
    * print 'answer is', answer
    * match answer == 41
