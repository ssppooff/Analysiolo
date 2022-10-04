package functionalUtilities;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class DBResultSet implements Input {
  public final ResultSet rSet;
  private final List<String> colNames;
  private int index = 0;

  private DBResultSet (ResultSet rSet, List<String> colNames) {
    super();
    this.rSet = rSet;
    this.colNames = colNames;
  }

  public static DBResultSet resultSet(ResultSet rSet, List<String> colNames) {
    return new DBResultSet(rSet, colNames);
  }

  private String nextColName() {
    String name = colNames.get(index);
    index = index + 1;
    return name;
  }

  @Override
  public Result<Tuple<LocalDate, Input>> nextDate() {
    try {
      return Result.success(new Tuple<>(rSet.getObject(nextColName(), LocalDate.class), this));
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  @Override
  public Result<Tuple<String, Input>> nextStr() {
    try {
      return Result.success(new Tuple<>(rSet.getString(nextColName()), this));
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  @Override
  public Result<Tuple<Integer, Input>> nextInt() {
    try {
      return Result.success(new Tuple<>(rSet.getInt(nextColName()), this));
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  @Override
  public Result<Tuple<Double, Input>> nextDbl() {
    try {
      return Result.success(new Tuple<>(rSet.getDouble(nextColName()), this));
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  @Override
  public Result<Tuple<BigDecimal, Input>> nextBigDecimal() {
    try {
      return Result.success(new Tuple<>(rSet.getBigDecimal(nextColName()), this));
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }
}