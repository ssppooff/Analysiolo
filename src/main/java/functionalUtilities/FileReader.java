package functionalUtilities;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Scanner;

public class FileReader implements Input {
  private final Scanner scanner;
  private FileReader(Scanner scanner) {
    this.scanner = scanner;
  }

  @Override
  public Result<Tuple<String, Input>> nextStr() {
    return scanner.hasNext()
        ? Result.success(new Tuple<>(scanner.next(), this))
        : Result.empty();
  }

  @Override
  public Result<Tuple<LocalDate, Input>> nextDate() {
    return scanner.hasNext()
        ? Result.success(new Tuple<>(LocalDate.parse(scanner.next()), this))
        : Result.empty();
  }

  @Override
  public Result<Tuple<Integer, Input>> nextInt() {
    return scanner.hasNextInt()
        ? Result.success(new Tuple<>(scanner.nextInt(), this))
        : Result.empty();
  }

  @Override
  public Result<Tuple<BigDecimal, Input>> nextBigDecimal() {
    return scanner.hasNextBigDecimal()
        ? Result.success(new Tuple<>(scanner.nextBigDecimal(), this))
        : Result.empty();
  }

  @Override
  public Result<Tuple<Double, Input>> nextDbl() {
    return scanner.hasNextDouble()
        ? Result.success(new Tuple<>(scanner.nextDouble(), this))
        : Result.empty();
  }

  public static Result<FileReader> read(String path) {
    try {
      return Result.success(
          new FileReader(new Scanner(new BufferedReader(new java.io.FileReader(path)))));
    } catch (FileNotFoundException e) {
      return Result.failure(e);
    }
  }

  public Result<Nothing> close() {
    scanner.close();
    return Result.success(Nothing.instance);
  }
}