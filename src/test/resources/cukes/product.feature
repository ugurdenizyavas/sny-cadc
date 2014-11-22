# language: en
Feature: Product Service
  Tests the product service which imports a single product from CADC to Octopus3

  Scenario: Product flow correct
    Given Cadc product z1.ceh
    Given Repo save service for publication SCORE locale en_GB sku z1.ceh
    When I import product with publication SCORE locale en_GB sku z1.ceh correctly
    Then Product with publication SCORE locale en_GB sku z1.ceh should be imported successful

  Scenario: Product flow invalid publication
    When I import product with invalid publication parameter
    Then Import should give publication parameter error

  Scenario: Product flow invalid locale
    When I import product with invalid locale parameter
    Then Import should give locale parameter error

  Scenario: Product flow invalid url
    When I import product with invalid url parameter
    Then Import should give url parameter error
