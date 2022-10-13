package functionalUtilities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class DataBase {
  Connection conn;

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

  public Result<PreparedStatement> prepareStatemet(String sqlString) {
      try {
        return Result.success(conn.prepareStatement(sqlString));
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
    Result<Statement> rS = Result.success(statement);
//    sqlStrings.ma
    for (String sql : sqlStrings) {
      rS = rS.flatMap(s -> execute(s, sql));
    }
    return rS;
  }

  public Result<Tuple<DBResultSet, Statement>> executeQuery(Statement s, String sqlQuery, List<String> colNames) {
    try {
      return Result.success(new Tuple<>(DBResultSet.resultSet(s.executeQuery(sqlQuery), colNames), s));
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }
}