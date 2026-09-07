Feature: java called from a feature - the case an opt-in JVM debugger would serve

  Scenario: call into the user's own Java
    * def seed = 20
    * def HelperV1 = Java.type('sample.HelperV1')
    * def answer = HelperV1.compute(seed)
    * print 'answer is', answer
    * match answer == 41
