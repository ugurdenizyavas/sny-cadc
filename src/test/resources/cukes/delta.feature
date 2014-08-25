# language: en
Feature: Delta
  Delta service should be in sync with data-source (CADC)

  Scenario: Delta flow successful
    Given Cadc services for locale en_GB with no errors
    Given Repo services for publication SCORE locale en_GB with no errors
    When I request delta of publication SCORE locale en_GB
    Then Delta for publication SCORE locale en_GB should be imported successfully

  Scenario: Delta flow with cadc delta service error
    Given Cadc delta service error for locale en_GB
    When I request delta of publication SCORE locale en_GB
    Then Delta for publication SCORE locale en_GB should get cadc delta service error

  Scenario: Delta flow with last modified date save error
    Given Cadc services for locale en_GB with no errors
    Given Repo services for publication SCORE locale en_GB with last modified date save error
    When I request delta of publication SCORE locale en_GB
    Then Delta for publication SCORE locale en_GB should get last modified date save error

  Scenario: Delta flow with parse delta error
    Given Cadc services for locale en_GB with parse delta error
    When I request delta of publication SCORE locale en_GB
    Then Delta for publication SCORE locale en_GB should get parse delta error

  Scenario: Delta flow with save errors
    Given Cadc services for locale en_GB with errors
    Given Repo services for publication SCORE locale en_GB with save errors
    When I request delta of publication SCORE locale en_GB
    Then Delta for publication SCORE locale en_GB should get save errors

  Scenario: Delta flow invalid publication parameter
    When I import delta with invalid publication parameter
    Then Import should give publication parameter error

  Scenario: Delta flow invalid locale parameter
    When I import delta with invalid locale parameter
    Then Import should give locale parameter error

  Scenario: Delta flow invalid cadcUrl parameter
    When I import delta with invalid cadcUrl parameter
    Then Import should give cadcUrl parameter error

  Scenario: Delta flow invalid since parameter
    When I import delta with invalid since parameter
    Then Import should give since parameter error

  Scenario: Sheet flow correct
    Given Cadc sheet z1.ceh
    Given Repo save service for publication SCORE locale en_GB sku z1.ceh
    When I import sheet with publication SCORE locale en_GB sku z1.ceh correctly
    Then Sheet with publication SCORE locale en_GB sku z1.ceh should be imported successful

  Scenario: Sheet flow invalid urn
    When I import sheet with invalid urn parameter
    Then Import should give urn parameter error

  Scenario: Sheet flow invalid url
    When I import sheet with invalid url parameter
    Then Import should give url parameter error
