@FunctionalTest
Feature: Get collection

  Background: get collection with projectId
    * print 'goodbye'
    * url baseURL
    * call read('classpath:util/Common.feature')
    * def serviceId = clientId
    * def collectionId = karate.get("collectionIdForProjectId")
    * def projectId = karate.get("projectUrn")

  Scenario: Get collection with filters=attribute.projectId==#projectId
    * def statusCode = 200
    * def collectionFilterParam = "attribute.projectId==" + projectId
    * def getCollection = call read('classpath:test-objects/collection/Collection.feature@get-collection')
    * match getCollection.response.id == collectionId
    * match collectionId == collectionId
    
    #TODO: permissionContext is not returned when we call collection api. Need to figure out another way for 2LO auth check.
  @ignore
  Scenario: Get collection with filters=attribute.projectId==#projectId with 2LO
    * def statusCode = 200
    * def collectionFilterParam = "attribute.projectId==" + projectId
    * def getCollection = call read('classpath:test-objects/collection/Collection.feature@get-collection-with-2LO')
    * match getCollection.response.id == collectionId

  @ignore
  Scenario: Get collection with a 2LO and a user impersonation header
    * def statusCode = 200
    * def collectionFilterParam = "attribute.projectId==" + projectId
    * def getCollection = call read('classpath:test-objects/collection/Collection.feature@get-collection-with-2LO-user-impersonation') { userId: "#(o2UserId)" }
    * match getCollection.response.id == collectionId

  Scenario: Get collection with a 2LO and a random user impersonation header
    * def statusCode = 403
    * def collectionFilterParam = "attribute.projectId==" + projectId
    * def getCollection = call read('classpath:test-objects/collection/Collection.feature@get-collection-with-2LO-user-impersonation') { userId: 'RANDOM' }

  Scenario: Get collection with filter=projectId using a client that does not have access
    * def statusCode = 401
    * def collectionFilterParam = "attribute.projectId==" + projectId
    * def getCollectionResult = call read('classpath:test-objects/collection/Collection.feature@get-collection-with-alt-client')
    * match getCollectionResult.response.title == "Invalid authorization header."
    * match getCollectionResult.response.detail == "Your request was not authorized."

  Scenario: Get collection with invalid projectId
    * def statusCode = 404
    * def collectionFilterParam = "attribute.projectId==INVALID"
    * def getCollectionResult = callu read('classpath:test-objects/collection/Collection.feature@get-collection-with-2LO')
    * match getCollectionResult.response.title == "Not Found."
    * match getCollectionResult.response.detail == "The supplied projectId does not have the collection associated with it."

  Scenario: Get collection with no projectId
    * def statusCode = 400
    * def getCollectionResult = call read('classpath:test-objects/collection/Collection.feature@get-collection-no-filter')

  Scenario: get all users and then get the first user by id
    Given method get
    Then status 200