package functionalUtilities;

import java.time.LocalDate;

public interface Input {
  public Result<Tuple<Integer, Input>> nextInt();
  public Result<Tuple<Double, Input>> nextDbl();
  public Result<Tuple<String, Input>> nextStr();
  public Result<Tuple<LocalDate, Input>> nextDate();
}