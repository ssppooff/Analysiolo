import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import functionalUtilities.DataBase;
import functionalUtilities.FileReader;
import functionalUtilities.List;
import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Stream;
import functionalUtilities.Tuple;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import stockAPI.Symbol;
import stockAPI.Transaction;

/* TODO Current task & subtasks:
    - nShares integer or double or BigDecimal?
    - ~~Input all the transactions into db~~
    - ~~Write methods to write multiple rows into db~~
    - ~~Write methods to read multiple rows into db~~

 If no date is given/what is the value of the portfolio now?
 * Input all the transactions into db
 * Read all the transactions from the db into memory
 - ask Yahoo Finance the prices of the list

 If the user asks for the value of the portfolio at the end of a certain date
 - figure out what stocks were held at the end of that day
 - ask Yahoo Finance the price for each stock
 - sum up and multiply according to the number of shares for each stock

 Metrics to print
 - Current Net value
 - Time weighted return, since inception, year, YTD
 - Per Stock: Current price | avg/mean buying price

 * Finished tasks
 - ~~compile list of all stocks held~~
 - ~~After parsing all transactions, make sure that no nShares is negative for any stock~~
 - ~~compile list of all stock held at this current moment~~
 - ~~Connect to H2 database using JDBC, write & read stuff to it~~
 - ~~Stream.java -> flattenResult~~
 - ~~check whether txType and price (pos or neg) corresponds~~
 */

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
class MainTest {
  String path = "src/test/java/testdata.txt";
  String pathErrorFile = "src/test/java/testdata_error.txt";
  Result<FileReader> fR;
  static LocalDate date = LocalDate.parse("2022-02-18");
  static String symbol = "VTI";
  static int nShares = 10;
  static BigDecimal buyPrice = new BigDecimal("40.11");

  // Create in-memory database
  String DB_INMEM = "jdbc:h2:mem:";

  // SQL Strings
  String SQL_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS transactions (id IDENTITY PRIMARY KEY, date DATE, symbol VARCHAR, numShares INT, price NUMERIC(20,3))";
  String SQL_INSERT_1 = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-02-18', 'VTI', 10, 40.11)";
  String SQL_INSERT_2 = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-10-09', 'AVUV', 100, 40.00)";
  String SQL_INSERT_3 = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-10-12', 'VTI', 40, 50.11)";
  String SQL_INSERT_4 = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-10-12', 'AVUV', -40, 60.00)";
  String SQL_QUERY = "SELECT date, symbol, numShares, price FROM transactions";

  @Test
  void parseTest() {
    fR = FileReader.read(pathErrorFile);
    Result<Map<Symbol, Integer>> res;

    res = FileReader.read(path)
        .map(this::parseStocks)
        .flatMap(this::checkNegativeStocks);
    assertTrue(res.isSuccess());
    res.forEach(m -> m.get(Symbol.symbol("VXUS"))
        .forEachOrFail(nShares -> assertEquals(334, nShares))
        .forEach(Assertions::fail));
  }

  private Map<Symbol, Integer> parseStocks(FileReader input) {
    return Stream.unfold(input, Main::createTxWithType)
        .foldLeft(Map.empty(), acc -> e -> acc.put(
            e.getSymbol(),
            acc.get(e.getSymbol()).getOrElse(0) + e.getNumShares()));
  }

  private Result<Map<Symbol, Integer>> checkNegativeStocks(Map<Symbol, Integer> stocks) {
    List<Tuple<Symbol, Integer>> negativeStock = stocks.stream().filter(t -> t._2 < 0).toList();
    return negativeStock.isEmpty()
        ? Result.success(stocks)
        : Result.failure("Input data contains stocks with negative number of shares: " + negativeStock.stream().map(t -> t._1).toList());
  }

  void printFailure(Object obj) {
    System.out.println("Failed: " + obj.toString());
  }

  @Test
  void h2TestFPSingleRow() {
    Result<DataBase> rDB = DataBase.openDataBase(DB_INMEM);
    Result<Statement> rS = rDB.flatMap(db ->
        db.createStatement().flatMap(s -> db.execute(s, List.of(SQL_CREATE_TABLE, SQL_INSERT_1))));

    Result<Transaction> rDBResSet = rDB.flatMap(db -> rS.flatMap(s ->
                db.executeQuery(s, SQL_QUERY, List.of("date", "symbol", "numShares", "price"))))
        .flatMap(t -> Main.createTx(t._1))
        .map(t -> t._1);
    rDBResSet.forEachOrFail(System.out::println).forEach(System.out::println);
  }

  @Test
  void h2TestSingleRow() {
    String DB_PATH = "";
    String DB_FILENAME = "";
    String DB_USER = "sa";
    String DB_PW = "";

    // Open connection to database
    try (Connection db = DriverManager.getConnection(DB_INMEM);
        Statement statement = db.createStatement()) {
      statement.execute(SQL_CREATE_TABLE);
      statement.execute(SQL_INSERT_1);
      ResultSet res = statement.executeQuery(SQL_QUERY);
      while (res.next()) {
        Transaction tx = Transaction.transaction(
            res.getObject("date", LocalDate.class),
            res.getString("symbol"),
            res.getInt("numShares"),
            res.getBigDecimal("price"));
      }
      res.close();
    } catch (SQLException e) {
      System.out.println("SQLException: " + e);
    }

  }

  @Test
  void parseIntoH2() {
    fR = FileReader.read(path);
    Result<Map<Symbol, Integer>> stocks = fR.map(this::parseStocks);

    try (Connection db = DriverManager.getConnection(DB_INMEM);
        Statement statement = db.createStatement()) {
      statement.execute(SQL_CREATE_TABLE);
      statement.execute(SQL_INSERT_1);
      ResultSet res = statement.executeQuery("select * from transactions");
      while (res.next()) {
        Transaction tx = Transaction.transaction(
            res.getObject("date", LocalDate.class),
            res.getString("symbol"),
            res.getInt("numShares"),
            res.getBigDecimal("price"));
      }
      res.close();
    } catch (SQLException e) {
      fail("SQLException: " + e);
    }
  }

  @Test
  void createTxTest() {
    fR = FileReader.read(pathErrorFile);
    assertTrue(fR.flatMap(Main::createTxWithType).isFailure());

    Transaction tx = Transaction.transaction(date, symbol, nShares, buyPrice);
    FileReader.read(path)
        .flatMap(Main::createTxWithType)
        .forEachOrFail(t -> assertEquals(tx, t._1))
        .forEach(Assertions::fail);
  }
}