package ch.cottier.functionalUtilities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Function;

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

  public Result<Boolean> close() {
    try {
      conn.close();
      return Result.success(conn.isClosed());
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  public Result<PreparedStatement> prepareStatement(String sqlString) {
      try {
        return Result.success(conn.prepareStatement(sqlString));
      } catch (SQLException e) {
        return Result.failure(e);
      }
  }

  public Result<DataBase> execute(String sqlString) {
    return execute(List.of(sqlString));
  }

  public Result<DataBase> execute(
      List<String> sqlStrings) {
    try (Statement s = conn.createStatement()) {
      for (String sql : sqlStrings)
        if (s.execute(sql))
          throw new IllegalStateException("Prepared Statement was a query instead of just an INSERT");

      return Result.success(this);
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  public <T> Result<Tuple<List<T>, DataBase>> mapQuery(String sqlQuery, List<String> colNames, Function<DBResultSet, Result<Tuple<T, Input>>> f) {
    try (Statement s = conn.createStatement()) {
      Result<List<T>> resList =  DBResultSet.resultSet(s.executeQuery(sqlQuery), colNames)
                                            .flatMapInput(f)
                                            .map(
                                                                                                   Tuple::_1);
      return resList.map(t -> new Tuple<>(t, this));
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }
}