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
import java.util.Comparator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ParserTest {
  String path = "src/test/java/testdata.txt";
  String pathErrorFile = "src/test/java/testdata_error.txt";
  String pathAdditional = "src/test/java/testdata_additional.txt";
  String pathAdditionalStocksError = "src/test/java/testdata_additional_stocksError.txt";

  // Data for test transaction
  static LocalDate date = LocalDate.parse("2021-02-18");
  static String symbol = "VTI";
  static int nShares = 10;
  static BigDecimal price = new BigDecimal("40.11");

  @SuppressWarnings("unused")
  <T> Result<T> assertSuccess(Result<T> r) {
    assertTrue(r.isSuccess(), r.toString());
    return r;
  }

  @SuppressWarnings("unused")
  <T> Result<T> assertFailure(Result<T> r) {
    assertTrue(r.isFailure(), r.toString());
    return r;
  }

  Result<List<Transaction>> readTxFromFile(String path) {
    Result<FileReader> fR = FileReader.read(path);
    Result<List<Transaction>> listTx = fR.flatMap(Parser::parseTransactions)
        .map(l -> l._1.sortFP(Comparator.comparing(Transaction::getDate)));
    assertSuccess(fR.flatMap(FileReader::close));
    return listTx;
  }

  Result<DataSource> inputDataIntoDS() {
    Result<FileReader> fR = FileReader.read(path);
    Result<List<Transaction>> listTx = fR.flatMap(Parser::parseTransactions).map(Tuple::_1);
    assertSuccess(fR.flatMap(FileReader::close));

    return assertSuccess(DataSource.openInMemory()
        .flatMap(ds -> listTx.flatMap(ds::insertTransactions)));
  }

  Result<Tuple<List<Transaction>, LocalDate>> prepDataInDS() {
    Result<Tuple<List<Transaction>, Tuple<LocalDate, DataSource>>> dsRes = inputDataIntoDS()
        .flatMap(DataSource::getTransactions)
        .flatMap(t1 -> t1._2.getLastDate().map(t2 -> new Tuple<>(t1._1, new Tuple<>(t2._1, t2._2))));
    assertSuccess(dsRes.map(Tuple::_2).map(Tuple::_2).flatMap(DataSource::close));
    return dsRes.map(t -> new Tuple<>(t._1, t._2._1));
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
        .flatMap(t -> t._1.fpStream()
            .foldLeft(Result.success(Map.empty()), Parser::checkForNegativeStock));
    assertSuccess(rStocks);
    assertSuccess(rFR.flatMap(FileReader::close));

    rStocks.forEach(map -> map.get(Symbol.symbol("VXUS"))
        .forEachOrFail(nShares -> assertEquals(0, nShares))
        .forEach(Assertions::fail));

    Transaction expTx = Transaction.transaction(date, symbol, nShares, price);
    rFR = FileReader.read(path);
    rFR.flatMap(Parser::createTxWithCheck)
        .forEach(t -> t._1.forEachOrFail(tx ->
            assertEquals(expTx, tx)).forEach(Assertions::fail));
    assertSuccess(rFR.flatMap(FileReader::close));
  }

  @Test
  void checkForNegativeStocks() {
    // Get transactions from db
    Result<Map<Symbol, Integer>> stocks = assertSuccess(prepDataInDS()
        .map(Tuple::_1)
        .map(Parser::parsePositions));

    // Read input data (simulates text file or directly from CLI) & sort the transactions
    Result<List<Transaction>> listTx = readTxFromFile(pathAdditional);
    assertSuccess(listTx);

    // Check after every transaction if the number of shares for any stock is < 0
    Result<Map<Symbol, Integer>> res = listTx.flatMap(l -> l.fpStream().
        foldLeft(stocks, Parser::checkForNegativeStock));
    assertSuccess(res);
  }

  @Test
  void negativeStocksErrorTest() {
    // Get transactions from datasource & compute what stocks are held
    Result<Map<Symbol, Integer>> stocks = assertSuccess(prepDataInDS().map(Tuple::_1).map(Parser::parsePositions));

    // Read input data (simulates text file or directly from CLI) & sort the transactions
    Result<List<Transaction>> listTx = readTxFromFile(pathAdditionalStocksError);

    // Check after every transaction if the number of shares for any stock is < 0
    Result<Map<Symbol, Integer>> res = listTx.flatMap(l -> l.fpStream().foldLeft(stocks, Parser::checkForNegativeStock));
    assertFailure(res);
  }
}