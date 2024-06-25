@Test
Feature: Test

  Background:
    * url 'http://google.com'

  Scenario: posts error
    * method post
    * print response
    Then match responseStatus != 200

  Scenario: gets 200
    * method get
    * print response
    Then match responseStatus == 201
