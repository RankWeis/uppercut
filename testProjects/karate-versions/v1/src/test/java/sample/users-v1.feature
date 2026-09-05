Feature: karate v1 sample - mirrors the v2 fixture so both runner paths are compared like for like

  Scenario: v1 built-ins and a nested call
    * def id = 'sample-id'
    * match id == '#string'
    * def num = 5
    * match num == '#number'
    * def result = call read('called.feature') { name: 'sample' }
    * match result.greeting == 'hello sample'

  Scenario: second scenario for tree ordering
    * def env = karate.env
    * print 'env is', env
