package functionalUtilities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class DataBase {
  Connection conn;

//  public static Result<String> executeStatement(String sql, Input input){
//    return Result.success("");
//  }

  private DataBase(String jdbcURL) throws SQLException {
    super();
    this.conn = DriverManager.getConnection(jdbcURL);
  }

  public static Result<DataBase> openDataBase(String jdbcURL) {
    try {
      return Result.success(new DataBase(jdbcURL));
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  public Result<Statement> createStatement() {
      try {
        return Result.success(conn.createStatement());
      } catch (SQLException e) {
        return Result.failure(e);
      }
  }

  public Result<Statement> execute(Result<Statement> rS, String sqlString) {
    return rS.flatMap(s -> execute(s, sqlString));
  }

  public Result<Statement> execute(Statement s, String sqlString) {
    try {
      s.execute(sqlString);
      return Result.success(s);
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  public Result<Statement> execute(Statement statement, List<String> sqlStrings) {
  return Stream.of(sqlStrings)
        .foldLeft(Result.success(statement), acc -> e -> acc.flatMap(s -> execute(s, e)));
  }

  public Result<Tuple<DBResultSet, Statement>> executeQuery(Statement s, String sqlQuery, List<String> colNames) {
    try {
      ResultSet rSet = s.executeQuery(sqlQuery);
      return rSet.next()
          ? Result.success(new Tuple<>(DBResultSet.resultSet(rSet, colNames), s))
          : Result.empty();
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }
}
