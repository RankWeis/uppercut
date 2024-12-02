Feature: Nested JSON and Dynamic Expressions

  Scenario: Process deeply nested JSON with dynamic expressions
    * def input = { "outer": { "inner": { "key": "#[randomString(5)]" } } }
    * def expected = karate.jsonPath(input, '$.outer.inner.key')
    * def dynamicResult = function() {
        var result = karate.jsonPath(input, '$.outer.inner.key');
        return result == expected;
    }
    * match dynamicResult() == true
    * print 'Dynamic result validation passed!'

  Scenario: Use a custom Java class to manipulate data
    * def MyJavaClass = Java.type('com.example.MyJavaClass')
    * def instance = new MyJavaClass()
    * def complexOperation = function (data) { return instance.processData(data); }
    * def input = [ 1, 2, 3, 4 ]
    * def result = complexOperation(input)
    * match result == [ 2, 4, 6, 8 ]

  Scenario Outline: Test with edge-case data inputs
    * def calculate = function (a, b) { return a / b; }
    Given a = <a> and b = <b>
    When result = call calculate(a, b)
    Then match result == <expected>

    Examples:
      | a    | b    | expected |
      | 10   | 2    | 5        |
      | -10  | 5    | -2       |
      | 1000 | 0.5  | 2000     |
      | 1e6  | 1e-3 | 1e9      |


  Scenario: Retry API call until success
    * def retryApiCall = function() {
        var maxAttempts = 3;
        for (var i = 0; i < maxAttempts; i++) {
            var response = karate.call('myApi.feature');
            if (response.status == 200) return response;
            karate.sleep(2000);
        }
        throw 'API call failed after ' + maxAttempts + ' attempts';
    }
    * def result = retryApiCall()
    * match result.status == 200

  Scenario: Chain API calls with dependencies
    Given url 'https://api.example.com/resource'
    And path 'create'
    And request { "name": "test", "value": 123 }
    When method POST
    Then status 201
    And def resourceId = response.id

    Given url 'https://api.example.com/resource'
    And path resourceId
    When method GET
    Then status 200
    And match response.name == 'test'

    Given url 'https://api.example.com/resource'
    And path resourceId
    When method DELETE
    Then status 204


  Scenario: Parallel processing of API requests
    * def makeRequest = function(id) {
        return karate.call('singleRequest.feature', {id: id});
    }
    * def ids = [ 1, 2, 3, 4, 5 ]
    * def results = karate.parallel(ids, makeRequest)
    * match results.length == 5

  Scenario: Combine Karate DSL with embedded JavaScript
    * def data = [{ id: 1, name: 'A' }, { id: 2, name: 'B' }]
    * def results = karate.filter(data, function (x) { return x.id > 1; })
    * match results == [{ id: 2, name: 'B' }]

    * def calculateSum = function(a, b) {
        return a + b;
    }
    * def sum = calculateSum(10, 20)
    * match sum == 30
