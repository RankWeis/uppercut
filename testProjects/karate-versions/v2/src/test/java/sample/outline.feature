Feature: scenario outline - exercises OUTLINE_ENTER and per-example events

  Scenario Outline: greeting <name>
    * def greeting = 'hello ' + '<name>'
    * match greeting == 'hello <name>'

    Examples:
      | name  |
      | alice |
      | bob   |
