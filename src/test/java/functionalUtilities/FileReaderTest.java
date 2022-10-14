package functionalUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FileReaderTest {
  String validPath = "src/test/java/testdata.txt";
  String invalidPath = "src/test/java/testdata2.txt";
  static LocalDate date = LocalDate.parse("2022-02-18");
  static String txType = "BUY";
  static String symbol = "VTI";
  static int nShares = +10;
  static BigDecimal price = BigDecimal.valueOf(40.11);

  @SuppressWarnings("unused")
  void assertSuccess(Result<?> r) {
    assertTrue(r.isSuccess(), r.toString());
  }

  @SuppressWarnings("unused")
  void assertFailure(Result<?> r) {
    assertTrue(r.isFailure(), r.toString());
  }

  @Test
  void fileReader() {
    Result<FileReader> fR = FileReader.read(invalidPath);
    assertFailure(fR);
    fR = FileReader.read(validPath);
    assertSuccess(fR);

    fR.flatMap(FileReader::nextDate)
        .forEachOrFail(t -> assertEquals(date, t._1))
        .forEach(Assertions::fail);
    fR.flatMap(FileReader::nextStr)
        .forEachOrFail(t -> assertEquals(txType, t._1))
        .forEach(Assertions::fail);
    fR.flatMap(FileReader::nextStr)
        .forEachOrFail(t -> assertEquals(symbol, t._1))
        .forEach(Assertions::fail);
    fR.flatMap(FileReader::nextInt)
        .forEachOrFail(t -> assertEquals(nShares, t._1))
        .forEach(Assertions::fail);
    fR.flatMap(FileReader::nextBigDecimal)
        .forEachOrFail(t -> assertEquals(price, t._1))
        .forEach(Assertions::fail);

    assertEquals(Result.empty(), fR.flatMap(FileReader::nextInt));
    assertSuccess(fR.flatMap(FileReader::close));
  }
}