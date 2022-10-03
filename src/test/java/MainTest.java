import static org.junit.jupiter.api.Assertions.assertEquals;

import functionalUtilities.FileReader;
import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Stream;
import java.time.LocalDate;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import stockAPI.Symbol;
import stockAPI.Transaction;

/** TODO
 * Current task & subtasks:
 - Input all the transactions into db
 - nShares integer or double?

 If no date is given/what is the value of the portfolio now?
 * Input all the transactions into db
 - Read all the transactions from the db into memory
 - ask Yahoo Finance the prices of the list
 - ~compile list of all stock held at this current  moment~

 If the user asks for the value of the portfolio at the end of a certain date
 - figure out what stocks were held at the end of that day
 - ask Yahoo Finance the price for each stock
 - sum up and multiply according to the number of shares for each stock

 Metrics to print
 - Current Net value
 - Time weighted return, since inception, year, YTD
 - Per Stock: Current price | avg/mean buying price

 * Finished tasks
 - ~compile list of all stocks held~
 - ~After parsing all transactions, make sure that no nShares is negative for any stock~
 */

class MainTest {
  String path = "src/test/java/testdata.txt";
  Result<FileReader> fR = FileReader.read(path);
  static LocalDate date = LocalDate.parse("2022-02-18");
  @SuppressWarnings("unused")
  static String txType = "SELL";
  static String symbol = "VTI";
  static int nShares = 10;
  static BigDecimal buyPrice = new BigDecimal("40.11");

  @Test
  void parseTest() {
    var stocks = fR.map(input -> Stream
        .unfold(input, Main::createTx)
        .foldLeft(Map.<Symbol, Integer>empty(), acc -> e -> acc.put(
            e.getSymbol(),
            acc.get(e.getSymbol()).getOrElse(0) + e.getNumShares()))
    );

    var negativeStock = stocks.map(m -> m.stream().filter(t -> t._2 < 0).toList()).getOrElse(new ArrayList<>());
    Result<Map<Symbol, Integer>> result = negativeStock.isEmpty()
        ? Result.success(stocks.getOrElse(Map.empty()))
        : Result.failure("Input data contains stocks with negative number of shares: " + negativeStock.stream().map(t -> t._1).toList());

    System.out.println(result);
//    negativeStock.forEach(System.out::println);
  }

  @Test
  void createTx() {
    Transaction tx = Transaction.transaction(date, symbol, nShares, buyBasePrice);
//    System.out.println(tx.toString());
    fR.flatMap(Main::createTx).forEach(t -> assertEquals(tx, t._1));
  }
}