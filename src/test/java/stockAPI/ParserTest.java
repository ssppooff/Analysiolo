package stockAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import functionalUtilities.FileReader;
import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ParserTest {
  String path = "src/test/java/testdata.txt";
  String pathErrorFile = "src/test/java/testdata_error.txt";

  // Data for test transaction
  static LocalDate date = LocalDate.parse("2022-02-18");
  static String symbol = "VTI";
  static int nShares = 10;
  static BigDecimal price = new BigDecimal("40.11");

  @SuppressWarnings("unused")
  void assertSuccess(Result<?> r) {
    assertTrue(r.isSuccess(), r.toString());
  }

  @SuppressWarnings("unused")
  void assertFailure(Result<?> r) {
    assertTrue(r.isFailure(), r.toString());
  }

  @Test
  void createTxTest() {
    Result<FileReader> fR = FileReader.read(pathErrorFile);
    Result<Transaction> rTx = fR.flatMap(Parser::createTxWithCheck)
        .flatMap(Tuple::_1);
    assertFailure(rTx);

    Transaction expTx = Transaction.transaction(date, symbol, nShares, price);
    rTx = FileReader.read(path)
        .flatMap(Parser::createTxWithCheck)
        .flatMap(Tuple::_1);
    rTx.forEachOrFail(tx -> assertEquals(expTx, tx))
        .forEach(Assertions::fail);

    assertSuccess(fR.flatMap(FileReader::close));
  }

  @Test
  void parseTest() {
    Result<FileReader> rFR = FileReader.read(pathErrorFile);
    assertFailure(rFR.flatMap(Parser::parseTransactions));
    assertSuccess(rFR.flatMap(FileReader::close));

    rFR = FileReader.read(path);
    Result<Map<Symbol, Integer>> rStocks = rFR
        .flatMap(Parser::parseTransactions)
        .flatMap(lTx -> lTx.fpStream()
            .foldLeft(Result.success(Map.empty()), Parser::checkForNegativeStock));
    assertSuccess(rStocks);
    assertSuccess(rFR.flatMap(FileReader::close));

    rStocks.forEach(map -> map.get(Symbol.symbol("VXUS"))
        .forEachOrFail(nShares -> assertEquals(334, nShares))
        .forEach(Assertions::fail));

    Transaction expTx = Transaction.transaction(date, symbol, nShares, price);
    rFR = FileReader.read(path);
    rFR.flatMap(Parser::createTxWithCheck)
        .forEach(t -> t._1.forEachOrFail(tx ->
            assertEquals(expTx, tx)).forEach(Assertions::fail));
    assertSuccess(rFR.flatMap(FileReader::close));
  }
}