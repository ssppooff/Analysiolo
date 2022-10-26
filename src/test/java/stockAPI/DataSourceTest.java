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
  <T> Result<T> assertSuccess(Result<T> r) {
    assertTrue(r.isSuccess(), r.toString());
    return r;
  }

  @SuppressWarnings("unused")
  void assertFailure(Result<?> r) {
    assertTrue(r.isFailure(), r.toString());
  }

  void listPerLine(List<Transaction> l) {
    System.out.println(
        l.foldLeft(new StringBuilder(), s -> tx ->
            s.append(tx.toString()).append("\n")));
  }

  Result<List<Transaction>> readTx(String path) {
    Result<FileReader> fR = FileReader.read(path);
    Result<List<Transaction>> listTx = fR.flatMap(Parser::parseTransactions);
    assertSuccess(fR.flatMap(FileReader::close));
    return listTx;
  }

  Result<DataBase> readTxIntoDB(List<Transaction> l) {
    // Create prepared statement to put all transactions into the db
    Result<DataBase> rDB = DataBase.openDataBase(DB_INMEM);
    rDB = rDB.flatMap(db -> db.execute(List.of(SQL_CREATE_TABLE)));
    Result<PreparedStatement> rPrepStmt = rDB.flatMap(db -> db.prepareStatement(SQL_INSERT_PREP));

    assertSuccess(
        l.foldLeft(rPrepStmt, acc -> e ->
        acc.flatMap(ps -> setTxData(ps, e).flatMap(this::executeStatement))));

    // Close prepared statement
    assertSuccess(rPrepStmt.flatMap(ps -> {
          try {
            ps.close();
            return Result.success(ps.isClosed());
          } catch (SQLException e) {
            return Result.failure(e);
          }
        }));

    return rDB;
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

  @Test
  void parseFPIntoDB() {
    Result<FileReader> fR = FileReader.read(path);
    Result<List<Transaction>> listTx = fR.flatMap(Parser::parseTransactions);

    // Check that all transactions were input correctly
    Result<DataBase> rDB = listTx.flatMap(this::readTxIntoDB);
    Result<List<Transaction>> resListTx = rDB.flatMap(
        db -> db.mapQuery(SQL_QUERY, List.of("date", "symbol", "numShares", "price"), Parser::createTx)
            .map(Tuple::_1));
    listTx.forEach(lexp -> resListTx.forEachOrFail(lres ->
            assertEquals(lexp, lres))
        .forEach(Assertions::fail));

    // Close all database resources
    assertSuccess(rDB.flatMap(DataBase::close));
    assertSuccess(fR.flatMap(FileReader::close));
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

  @Test
  void getTransactions() {
    Result<List<Transaction>> listTx = readTx(path);
    Result<DataSource> rDS = DataSource.openInMemory();
    rDS = rDS.flatMap(ds -> listTx.flatMap(ds::insertTransactions));
    var res = rDS.flatMap(DataSource::getTransactions);
    assertSuccess(res);
    res.map(Tuple::_1).forEach(this::listPerLine);

    rDS = res.map(Tuple::_2);
    assertSuccess(rDS.map(DataSource::close));
  }

  @Test
  void getLastDate() {
    Result<DataSource> rDS = DataSource.openInMemory()
        .flatMap(ds -> readTx(path).flatMap(ds::insertTransactions));
    Result<LocalDate> res = rDS.flatMap(DataSource::getLastDate).map(Tuple::_1);
    LocalDate expDate = LocalDate.parse("2021-12-12");
    assertSuccess(res).forEach(date -> assertEquals(expDate, date));
  }
}