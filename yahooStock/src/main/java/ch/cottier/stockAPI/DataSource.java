package ch.cottier.stockAPI;

import ch.cottier.functionalUtilities.DBResultSet;
import ch.cottier.functionalUtilities.DataBase;
import ch.cottier.functionalUtilities.Input;
import ch.cottier.functionalUtilities.List;
import ch.cottier.functionalUtilities.Result;
import ch.cottier.functionalUtilities.Tuple;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

public class DataSource {
  // Create in-memory database
  private final static String DB_INMEM = "jdbc:h2:mem:";
  private final static String DB_CONNECT = "jdbc:h2:";
  private final static String DB_JDBC_EXISTS_FLAG = ";IFEXISTS=TRUE";
  private final DataBase db;
  private final PreparedStatement insertTransaction;

  // SQL Strings
  private static final String SQL_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS transactions (id IDENTITY PRIMARY KEY, date DATE, symbol VARCHAR, numShares INT, price NUMERIC(20,3))";
  private static final String SQL_INSERT_PREP = "INSERT INTO transactions (date, symbol, numShares, price) VALUES (?, ?, ?, ?)";
  private static final String SQL_QUERY_DESC = "SELECT date, symbol, numShares, price FROM transactions";
  private static final String SQL_QUERY_LAST_DATE = "SELECT MAX(date) AS date FROM transactions FETCH FIRST 1 ROW ONLY";

  private DataSource(DataBase db, PreparedStatement ps) {
    super();
    this.db = db;
    this.insertTransaction = ps;
  }

  public Result<Tuple<LocalDate, DataSource>> getLastDate() {
    return db.mapQuery(SQL_QUERY_LAST_DATE, List.of("date"), DBResultSet::nextDate)
        .map(t -> new Tuple<>(t._1.head(), this));
  }

  public Result<Tuple<List<Transaction>, DataSource>> getTransactions() {
    return db.mapQuery(SQL_QUERY_DESC, List.of("date", "symbol", "numShares", "price"),
            DataSource::createTx)
        .map(t -> new Tuple<>(t._1, this));
  }

  private static Result<Tuple<Transaction, Input>> createTx(Input input) {
    return input.nextDate()
        .flatMap(date -> date._2.nextStr()
            .flatMap(symbol -> symbol._2.nextInt()
                .flatMap(nShares -> nShares._2.nextBigDecimal()
                    .map(price ->
                        new Tuple<>(
                            Transaction.transaction(date._1, symbol._1, nShares._1, price._1),
                            price._2)))));
  }

  public Result<DataSource> insertTransactions(List<Transaction> lTx) {
    Result<PreparedStatement> res = lTx.foldLeft(Result.success(insertTransaction), rPS -> tx ->
            rPS.flatMap(ps -> setTxData(ps, tx)
                .flatMap(DataSource::executeStatement)));

    return res.map(ignored -> this);
  }

  private static Result<PreparedStatement> setTxData(PreparedStatement ps, Transaction tx) {
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

  private static Result<PreparedStatement> executeStatement(PreparedStatement ps) {
    try {
      if (ps.execute()) {
        throw new IllegalStateException("Prepared Statement was a query instead of just an INSERT");
      }
      return Result.success(ps);
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  public static Result<DataSource> openInMemory() {
    return DataBase.openDataBase(DB_INMEM)
        .flatMap(db1 -> db1.execute(SQL_CREATE_TABLE))
        .flatMap(db1 -> {
          Result<PreparedStatement> rPS = db1.prepareStatement(SQL_INSERT_PREP);
          return rPS.map(ps -> new DataSource(db1, ps));
        });
  }

  public static Result<DataSource> open(String path) {
    return DataBase.openDataBase(DB_CONNECT + path)
                   .flatMap(db1 -> db1.execute(SQL_CREATE_TABLE))
                   .flatMap(db1 -> {
                     Result<PreparedStatement> rPS = db1.prepareStatement(SQL_INSERT_PREP);
                     return rPS.map(ps -> new DataSource(db1, ps));
                   });
  }

  public static Result<DataSource> openIfExists(String path) {
    return DataBase.openDataBase(DB_CONNECT + path + DB_JDBC_EXISTS_FLAG)
                   .flatMap(db1 -> db1.execute(SQL_CREATE_TABLE))
                   .flatMap(db1 -> {
                     Result<PreparedStatement> rPS = db1.prepareStatement(SQL_INSERT_PREP);
                     return rPS.map(ps -> new DataSource(db1, ps));
                   });
  }

  public Result<Boolean> close() {
    try {
      insertTransaction.close();
      Result<Boolean> res = Result.success(insertTransaction.isClosed());
      return res.flatMap(ignored -> db.close());
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }
}