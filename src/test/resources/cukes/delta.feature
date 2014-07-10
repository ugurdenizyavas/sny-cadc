# language: en
Feature: Delta
  Delta service should be in sync with data-source (CADC)

  Scenario: Save flow
    When I save sheet z1.ceh
    Then sheet z1.ceh should be saved

  Scenario: Delta flow correct
    Given Cadc returns 3 sheets for locale en_GB successfully
    When I request delta of publication SCORE locale en_GB since 2014-06-20T20:30:00.000Z
    Then Sheets should be imported with publication SCORE locale en_GB since 2014-06-20T20:30:00.000Z

  Scenario: Delta flow invalid publication parameter
    When I import delta with invalid publication parameter
    Then Delta import should give publication parameter error

  Scenario: Delta flow invalid locale parameter
    When I import delta with invalid locale parameter
    Then Delta import should give locale parameter error

  Scenario: Delta flow invalid cadcUrl parameter
    When I import delta with invalid cadcUrl parameter
    Then Delta import should give cadcUrl parameter error

  Scenario: Delta flow invalid since parameter
    When I import delta with invalid since parameter
    Then Delta import should give since parameter error

  Scenario: Sheet flow correct
    Given Cadc sheet z1.ceh
    When I import sheet z1.ceh correctly
    Then Sheet import of z1.ceh should be successful

  Scenario: Sheet flow invalid urn
    When I import sheet with invalid urn parameter
    Then Sheet import should give urn parameter error

  Scenario: Sheet flow invalid url
    When I import sheet with invalid url parameter
    Then Sheet import should give url parameter error

