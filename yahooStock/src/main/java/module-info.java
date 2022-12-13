module yahooStock {
  requires java.sql;
  requires functionalUtilities;
  requires YahooFinanceAPI;
  requires org.slf4j.nop;

  exports ch.cottier.stockAPI;
}