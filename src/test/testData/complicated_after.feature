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
    * def complexOperation = function(data) { return instance.processData(data); }
    * def input = [ 1, 2, 3, 4 ]
    * def result = complexOperation(input)
    * match result == [ 2, 4, 6, 8 ]

  Scenario Outline: Test with edge-case data inputs
    * def calculate = function(a, b) { return a / b; }
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
    And r = function() {
      document.querySelectorAll('.ruResponseButtons ._42ft._4jy0._4jy3._4jy1');
      for (i = 0; i < r.length; i++) {
        r[i].click();
      }
    }
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

  Scenario: Perform nested operations on a JSON structure
    * def data = { "users": [ { "id": 1, "name": "Alice" }, { "id": 2, "name": "Bob" } ] }
    * def transformData = function(input) {
      var output = [];
      input.users.forEach(function(user) {
        var transformed = {userId: user.id, details: []};
        for (var i = 1; i <= 3; i++) {
          transformed.details.push({detailId: i, info: user.name + '_info' + i});
        }
        output.push(transformed);
      });
      return output;
    }
    * def result = transformData(data)
    * print 'Transformed Data:', result
    * match result ==
  [
    {
      userId: 1,
      details: [
        {
          detailId: 1,
          info: 'Alice_info1'
        },
        {
          detailId: 2,
          info: 'Alice_info2'
        },
        {
          detailId: 3,
          info: 'Alice_info3'
        }
      ]
    },
    {
      userId: 2,
      details: [
        {
          detailId: 1,
          info: 'Bob_info1'
        },
        {
          detailId: 2,
          info: 'Bob_info2'
        },
        {
          detailId: 3,
          info: 'Bob_info3'
        }
      ]
    }
  ]

  Scenario: Parallel processing of API requests
    * def makeRequest = function(id) {
      return karate.call('singleRequest.feature', {id: id});
    }
    * def ids = [ 1, 2, 3, 4, 5 ]
    * def results = karate.parallel(ids, makeRequest)
    * match results.length == 5

  Scenario: Combine Karate DSL with embedded JavaScript
    * def id = 23
    * def data = [ { id: 1, name: 'A' }, { id: 2, name: 'B' } ]
    * def results = karate.filter(data, function(x) { return x.id > 1; })
    * match results == [ { id: 2, name: 'B' } ]

    * def calculateSum = function(a, b) {
      return a + b;
    }
    * def sum = calculateSum(10, 20)
    * match sum == 30

  Scenario: Flatten a deeply nested JSON object
    * def nestedJson =
  {
    "level1": {
      "level2": {
        "level3": {
          "key": "value"
        },
        "anotherKey": "anotherValue"
      }
    }
  }
    * def flattenJson = function(obj, prefix) {
      var result = {};
      for (var key in obj) {
        var prefixedKey = prefix ? prefix + '.' + key : key;
        if (typeof obj[key] === 'object' && obj[key] !== null) {
          Object.assign(result, flattenJson(obj[key], prefixedKey));
        } else {
          result[prefixedKey] = obj[key];
        }
      }
      return result;
    }
    * def flattened = flattenJson(nestedJson, '')
    * print 'Flattened JSON:', flattened
    * match flattened == { 'level1.level2.level3.key': 'value', 'level1.level2.anotherKey': 'anotherValue' }


  Scenario: Merge multiple JSON arrays into a single array
    * def arrays = [
      [
        {
          "id": 1,
          "name": "Item1"
        },
        {
          "id": 2,
          "name": "Item2"
        }
      ],
      [
        {
          "id": 3,
          "name": "Item3"
        },
        {
          "id": 4,
          "name": "Item4"
        }
      ],
      [
        {
          "id": 5,
          "name": "Item5"
        }
      ]
    ]
    * def mergeArrays = function(arrays) {
      var merged = [];
      arrays.forEach(function(array) {
        merged = merged.concat(array);
      });
      return merged;
    }
    * def result = mergeArrays(arrays)
    * print 'Merged JSON Array:', result
    * match result ==
  [
    {
      id: 1,
      name: 'Item1'
    },
    {
      id: 2,
      name: 'Item2'
    },
    {
      id: 3,
      name: 'Item3'
    },
    {
      id: 4,
      name: 'Item4'
    },
    {
      id: 5,
      name: 'Item5'
    }
  ]

  Scenario: Generate JSON dynamically using embedded JavaScript
    * def generateDynamicJson = function() {
      var result = {};
      for (var i = 1; i <= 5; i++) {
        result['key' + i] = {value: i * 10, nested: {id: 'item' + i}};
      }
      return result;
    }
    * def dynamicJson = generateDynamicJson()
    * print 'Generated JSON:', dynamicJson
    * match dynamicJson ==
  {
    key1: {
      value: 10,
      nested: {
        id: 'item1'
      }
    },
    key2: {
      value: 20,
      nested: {
        id: 'item2'
      }
    },
    key3: {
      value: 30,
      nested: {
        id: 'item3'
      }
    },
    key4: {
      value: 40,
      nested: {
        id: 'item4'
      }
    },
    key5: {
      value: 50,
      nested: {
        id: 'item5'
      }
    }
  }

  Scenario: JSON expression syntax
    Given var = "Hello"
    Given a =
    """
    {
      "withoutQuotes": #(var),
      "withQuotes": "#(var)"
    }
    """
