# language: en
Feature: Sheet Service
  Tests the sheet service which imports a single sheet from CADC to Octopus3

  Scenario: Sheet flow correct
    Given Cadc sheet z1.ceh
    Given Repo save service for publication SCORE locale en_GB sku z1.ceh
    When I import sheet with publication SCORE locale en_GB sku z1.ceh correctly
    Then Sheet with publication SCORE locale en_GB sku z1.ceh should be imported successful

  Scenario: Sheet flow invalid publication
    When I import sheet with invalid publication parameter
    Then Import should give publication parameter error

  Scenario: Sheet flow invalid locale
    When I import sheet with invalid locale parameter
    Then Import should give locale parameter error

  Scenario: Sheet flow invalid url
    When I import sheet with invalid url parameter
    Then Import should give url parameter error
