package functionalUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FileReaderTest {
  String validPath = "src/test/java/testdata.txt";
  String invalidPath = "src/test/java/testdata2.txt";
  static LocalDate date = LocalDate.parse("2022-02-18");
  static String txType = "BUY";
  static String symbol = "VTI";
  static int nShares = +10;
  static BigDecimal price = BigDecimal.valueOf(40.11);

  @BeforeAll
  static void lift() {}

  @Test
  void fileReader() {
    Result<FileReader> rR = FileReader.read(invalidPath);
    assertTrue(rR.isFailure());
    rR = FileReader.read(validPath);
    assertTrue(rR.isSuccess());

    rR.flatMap(FileReader::nextDate)
        .forEachOrFail(t -> assertEquals(date, t._1))
        .forEach(Assertions::fail);
    rR.flatMap(FileReader::nextStr)
        .forEachOrFail(t -> assertEquals(txType, t._1))
        .forEach(Assertions::fail);
    rR.flatMap(FileReader::nextStr)
        .forEachOrFail(t -> assertEquals(symbol, t._1))
        .forEach(Assertions::fail);
    rR.flatMap(FileReader::nextInt)
        .forEachOrFail(t -> assertEquals(nShares, t._1))
        .forEach(Assertions::fail);
    rR.flatMap(FileReader::nextBigDecimal)
        .forEachOrFail(t -> assertEquals(price, t._1))
        .forEach(Assertions::fail);

    assertEquals(Result.empty(), rR.flatMap(FileReader::nextInt));
  }
}