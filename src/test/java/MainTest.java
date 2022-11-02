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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import stockAPI.DataSource;
import stockAPI.Parser;
import stockAPI.Symbol;
import stockAPI.Transaction;

/* TODO Current task & subtasks:
    * Write CLI parser so that it can be used as a cLI tool
    * Check all symbols input by the user -> replace Symbol.name without Result<String>
    * MWRR & AIRR?
    - When I input further transactions into the db, what is the sorting oder?
    - nShares integer or double or BigDecimal?

# CLI call options
* Create a new database and add some transactions
$ portfolio --new-database db-name(.db) --ingest file-with-transactions

* Add some transactions into the database
$ portfolio demo.db --ingest file-with-additional-transactions

* Compute the current value of the portfolio
$ portfolio demo.db
$ portfolio demo.db value

* Compute the value of the portfolio on a certain date
$ portfolio demo.db value 2021-12-31

* Compute the TWRR between since inception, 1 year, YTD, between two dates
$ portfolio demo.db twrr inception
$ portfolio demo.db twrr one-year
$ portfolio demo.db twrr ytd
$ portfolio demo.db twrr 2021-01-01 2021-10-31

* Compute the weighted avg acquisition price for each stock held: currently, at a specific date
$ portfolio demo.db avgCost
$ portfolio demo.db avgCost 2021-10-10
$ portfolio demo.db --filter=TSLA avgCost 2021-10-10

* Filter the used transactions to a specific stock
$ portfolio demo.db --filter=TSLA
$ portfolio demo.db --filter=TSLA,AVUV

* Get price of a specific stock: current, specific date
$ portfolio --get-price TSLA
$ portfolio --get-price TSLA 2021-10-10

 If no date is given/what is the value of the portfolio now?
 - ask Yahoo Finance the prices of the list

 If the user asks for the value of the portfolio at the end of a certain date
 - ~~figure out what stocks were held at the end of that day~~
 - ~~ask Yahoo Finance the price for each stock~~
 - ~~sum up and multiply according to the number of shares for each stock~~

 Metrics to print
 - ~~Current Net value~~
 - ~~Net value at a specific date~~
 - ~~Time weighted return, since inception, year, YTD~~
 - ~~Per Stock: Current price | avg/mean buying price~~

# Internal considerations
 - When combining transactions from the database and command input (directly or from a file):
   - you must sort the transactions according to date
   - How do you sort transactions that were done on the same date regarding the same stock?

## Assumptions
 - Transactions in the db are already correctly sorted

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
   - ~~Make sure that after each transactions there is no negative number of shares for each stock~~
   - ~~after having checked the new transactions, input them into the db~~
   - ~~Compute weighted average of stock purchase for each stock
       -> ((nShares * price) for each tx)/totShares in portfolio~~
   - ~~Create function to get value of 1 and multiple stocks, now and at specific date~~
   - ~~Time-Weighted Rate of Return (TWRR)~~
   - ~~Fix wrong dates in test data 2022 -> 2021~~
   - ~~Refactor into Portfolio class~~
 */

class AnalysioloTest {
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

  @SuppressWarnings("unused")
  void listPerLine(List<Transaction> l) {
    System.out.println(
        l.foldLeft(new StringBuilder(), s -> tx ->
            s.append(tx.toString()).append("\n")));
  }

