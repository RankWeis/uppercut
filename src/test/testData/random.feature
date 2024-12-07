Feature: json schema validation

# ignore because it does not work offline and slows down the Karate CI build
# one more reason to avoid using JSON schema !
  @ignore
  Scenario: using a third-party lib and a schema file
    * string schema = read('products-schema.json')
    * string json = read('products.json')
    * def SchemaUtils = Java.type('com.intuit.karate.demo.util.SchemaUtils')
    * assert SchemaUtils.isValid(json, schema)
    * print <hello>
    * read('comment.json')

  Scenario: using karate's simpler alternative to json-schema
    * def warehouseLocation = { latitude: '#number', longitude: '#number' }
    * def letJs2 =
    """
      jquery.constructor = "wahtever";
      var demoBaseUrl = 'https://some-demo-url.com';
    """
    * def letJs =
    """
      function fn() {
  var token = karate.get('token');
  var time = karate.get('time');
  if (token && time) {
    var uuid = java.util.UUID.randomUUID(); // create a unique id for each request
    // demoBaseUrl was available at the time this function was declared
    // and so behaves like a constant, use 'karate.get' for dynamic values
    return {
        Authorization: token + time + demoBaseUrl,
        request_id: uuid + '' // convert the java uuid into a string
    };
  } else {
    return {};
  }
}    """
    * def productStructure =
    """
    {
      id: '#number',
      name: '#string',
      price: '#number? _ > 0',
      tags: '##[_ > 0] #string',
      dimensions: {
        length: '#number',
        width: '#number',
        height: '#number'
      },
      warehouseLocation: '##(warehouseLocation)'
    }
    """
    * def productsJson =
    """
    [{
        "id": 2,
        "name": "An ice sculpture",
        "price": 12.50,
        "tags": ["cold", "ice"],
        "dimensions": {
              "length": 7.0,
            "width": 12.0,
            "height": 9.5
        },
        "warehouseLocation": {
            "latitude": -78.75,
            "longitude": 20.4
        }
    },
    {
        "id": 3,
        "name": "A blue mouse",
        "price": 25.50,
        "dimensions": {
            "length": 3.1,
            "width": 1.0,
            "height": 1.0
        },
        "warehouseLocation": {
            "latitude": 54.4,
            "longitude": -32.7
        }
    }]
    """
      * def json = read('products.json')
    * match json == '#[] productStructure'
