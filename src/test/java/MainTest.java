import static org.junit.jupiter.api.Assertions.assertEquals;

import functionalUtilities.FileReader;
import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Stream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import stockAPI.Symbol;
import stockAPI.Transaction;

/** TODO
 * Current task & subtasks:
 - Input all the transactions into db
 - Connect to H2 database
 - Write stuff to database
 - Read stuff to database
 - ?? db stuff
 - nShares integer or double or BiDecimal?
 - BiDecimal for currency values

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

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
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
  void h2Test() {
    String DB_PATH = "";
    String DB_FILENAME = "";
    String DB_USER = "sa";
    String DB_PW = "";

    // Create in-memory database
    String DP_INMEM = "jdbc:h2:mem:db0";

    // Open connection to database
    try (Connection db = DriverManager.getConnection("jdbc:h2:mem:db0");
        Statement statement = db.createStatement()) {
      statement.execute("create table if not exists transactions (id identity primary key, date timestamp, symbol varchar, numShares int, buyPrice numeric)");
      statement.execute("insert into transactions (date, symbol, numShares, buyPrice) values ('2022-10-09 15:36:00', 'AVUV', 100, 40.00)");
      ResultSet res = statement.executeQuery("select * from transactions");
      while (res.next()) {
        Transaction tx = Transaction.transaction(
            res.getObject("date", LocalDate.class),
            res.getString("symbol"),
            res.getInt("numShares"),
            res.getBigDecimal("buyPrice"));
        System.out.println(tx);
      }
      res.close();
    } catch (SQLException e) {
      System.out.println("SQLException: " + e);
    }

  }

  @Test
  void createTx() {
    Transaction tx = Transaction.transaction(date, symbol, nShares, buyPrice);
//    System.out.println(tx.toString());
    fR.flatMap(Main::createTx).forEach(t -> assertEquals(tx, t._1));
  }
}