import static org.junit.jupiter.api.Assertions.assertEquals;

import functionalUtilities.FileReader;
import functionalUtilities.Result;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import parser.Transaction;

/** TODO
 If no date is given/what is the value of the portfolio now?
 - Input all the transactions into db
 - Read all the transactions from the db into memory
 - compile list of all stock held at this current  moment
 - ask Yahoo Finance the prices of the list

 If the user asks for the value of the portfolio at the end of a certain date
 - figure out what stocks were held at the end of that day
 - ask Yahoo Finance the price for each stock
 - sum up and multiply according to the number of shares for each stock

 Metrics to print
 - Current Net value
 - Time weighted return, since inception, year, YTD
 - Per Stock: Current price | avg/mean buying price
 */

class MainTest {
  String path = "src/test/java/testdata.txt";
  Result<FileReader> rReader = FileReader.read(path);
  static LocalDate date = LocalDate.parse("2022-09-10");
  static String txType = "BUY";
  static String symbol = "AVUV";
  static int nShares = -200;
  static int buyBasePrice = 3000;

  @Test
  void createTx() {
    Transaction tx = Transaction.transaction(date, symbol, nShares, buyBasePrice);
    rReader.flatMap(Main::createTx).forEach(t -> assertEquals(tx, t._1));
  }
}