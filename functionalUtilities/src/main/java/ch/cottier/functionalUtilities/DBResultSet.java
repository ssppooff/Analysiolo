package ch.cottier.functionalUtilities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.function.Function;

public class DBResultSet implements Input {
  public final ResultSet rSet;
  private final java.util.List<String> colNames;
  private int index = 0;

  private DBResultSet (ResultSet rSet, java.util.List<String> colNames) {
    super();
    this.rSet = rSet;
    this.colNames = colNames;
  }

  public static DBResultSet resultSet(ResultSet rSet, java.util.List<String> colNames) {
    return new DBResultSet(rSet, colNames);
  }

  public <T> Result<Tuple<List<T>, DBResultSet>> map(Function<DBResultSet, T> f) {
    try {
      java.util.List<T> l = new ArrayList<>();
      while (rSet.next())
        l.add(f.apply(this));
      return Result.success(new Tuple<>(
          List.of(l), this));
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  public <T> Result<Tuple<List<T>, DBResultSet>> flatMap(Function<DBResultSet, Result<T>> f) {
    return map(f)
        .flatMap(t -> List.flattenResult(t._1)
                          .map(l -> new Tuple<>(l, t._2)));
  }

  public <T> Result<Tuple<List<T>, DBResultSet>> mapInput(Function<DBResultSet, Tuple<T, Input>> f) {
    return map(x -> f.apply(x)._1);
  }

  public <T> Result<Tuple<List<T>, DBResultSet>> flatMapInput(Function<DBResultSet, Result<Tuple<T, Input>>> f) {
    return flatMap(x -> f.apply(x).map(t -> t._1));
  }

  private String nextColName() {
    String name = colNames.get(index);
    index = (index + 1) % colNames.size();
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
      return Result.success(new Tuple<>(rSet.getBigDecimal(nextColName())
                                            .setScale(6, RoundingMode.HALF_UP), this));
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }
}