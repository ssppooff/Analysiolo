import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import functionalUtilities.DataBase;
import functionalUtilities.FileReader;
import functionalUtilities.List;
import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import stockAPI.Symbol;
import stockAPI.Transaction;

/* TODO Current task & subtasks:
    * Refactor into DataSource.java
    - Parse data from file, check whether it is valid, if so, put into db
    - nShares integer or double or BigDecimal?

 If no date is given/what is the value of the portfolio now?
 * Input all the transactions into db
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
   - ~~Input all the transactions into db~~
   - ~~Write methods to write multiple rows into db~~
   - ~~Write methods to read multiple rows into db~~
   - ~~compile list of all stocks held~~
   - ~~After parsing all transactions, make sure that no nShares is negative for any stock~~
   - ~~compile list of all stock held at this current moment~~
   - ~~Connect to H2 database using JDBC, write & read stuff to it~~
   - ~~Stream.java -> flattenResult~~
   - ~~check whether txType and price (pos or neg) corresponds~~
   - ~~Read all the transactions from the db into memory~~
   - ~~When parsing from file, make sure to return failure of createTxWithType()~~
   - ~~List.unfold() where you preserve the Return.failure()~~
   - ~~Write multiple transactions into db in FP style~~
   - ~~Close statements, preparedStatements and database~~
   - ~~Refactor code that gets data out of DataBase, eliminate use of statements and ResultSets
      from MainTest.java~~
   - ~~Can I remove preparedStatements from DataBase.java?~~
 */

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
class MainTest {
  String path = "src/test/java/testdata.txt";
  String pathErrorFile = "src/test/java/testdata_error.txt";
  Result<FileReader> fR;
  static LocalDate date = LocalDate.parse("2022-02-18");
  static String symbol = "VTI";
  static int nShares = 10;
  static BigDecimal price = new BigDecimal("40.11");

  // Create in-memory database
  String DB_INMEM = "jdbc:h2:mem:";

  // SQL Strings
  static final String SQL_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS transactions (id IDENTITY PRIMARY KEY, date DATE, symbol VARCHAR, numShares INT, price NUMERIC(20,3))";
  static final String SQL_INSERT_1 = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-02-18', 'VTI', 10, 40.11)";
  static final String SQL_INSERT_2 = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-10-09', 'AVUV', 100, 40.00)";
  static final String SQL_INSERT_3 = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-10-12', 'VTI', 40, 50.11)";
  static final String SQL_INSERT_4 = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-10-12', 'AVUV', -40, 60.00)";
  static final String SQL_QUERY = "SELECT date, symbol, numShares, price FROM transactions";
  static final String SQL_INSERT_PREP = "INSERT INTO transactions (date, symbol, numShares, price) VALUES (?, ?, ?, ?)";

  void assertSuccess(Result<?> r) {
    assertTrue(r.isSuccess(), r.toString());
  }

  void assertFailure(Result<?> r) {
    assertTrue(r.isFailure(), r.toString());
  }

  @Test
  void parseFPIntoDB() {
    Result<List<Transaction>> listTx = FileReader.read(path)
        .flatMap(this::parseTransactions);
    assertSuccess(listTx.map(this::parseStocks).flatMap(this::checkForNegativeStocks));

    // Create prepared statement to put all transactions into the db
    Result<DataBase> rDB = DataBase.openDataBase(DB_INMEM);
    rDB = rDB.flatMap(db -> db.execute(List.of(SQL_CREATE_TABLE)));
    Result<PreparedStatement> rPrepStmt = rDB.flatMap(db -> db.prepareStatemet(SQL_INSERT_PREP));

    var foo = listTx.flatMap(l ->
        l.foldLeft(rPrepStmt, acc -> e -> acc.flatMap(ps -> insertTxData(ps, e).flatMap(this::executeStatement))));
    assertSuccess(foo);

    // Check that all transactions were input correctly
    var resListTx = rDB.flatMap(
        db -> db.mapQuery(SQL_QUERY, List.of("date", "symbol", "numShares", "price"), Main::createTx)
            .map(Tuple::_1));
    listTx.forEach(lexp -> resListTx.forEachOrFail(lres ->
            assertEquals(lexp, lres))
        .forEach(Assertions::fail));

    // Close all database resources
    var f = rPrepStmt.flatMap(ps -> {
      try {
        ps.close();
        return Result.success(ps.isClosed());
      } catch (SQLException e) {
        return Result.failure(e);
      }
    });
    assertSuccess(f);
    assertSuccess(rDB.flatMap(DataBase::close));
  }

