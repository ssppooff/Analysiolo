import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import functionalUtilities.FileReader;
import functionalUtilities.List;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import stockAPI.DataSource;
import stockAPI.Parser;
import stockAPI.Transaction;

/* TODO Current task & subtasks:
    * Check for negative stocks after combining the transactions in the db and the supplied file
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
 */

class MainTest {
  String path = "src/test/java/testdata.txt";

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
}