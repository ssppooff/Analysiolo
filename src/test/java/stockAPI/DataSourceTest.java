package stockAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import functionalUtilities.DataBase;
import functionalUtilities.FileReader;
import functionalUtilities.List;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DataSourceTest {
  String path = "src/test/java/testdata.txt";

  // Data for test transaction
  static LocalDate date = LocalDate.parse("2022-02-18");
  static String symbol = "VTI";
  static int nShares = 10;
  static BigDecimal price = new BigDecimal("40.11");

  // Create in-memory database
  private final static String DB_INMEM = "jdbc:h2:mem:";

  // SQL Strings
  static final String SQL_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS transactions (id IDENTITY PRIMARY KEY, date DATE, symbol VARCHAR, numShares INT, price NUMERIC(20,3))";
  static final String SQL_INSERT_PREP = "INSERT INTO transactions (date, symbol, numShares, price) VALUES (?, ?, ?, ?)";
  static final String SQL_QUERY = "SELECT date, symbol, numShares, price FROM transactions";
  static final String SQL_INSERT_1 = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-02-18', 'VTI', 10, 40.11)";
  static final String SQL_INSERT_2 = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-10-09', 'AVUV', 100, 40.00)";
  static final String SQL_INSERT_3 = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-10-12', 'VTI', 40, 50.11)";
  static final String SQL_INSERT_4 = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-10-12', 'AVUV', -40, 60.00)";

  @SuppressWarnings("unused")
  void assertSuccess(Result<?> r) {
    assertTrue(r.isSuccess(), r.toString());
  }

  @SuppressWarnings("unused")
  void assertFailure(Result<?> r) {
    assertTrue(r.isFailure(), r.toString());
  }

  @Test
  void parseFPIntoDB() {
    Result<List<Transaction>> listTx = FileReader.read(path)
        .flatMap(Parser::parseTransactions);
    assertSuccess(listTx.map(Parser::parseStocks).flatMap(Parser::checkForNegativeStocks));

    // Create prepared statement to put all transactions into the db
    Result<DataBase> rDB = DataBase.openDataBase(DB_INMEM);
    rDB = rDB.flatMap(db -> db.execute(List.of(SQL_CREATE_TABLE)));
    Result<PreparedStatement> rPrepStmt = rDB.flatMap(db -> db.prepareStatement(SQL_INSERT_PREP));

    var foo = listTx.flatMap(l ->
        l.foldLeft(rPrepStmt, acc -> e -> acc.flatMap(ps -> setTxData(ps, e).flatMap(this::executeStatement))));
    assertSuccess(foo);

    // Check that all transactions were input correctly
    var resListTx = rDB.flatMap(
        db -> db.mapQuery(SQL_QUERY, List.of("date", "symbol", "numShares", "price"), Parser::createTx)
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

  @Test
  void h2TestFPSingleRow() {
    Result<DataBase> rDB = DataBase.openDataBase(DB_INMEM);
    rDB = rDB.flatMap(db -> db.execute(List.of(SQL_CREATE_TABLE, SQL_INSERT_1)));
    Result<List<Transaction>> listTx = rDB.flatMap(
        db -> db.mapQuery(SQL_QUERY, List.of("date", "symbol", "numShares", "price"), Parser::createTx)
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
        db -> db.mapQuery(SQL_QUERY, List.of("date", "symbol", "numShares", "price"), Parser::createTx)
            .map(Tuple::_1));

    List<Transaction> testTx = List.of(Transaction.transaction(date, symbol, nShares, price),
        Transaction.transaction(LocalDate.parse("2022-10-09"), "AVUV", 100, BigDecimal.valueOf(40.00)),
        Transaction.transaction(LocalDate.parse("2022-10-12"), "VTI", 40, BigDecimal.valueOf(50.11)),
        Transaction.transaction(LocalDate.parse("2022-10-12"), "AVUV", -40, BigDecimal.valueOf(60.00)));
    listTx.forEach(tx -> assertEquals(testTx, tx));
  }

  Result<PreparedStatement> setTxData(PreparedStatement ps, Transaction tx) {
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
}