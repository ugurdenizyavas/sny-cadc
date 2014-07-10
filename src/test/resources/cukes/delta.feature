# language: en
Feature: Delta
  Delta service should be in sync with data-source (CADC)

  Scenario: Save flow
    When I save sheet z1.ceh
    Then sheet z1.ceh should be saved

  Scenario: Delta flow
    Given Cadc returns 3 sheets for locale en_GB successfully
    When I request delta of publication SCORE locale en_GB since 2014-06-20T20:30:00.000Z
    Then Sheets should be imported with publication SCORE locale en_GB since 2014-06-20T20:30:00.000Z

  Scenario: Sheet flow correct
    Given Cadc sheet z1.ceh
    When I import sheet z1.ceh correctly
    Then Sheet z1.ceh should be imported

  Scenario: Sheet flow invalid urn
    When I import sheet z1.ceh with invalid urn
    Then Import should give urn parameter error

  Scenario: Sheet flow invalid cadc url
    When I import sheet z1.ceh with invalid cadc url
    Then Import should give cadc url parameter error
