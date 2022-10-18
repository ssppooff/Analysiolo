import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import stockAPI.DataSource;
import stockAPI.Parser;
import stockAPI.Symbol;
import stockAPI.Transaction;

/* TODO Current task & subtasks:
    * Make sure that after each transactions there is no negative number of shares for each stock
    - When I input further transactions into the db, what is the sorting oder?
    - nShares integer or double or BigDecimal?

 Internal considerations
 - When combining transactions from the database and command input (directly or from a file):
   - you must sort the transactions according to date
   - How do you sort transactions that were done on the same date regarding the same stock?

 Assumptions
 - Transactions in the db are already correctly sorted

 If no date is given/what is the value of the portfolio now?
 - ask Yahoo Finance the prices of the list

 If the user asks for the value of the portfolio at the end of a certain date
 - figure out what stocks were held at the end of that day
 - ask Yahoo Finance the price for each stock
 - sum up and multiply according to the number of shares for each stock

 Metrics to print
 - Current Net value
 - Time weighted return, since inception, year, YTD
 - Per Stock: Current price | avg/mean buying price

 * Finished tasks
   - ~~Input all the transactions into db~~
   - ~~Write methods to write multiple rows into db~~
   - ~~Write methods to read multiple rows into db~~
   - ~~compile list of all stocks held~~
   - ~~After parsing all transactions, make sure that no nShares is negative for any stock~~
   - ~~compile list of all stock held at this current moment~~
   - ~~Connect to H2 database using JDBC, write & read stuff to it~~
   - ~~Stream.java -> flattenResult~~
   - ~~check whether txType and price (pos or neg) corresponds~~
   - ~~Read all the transactions from the db into memory~~
   - ~~When parsing from file, make sure to return failure of createTxWithType()~~
   - ~~List.unfold() where you preserve the Return.failure()~~
   - ~~Write multiple transactions into db in FP style~~
   - ~~Close statements, preparedStatements and database~~
   - ~~Refactor code that gets data out of DataBase, eliminate use of statements and ResultSets
      from MainTest.java~~
   - ~~Can I remove preparedStatements from DataBase.java?~~
   - ~~Refactor into DataSource.java~~
   - ~~Refactor into Parser.java~~
   - ~~Refactor tests into DataSourceTest.java, ParserTest.java and DataBaseTest.java~~
   - ~~Parse data from file, check whether it is valid, if so, put into db~~
   - ~~Sort the newly provided transactions, if some were done on the same day, sort them according to
      the sequence they were provided~~
   - ~~Check whether the new provided transactions are all later (or on the same day) as the latest from
    the db~~
   - ~~Figure out, whether the provided data is sorted in descending or ascending order~~
   - ~~After reading in data, make sure it is sorted correctly -> if note let the user know~~
   - ~~Check for negative stocks after combining the transactions in the db and the supplied file~~
 */

class MainTest {
  String path = "src/test/java/testdata.txt";
  String pathErrorFile = "src/test/java/testdata_error.txt";
  String pathAdditional = "src/test/java/testdata_additional.txt";
  String pathAdditionalError = "src/test/java/testdata_additional_error.txt";

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

  void listPerLine(List<Transaction> l) {
    System.out.println(
        l.foldLeft(new StringBuilder(), s -> tx ->
            s.append(tx.toString()).append("\n")));
  }

  Result<List<Transaction>> readTxFromFile(String path) {
    Result<FileReader> fR = FileReader.read(path);
    Result<List<Transaction>> listTx = fR.flatMap(Parser::parseTransactions);
    assertSuccess(fR.flatMap(FileReader::close));
    return listTx;
  }

  Result<List<Transaction>> prepDataInDS() {
    Result<FileReader> fR = FileReader.read(path);
    Result<List<Transaction>> listTx = fR.flatMap(Parser::parseTransactions);
    assertSuccess(fR.flatMap(FileReader::close));

    Result<DataSource> rDS = DataSource.openInMemory()
        .flatMap(ds -> listTx.flatMap(ds::insertTransaction));
    assertSuccess(rDS);

    Result<Tuple<List<Transaction>, DataSource>> dsRes = rDS.flatMap(DataSource::getTransactions);
    assertSuccess(dsRes.map(Tuple::_2).flatMap(DataSource::close));
    return dsRes.map(Tuple::_1);
  }

  @Test
  void getOrderSeqTest() {
    List<Transaction> lDates = List.of(
        Transaction.transaction(LocalDate.parse("2022-12-12"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-13"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-20"), "SP500", 10, BigDecimal.ONE));
    assertTrue(Main.getOrderSeq(lDates)._1);
    lDates = List.of(
        Transaction.transaction(LocalDate.parse("2022-12-20"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-13"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-12"), "SP500", 10, BigDecimal.ONE));
    assertFalse(Main.getOrderSeq(lDates)._1);
  }