  Result<List<Transaction>> readTxFromFile(String path) {
    Result<FileReader> fR = FileReader.read(path);
    Result<List<Transaction>> listTx = fR.flatMap(Parser::parseTransactions)
        .map(Tuple::_1)
        .map(Analysiolo::getOrderSeq)
        .flatMap(t -> Analysiolo.checkCorrectSequence(t._2, t._1))
        .map(l -> l.sortFP(Comparator.comparing(Transaction::getDate)));
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
  void fileNameVerifier() {
    assertEquals("", Analysiolo.removePossibleExtensions(""));

    String path = "absolute/path/to/testdb";
    String expPath = "absolute/path/to/testdb";
    assertEquals(path, Analysiolo.removePossibleExtensions(path));

    path = "absolute/path/to/testdb.db";
    expPath = "absolute/path/to/testdb";
    assertEquals(expPath, Analysiolo.removePossibleExtensions(path));

    path = "absolute/path/to/testdb.mv.db";
    expPath = "absolute/path/to/testdb";
    assertEquals(expPath, Analysiolo.removePossibleExtensions(path));

    path = "absolute/path.db/to/testdb.mv.db";
    expPath = "absolute/path.db/to/testdb";
    assertEquals(expPath, Analysiolo.removePossibleExtensions(path));

    path = "absolute/path.db/to/testdb";
    expPath = "absolute/path.db/to/testdb";
    assertEquals(expPath, Analysiolo.removePossibleExtensions(path));

    path = "absolute with spaces/path.db/to/testdb.db";
    expPath = "absolute with spaces/path.db/to/testdb";
    assertEquals(expPath, Analysiolo.removePossibleExtensions(path));
  }

  @Test
  void getOrderSeqTest() {
    List<Transaction> lDates = List.of(
        Transaction.transaction(LocalDate.parse("2022-12-12"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-13"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-20"), "SP500", 10, BigDecimal.ONE));
    assertTrue(Analysiolo.getOrderSeq(lDates)._1);
    lDates = List.of(
        Transaction.transaction(LocalDate.parse("2022-12-20"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-13"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-12"), "SP500", 10, BigDecimal.ONE));
    assertFalse(Analysiolo.getOrderSeq(lDates)._1);
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
    assertSuccess(Analysiolo.checkCorrectSequence(lTx, true));

    Result<List<Transaction>> res = assertFailure(Analysiolo.checkCorrectSequence(lTxError, true));
    Result<List<Transaction>> expRes = Result.failure("Wrong date after line 2");
    assertEquals(expRes, res);

    lTxError = List.of(
        Transaction.transaction(LocalDate.parse("2022-12-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-10-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-11-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-09-01"), "SP500", 10, BigDecimal.ONE));
    res = assertFailure(Analysiolo.checkCorrectSequence(lTxError, false));
    assertEquals(expRes, res);
  }

  @Test
  void parseIntoDataSource() {
    // Read in input data, make sure no shares are negative after any transaction
    Result<List<Transaction>> listTx = readTxFromFile(path);
    assertSuccess(listTx);
    assertSuccess(listTx.flatMap(l -> l.fpStream()
        .foldLeft(Result.success(Map.empty()), Parser::checkForNegativeStock)));

    Result<DataSource> rDS = assertSuccess(DataSource.openInMemory());

    // Insert
    rDS = rDS.flatMap(ds -> listTx.flatMap(ds::insertTransactions));
    assertSuccess(rDS);

    // Query & Comparison
    var f = rDS.flatMap(DataSource::getTransactions);
    Result<List<Transaction>> resListTx = f.map(Tuple::_1)
        .map(ltx -> ltx.sortFP(Comparator.comparing(Transaction::getDate)));
    assertEquals(listTx, resListTx);

    // Close data source
    assertSuccess(f.map(Tuple::_2).flatMap(DataSource::close));
  }

  @Test
  void checkCombinedData() {
    // Read input data (simulates text file or directly from CLI)
    // sort the transactions
    Result<List<Transaction>> listTx = readTxFromFile(pathAdditional);
    assertSuccess(listTx);

    // get transactions and last date from db
    Result<Tuple<List<Transaction>, LocalDate>> dsRes = assertSuccess(prepDataInDS());
    Result<List<Transaction>> dsTx = dsRes.map(Tuple::_1);
    Result<LocalDate> lastDateDb = dsRes.map(Tuple::_2);
    Result<Map<Symbol, Integer>> dbStocks = dsTx.map(Parser::parsePositions);

    // Check whether last transaction from file is from the same day or more recent than last from db
    Result<LocalDate> firstDateInput = assertSuccess(listTx.map(l -> l.head().getDate()));

    assertSuccess(lastDateDb.flatMap(dbDate -> firstDateInput.map(inputDate ->
        dbDate.compareTo(inputDate) <= 0)))
        .forEachOrFail(Assertions::assertTrue)
        .forEach(Assertions::fail);

    // Check whether there are any negative number of shares after every new transaction
    Result<Map<Symbol, Integer>> res = listTx.flatMap(l -> l.fpStream()
        .foldLeft(dbStocks, Parser::checkForNegativeStock));
    assertSuccess(res);
  }

  @Test
  void checkSequenceError() {
    // Read input data (simulates text file or directly from CLI)
    // sort the transactions
    Result<List<Transaction>> err = readTxFromFile(pathAdditionalError);
    assertFailure(err);
  }

  @Test
  void checkCombinedDataWithError() {
    // Read data in manually due to errors
    Result<FileReader> fR = FileReader.read(pathAdditionalError);
    Result<List<Transaction>> listTx = fR.flatMap(Parser::parseTransactions)
        .map(t -> t._1.sortFP(Comparator.comparing(Transaction::getDate)));
    assertSuccess(fR.flatMap(FileReader::close));
    assertSuccess(listTx);

    // Get transactions from db
    Result<List<Transaction>> dsTx = prepDataInDS().map(Tuple::_1);
    Result<Map<Symbol, Integer>> dbStocks = dsTx.map(Parser::parsePositions);

    // Check that there is a point where the number of shares becomes negative
    Result<Map<Symbol, Integer>> negStocks = listTx.flatMap(lTx -> lTx.fpStream()
        .foldLeft(dbStocks, Parser::checkForNegativeStock));
    assertFailure(negStocks);
  }

  @Test
  void combineDataIntoDS() {
    // Prep data source & get relevant data out of it
    Result<Tuple<List<Transaction>, Tuple<LocalDate, DataSource>>> dsRes =
        inputDataIntoDS()
        .flatMap(DataSource::getTransactions)
        .flatMap(t1 -> t1._2.getLastDate().map(t2 -> new Tuple<>(t1._1, new Tuple<>(t2._1, t2._2))));

    Result<List<Transaction>> dsTx = dsRes.map(Tuple::_1);
    Result<LocalDate> lastDateDb = dsRes.map(t -> t._2._1);
    Result<DataSource> rDS = dsRes.map(t -> t._2._2);
    Result<Map<Symbol, Integer>> dbStocks = dsTx.map(Parser::parsePositions);

    // Read input data (simulates text file or directly from CLI)
    Result<List<Transaction>> listTx = assertSuccess(readTxFromFile(pathAdditional));

    // Check whether last transaction from file is from the same day or more recent than last from db
    Result<LocalDate> firstDateInput = assertSuccess(listTx.map(l -> l.head().getDate()));
    assertSuccess(lastDateDb.flatMap(dbDate -> firstDateInput.map(inputDate ->
        dbDate.compareTo(inputDate) <= 0)))
        .forEachOrFail(Assertions::assertTrue)
        .forEach(Assertions::fail);

    // Check whether there are any negative number of shares after every new transaction
    Result<Map<Symbol, Integer>> resStocks = listTx.flatMap(l -> l.fpStream()
        .foldLeft(dbStocks, Parser::checkForNegativeStock));
    assertSuccess(resStocks);

    // Insert new transactions into DS
    rDS = assertSuccess(rDS.flatMap(ds -> listTx.flatMap(ds::insertTransactions)));
    Result<List<Transaction>> expList = listTx.flatMap(lTx -> dsTx.map(lds -> List.concat(lds, lTx)));
    var res = rDS.flatMap(DataSource::getTransactions);
    assertSuccess(res);
    expList.forEach(expL -> res.map(Tuple::_1).forEach(l -> assertEquals(expL, l)));

    // Close data source
    assertSuccess(res.map(Tuple::_2).flatMap(DataSource::close));
  }
}