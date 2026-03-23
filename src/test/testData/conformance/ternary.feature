Feature: Ternary operator support

  Scenario: Ternary expressions should not produce errors
    * def result = true ? 'yes' : 'no'
    * def status = response ? 200 : 404
    * def value = (a && b) ? a : b
    * def nested = a ? b ? 'x' : 'y' : 'z'
    * def withVar = myVar ? 'found' : 'missing'
    * assert name == 'two' ? extra == 'normal' : true