  @Test
  void checkSequenceTest() {
    List<Transaction> lTx = List.of(
        Transaction.transaction(LocalDate.parse("2022-09-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-10-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-11-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-01"), "SP500", 10, BigDecimal.ONE));
    List<Transaction> lTxError = List.of(
        Transaction.transaction(LocalDate.parse("2022-09-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-11-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-10-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-01"), "SP500", 10, BigDecimal.ONE));
    assertSuccess(Main.checkCorrectSequence(lTx, true));

    Result<List<Transaction>> res = assertFailure(Main.checkCorrectSequence(lTxError, true));
    Result<List<Transaction>> expRes = Result.failure("Wrong date after line 2");
    assertEquals(expRes, res);

    lTxError = List.of(
        Transaction.transaction(LocalDate.parse("2022-12-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-10-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-11-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-09-01"), "SP500", 10, BigDecimal.ONE));
    res = assertFailure(Main.checkCorrectSequence(lTxError, false));
    assertEquals(expRes, res);
  }

  @Test
  void parseIntoDataSource() {
    // Prep input data
    Result<FileReader> fR = FileReader.read(path);
    Result<List<Transaction>> listTx = fR.flatMap(Parser::parseTransactions);
    assertSuccess(fR.flatMap(FileReader::close));
    assertSuccess(listTx.map(Parser::parseStocks).flatMap(Parser::checkForNegativeStocks));

    Result<DataSource> rDS = DataSource.openInMemory();
    assertSuccess(rDS);

    // Insert
    rDS = rDS.flatMap(ds -> listTx.flatMap(ds::insertTransaction));
    assertSuccess(rDS);

    // Query & Comparison
    var f = rDS.flatMap(DataSource::getTransactions);
    rDS = f.map(Tuple::_2);
    Result<List<Transaction>> resListTx = f.map(Tuple::_1)
        .map(ltx -> ltx.sortFP(Comparator.comparing(Transaction::getDate)));
    assertEquals(listTx, resListTx);

    // Close data source
    assertSuccess(rDS.flatMap(DataSource::close));
  }

  @Test
  void checkCombinedData() {
    // Read input data (simulates text file or directly from CLI)
    // sort the transactions
    Result<List<Transaction>> listTx = readTxFromFile(pathAdditional)
        .map(Main::getOrderSeq)
        .flatMap(t -> Main.checkCorrectSequence(t._2, t._1))
        .map(l -> l.sortFP(Comparator.comparing(Transaction::getDate).reversed()));
    assertSuccess(listTx);
//    System.out.println(listTx);

    // Get transactions from db
//    dbTx.forEach(this::listPerLine);
    Result<List<Transaction>> dsTx = prepDataInDS();

    // Check whether last transaction from file is from the same day or more recent than last from db
    Result<LocalDate> lastDateDb = assertSuccess(dsTx.map(l -> l.head().getDate()));
    Result<LocalDate> firstDateInput = assertSuccess(listTx.flatMap(List::last).map(Transaction::getDate));

    assertSuccess(lastDateDb.flatMap(dbDate -> firstDateInput.map(inputDate ->
        dbDate.compareTo(inputDate) <= 0)))
        .forEachOrFail(Assertions::assertTrue)
        .forEach(Assertions::fail);

    // Comparison
    dsTx = dsTx.flatMap(ldb -> listTx.map(lTx -> List.concat(lTx, ldb)));
//    dbTx.forEach(this::listPerLine);
    Result<Map<Symbol, Integer>> negStocks = dsTx.map(Parser::parseStocks).flatMap(Parser::checkForNegativeStocks);
    assertSuccess(negStocks);
  }

  @Test
  void checkSequenceError() {
    // Read input data (simulates text file or directly from CLI)
    // sort the transactions
    Result<List<Transaction>> err = readTxFromFile(pathAdditionalError)
        .map(Main::getOrderSeq)
        .flatMap(t -> Main.checkCorrectSequence(t._2, t._1))
        .map(l -> l.sortFP(Comparator.comparing(Transaction::getDate).reversed()));
    assertFailure(err);
  }

  @Test
  void checkCombinedDataWithError() {
    // Read input data (simulates text file or directly from CLI)
    // sort the transactions
    Result<List<Transaction>> listTx = readTxFromFile(pathAdditionalError)
        .map(l -> l.sortFP(Comparator.comparing(Transaction::getDate)));

    // Get transactions from db
    Result<List<Transaction>> dsTx = prepDataInDS();

    // Comparison
    listTx = listTx.flatMap(lTx -> dsTx.map(ldb -> List.concat(ldb, lTx)));
    Result<Map<Symbol, Integer>> negStocks = listTx.map(Parser::parseStocks).flatMap(Parser::checkForNegativeStocks);
    assertFailure(negStocks);
  }
}