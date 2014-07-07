# language: en
Feature: Delta
  Delta service should be in sync with data-source (CADC)

  Scenario: Delta import flow
    Given Cadc returns 3 products for locale en_GB
    When I request delta of publication SCORE and locale en_GB since 2014-06-20T20:30:00.000Z
    Then 3 products should be imported with publication SCORE and locale en_GB since 2014-06-20T20:30:00.000Z

  Scenario: Sheet import flow
    Given Cadc sheet z1.ceh
    When I import sheet z1.ceh
    Then sheet z1.ceh should be imported

  Scenario: Save flow
    When I save sheet z1.ceh
    Then sheet z1.ceh should be saved