  Result<PreparedStatement> insertTxData(PreparedStatement ps, Transaction tx) {
    try {
      ps.setString(1, tx.getDate().toString());
      ps.setString(2, tx.getSymbol().toString());
      ps.setInt(3, tx.getNumShares());
      ps.setBigDecimal(4, tx.getPrice());
      return Result.success(ps);
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  Result<PreparedStatement> executeStatement(PreparedStatement ps) {
    try {
      if (ps.execute()) {
        throw new IllegalStateException("Prepared Statement was a query instead of just an INSERT");
      }
      return Result.success(ps);
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  @Test
  void parseTest() {
    assertFailure(FileReader.read(pathErrorFile)
        .flatMap(this::parseTransactions));

    Result<Map<Symbol, Integer>> rStocks = FileReader.read(pathErrorFile)
        .map(fR -> List.unfold(fR, Main::createTxWithCheck))
        .flatMap(l -> List.flattenResult(l.filter(Result::isSuccess)))
        .map(this::parseStocks)
        .flatMap(this::checkForNegativeStocks);
    assertFailure(rStocks);

    rStocks = FileReader.read(path)
        .flatMap(this::parseTransactions)
        .map(this::parseStocks)
        .flatMap(this::checkForNegativeStocks);
    assertSuccess(rStocks);

    rStocks.forEach(map -> map.get(Symbol.symbol("VXUS"))
        .forEachOrFail(nShares -> assertEquals(334, nShares))
        .forEach(Assertions::fail));

    Transaction expTx = Transaction.transaction(date, symbol, nShares, price);
    FileReader.read(path)
        .flatMap(Main::createTxWithCheck)
        .forEach(t -> t._1.forEachOrFail(tx ->
            assertEquals(expTx, tx)).forEach(Assertions::fail));
  }

  private Result<List<Transaction>> parseTransactions(FileReader input) {
    return List.flattenResult(List.unfold(input, Main::createTxWithCheck));
  }

  private Map<Symbol, Integer> parseStocks(List<Transaction> l) {
    return l.foldLeft(Map.empty(), acc -> e -> acc.put(
            e.getSymbol(),
            acc.get(e.getSymbol()).getOrElse(0) + e.getNumShares()));
  }

  private Result<Map<Symbol, Integer>> checkForNegativeStocks(Map<Symbol, Integer> stocks) {
    List<Tuple<Symbol, Integer>> negativeStock = stocks.stream().filter(t -> t._2 < 0).toList();
    return negativeStock.isEmpty()
        ? Result.success(stocks)
        : Result.failure("Input data contains stocks with negative number of shares: " +
            negativeStock.stream().map(t -> t._1).toList());
  }

  @Test
  void h2TestFPSingleRow() {
    Result<DataBase> rDB = DataBase.openDataBase(DB_INMEM);
    rDB = rDB.flatMap(db -> db.execute(List.of(SQL_CREATE_TABLE, SQL_INSERT_1)));
    Result<List<Transaction>> listTx = rDB.flatMap(
        db -> db.mapQuery(SQL_QUERY, List.of("date", "symbol", "numShares", "price"), Main::createTx)
            .map(Tuple::_1));

    List<Transaction> testTx = List.of(Transaction.transaction(date, symbol, nShares, price));
    listTx.forEachOrFail(tx -> assertEquals(testTx, tx)).forEach(Assertions::fail);
  }

  @Test
  void h2TestFPMultipleRows() {
    Result<DataBase> rDB = DataBase.openDataBase(DB_INMEM);
    rDB = rDB.flatMap(db -> db.execute(
        List.of(SQL_CREATE_TABLE, SQL_INSERT_1, SQL_INSERT_2, SQL_INSERT_3, SQL_INSERT_4)));

    Result<List<Transaction>> listTx = rDB.flatMap(
        db -> db.mapQuery(SQL_QUERY, List.of("date", "symbol", "numShares", "price"), Main::createTx)
            .map(Tuple::_1));

    List<Transaction> testTx = List.of(Transaction.transaction(date, symbol, nShares, price),
        Transaction.transaction(LocalDate.parse("2022-10-09"), "AVUV", 100, BigDecimal.valueOf(40.00)),
        Transaction.transaction(LocalDate.parse("2022-10-12"), "VTI", 40, BigDecimal.valueOf(50.11)),
        Transaction.transaction(LocalDate.parse("2022-10-12"), "AVUV", -40, BigDecimal.valueOf(60.00)));
    listTx.forEach(tx -> assertEquals(testTx, tx));
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
    Result<Map<Symbol, Integer>> stocks = fR
        .flatMap(this::parseTransactions)
        .map(this::parseStocks);

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
    Result<Transaction> rTx = FileReader.read(pathErrorFile)
        .flatMap(Main::createTxWithCheck)
        .flatMap(Tuple::_1);
    assertFailure(rTx);

    Transaction expTx = Transaction.transaction(date, symbol, nShares, price);
    rTx = FileReader.read(path)
        .flatMap(Main::createTxWithCheck)
        .flatMap(Tuple::_1);
    rTx.forEachOrFail(tx -> assertEquals(expTx, tx))
        .forEach(Assertions::fail);
  }
}