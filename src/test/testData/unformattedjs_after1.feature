Feature: Badly formatted JavaScript

  Scenario: format me
    * def a =
    """
    var obj = {
      name: "John", age: 30, job: {
        title: "developer", skills: ["javascript", "java", "python"]
      }, isActive: true
    }
    function sayHello(name) { return "Hello " + name; }
    ;
    for (var i = 0; i < 10; i++) { console.log(i); }
    """