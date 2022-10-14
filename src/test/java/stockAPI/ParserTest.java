package stockAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import functionalUtilities.FileReader;
import functionalUtilities.List;
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
    Result<Transaction> rTx = FileReader.read(pathErrorFile)
        .flatMap(Parser::createTxWithCheck)
        .flatMap(Tuple::_1);
    assertFailure(rTx);

    Transaction expTx = Transaction.transaction(date, symbol, nShares, price);
    rTx = FileReader.read(path)
        .flatMap(Parser::createTxWithCheck)
        .flatMap(Tuple::_1);
    rTx.forEachOrFail(tx -> assertEquals(expTx, tx))
        .forEach(Assertions::fail);
  }

  @Test
  void parseTest() {
    assertFailure(FileReader.read(pathErrorFile)
        .flatMap(Parser::parseTransactions));

    Result<Map<Symbol, Integer>> rStocks = FileReader.read(pathErrorFile)
        .map(fR -> List.unfold(fR, Parser::createTxWithCheck))
        .flatMap(l -> List.flattenResult(l.filter(Result::isSuccess)))
        .map(Parser::parseStocks)
        .flatMap(Parser::checkForNegativeStocks);
    assertFailure(rStocks);

    rStocks = FileReader.read(path)
        .flatMap(Parser::parseTransactions)
        .map(Parser::parseStocks)
        .flatMap(Parser::checkForNegativeStocks);
    assertSuccess(rStocks);

    rStocks.forEach(map -> map.get(Symbol.symbol("VXUS"))
        .forEachOrFail(nShares -> assertEquals(334, nShares))
        .forEach(Assertions::fail));

    Transaction expTx = Transaction.transaction(date, symbol, nShares, price);
    FileReader.read(path)
        .flatMap(Parser::createTxWithCheck)
        .forEach(t -> t._1.forEachOrFail(tx ->
            assertEquals(expTx, tx)).forEach(Assertions::fail));
  }
}