import static org.junit.jupiter.api.Assertions.assertEquals;

import functionalUtilities.FileReader;
import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Stream;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import stockAPI.Symbol;
import stockAPI.Transaction;

/** TODO
 * Current task & subtasks:
 - --compile list of all stocks held--
 - After parsed all transactions, make sure that no nShares is negative for any stock
 - nShares integer or double?

 If no date is given/what is the value of the portfolio now?
 - Input all the transactions into db
 - Read all the transactions from the db into memory
 * compile list of all stock held at this current  moment
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
  Result<FileReader> fR = FileReader.read(path);
  static LocalDate date = LocalDate.parse("2022-02-18");
  @SuppressWarnings("unused")
  static String txType = "SELL";
  static String symbol = "VTI";
  static int nShares = 10;
  static int buyBasePrice = 4011;

  @Test
  void test() {
    var transactions = fR.map(input -> Stream.unfold(input, Main::createTx))
        .getOrElse(Stream.empty());
    Map<Symbol, Integer> foo = transactions
        .foldLeft(Map.empty(), acc -> e -> {
          int nShares = acc.get(e.getSymbol())
              .map(i -> i + e.getNumShares())
              .getOrElse(e.getNumShares());
          return acc.put(e.getSymbol(), nShares);
        });
    foo.stream().forEach(System.out::println);
  }

  @Test
  void createTx() {
    Transaction tx = Transaction.transaction(date, symbol, nShares, buyBasePrice);
//    System.out.println(tx.toString());
    fR.flatMap(Main::createTx).forEach(t -> assertEquals(tx, t._1));
  }
}