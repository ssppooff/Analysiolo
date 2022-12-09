package ch.cottier.functionalUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import org.testng.annotations.Test;
//import ch.cottier.stockAPI.Transaction;

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
class DataBaseTest {

  // Data for test transaction
  static LocalDate date = LocalDate.parse("2022-02-18");
  static String symbol = "VTI";
  static int nShares = 10;
  static BigDecimal price = new BigDecimal("40.11");

  // Create in-memory database
  String DB_INMEM = "jdbc:h2:mem:";

  // SQL Strings
  static final String SQL_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS transactions (id IDENTITY PRIMARY KEY, date DATE, symbol VARCHAR, numShares INT, price NUMERIC(20,3))";
  static final String SQL_INSERT = "INSERT INTO transactions (date, symbol, numShares, price) VALUES ('2022-02-18', 'VTI', 10, 40.11)";
  static final String SQL_QUERY = "SELECT date, symbol, numShares, price FROM transactions";

  @Test
  void h2TestSingleRow() {
    // TODO get rid of dependency on yahooStock module
    // Open connection to database
    try (Connection db = DriverManager.getConnection(DB_INMEM);
        Statement statement = db.createStatement()) {
      statement.execute(SQL_CREATE_TABLE);
      statement.execute(SQL_INSERT);
      ResultSet res = statement.executeQuery(SQL_QUERY);
//      Transaction tx= null;
//      while (res.next()) {
//        tx = Transaction.transaction(
//            res.getObject("date", LocalDate.class),
//            res.getString("symbol"),
//            res.getInt("numShares"),
//            res.getBigDecimal("price"));
//      }

//      Transaction expTx = Transaction.transaction(date, symbol, nShares, price);
//      assertEquals(expTx, tx);
      res.close();
    } catch (SQLException e) {
      System.out.println("SQLException: " + e);
    }
  }
}