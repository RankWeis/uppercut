Feature: karate v2 sample - exercises v2-only APIs and a called feature

  Scenario: v2 built-ins and a nested call
    * def id = karate.uuid()
    * match id == '#string'
    # v2 range assertion syntax
    * def num = 5
    * match num == '#number'
    * def result = call read('called.feature') { name: 'spike' }
    * match result.greeting == 'hello spike'

  Scenario: second scenario for tree ordering
    * def env = karate.env
    * print 'env is', env
