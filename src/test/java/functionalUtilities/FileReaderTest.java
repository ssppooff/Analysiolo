package functionalUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FileReaderTest {
  String validPath = "src/test/java/testdata.txt";
  String invalidPath = "src/test/java/testdata2.txt";
  static LocalDate date = LocalDate.parse("2022-09-10");
  static String txType = "BUY";
  static String symbol = "AVUV";
  static int nShares = -200;
  static double buyPrice = 30.00;
  static int buyBasePrice = 3000;

  @BeforeAll
  static void lift() {}

  @Test
  void fileReader() {
    Result<FileReader> rR = FileReader.read(invalidPath);
    assertTrue(rR.isFailure());
    rR = FileReader.read(validPath);
    assertTrue(rR.isSuccess());

    rR.flatMap(FileReader::nextDate).forEach(t -> assertEquals(date, t._1));
    rR.flatMap(FileReader::nextStr).forEach(t -> assertEquals(txType, t._1));
    rR.flatMap(FileReader::nextStr).forEach(t -> assertEquals(symbol, t._1));
    rR.flatMap(FileReader::nextInt).forEach(t -> assertEquals(nShares, t._1));
    rR.flatMap(FileReader::nextDbl)
        .forEach(t -> assertEquals(buyPrice * 100, t._1));

    assertEquals(Result.empty(), rR.flatMap(FileReader::nextInt));
  }
}