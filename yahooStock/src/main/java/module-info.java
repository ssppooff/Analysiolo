module yahooStock {
  requires java.sql;
  requires functionalUtilities;
  requires YahooFinanceAPI;

  exports ch.cottier.stockAPI;
}