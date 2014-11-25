# language: en
Feature: Delta Service
  Tests the delta service which gets the delta from CADC starts the import of changed products from CADC to Octopus3

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

  Scenario: Delta flow invalid sdate parameter
    When I import delta with invalid sdate parameter
    Then Import should give sdate parameter error

