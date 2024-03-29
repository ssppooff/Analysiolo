package ch.cottier.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cottier.app.Options.DBOptions;
import ch.cottier.app.Options.TimeFilter;
import ch.cottier.functionalUtilities.FileReader;
import ch.cottier.functionalUtilities.List;
import ch.cottier.functionalUtilities.Map;
import ch.cottier.functionalUtilities.Result;
import ch.cottier.functionalUtilities.Tuple;
import ch.cottier.stockAPI.DataSource;
import ch.cottier.stockAPI.Parser;
import ch.cottier.stockAPI.Symbol;
import ch.cottier.stockAPI.Transaction;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AnalysioloTest {
  String path = "src/test/resources/testdata.txt";
//  String pathErrorFile = "src/test/resources/testdata_error.txt";
  String pathAdditional = "src/test/resources/testdata_additional.txt";
  String pathAdditionalError = "src/test/resources/testdata_additional_error.txt";

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
        .map(Utilities::getOrderSeq)
        .flatMap(t -> Utilities.checkCorrectSequence(t._2, t._1))
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

  Options.DBOptions prepDBOptions() {
    Options.DBOptions dbOptions = new DBOptions();
    dbOptions.dbPath = null;
    dbOptions.newDBPath = new File("src/test/resources/testdb.mv.db");
    if (dbOptions.newDBPath.exists())
      assertTrue(dbOptions.newDBPath.delete());

    return dbOptions;
  }

  Result<Boolean> deleteDB(Options.DBOptions dbOptions) {
    if (dbOptions != null) {
      File path = dbOptions.newDBPath == null
          ? dbOptions.dbPath
          : dbOptions.newDBPath;
      assertTrue(path.exists());
      assertTrue(path.delete());
      return Result.success(true);
    }
    return Result.failure("db is null");
  }

  @Test
  void listTest() {
    Options options = new Options();
    options.dbOptions = prepDBOptions();
    options.timeFilter = new TimeFilter();

    options.timeFilter.period = java.util.List.of("2021-05-01", "2021-11-30");

    options.symbol = java.util.List.of("VTI");
    options.txFile = new File("src/test/resources/testdata.txt");

    Result<List<Transaction>> res = Analysiolo.list_(options);
    assertSuccess(res)
        .forEachOrFail(lTx -> assertEquals(3, lTx.size()))
        .forEach(Assertions::fail);
    assertSuccess(deleteDB(options.dbOptions));

    options.symbol = null;
    options.timeFilter.date = LocalDate.parse("2021-09-08");
    options.timeFilter.period = null;
    res = Analysiolo.list_(options);
    assertSuccess(res)
        .forEachOrFail(lTx -> assertEquals(5, lTx.size()))
        .forEach(Assertions::fail);
    assertSuccess(deleteDB(options.dbOptions));

    options.symbol = java.util.List.of("MSFT");
    res = Analysiolo.list_(options);
    assertTrue(res.isEmpty());

    options.dbOptions.dbPath = new File("src/test/resources/testdb.mv.db");
    options.dbOptions.newDBPath = null;
    options.timeFilter = null;
    options.txFile = null;
    options.symbol = null;
    res = Analysiolo.list_(options);
    assertSuccess(res)
        .forEachOrFail(lTx -> assertEquals(13, lTx.size()))
        .forEach(Assertions::fail);
    assertSuccess(deleteDB(options.dbOptions));
  }

  @Test
  void valueTest() {
    Options options = new Options();
    options.dbOptions = prepDBOptions();
    options.timeFilter = null;
    options.symbol = java.util.List.of("VTI");
    options.txFile = new File("src/test/resources/testdata.txt");

    var res = assertSuccess(Analysiolo.value_(options));
    BigDecimal expValue;
    res.forEachOrFail(l -> assertFalse(l.isEmpty()))
        .forEach(Assertions::fail);
    res.forEach(l -> System.out.println("Current value, filtering for stocks "
        + options.symbol.toString() + ": " + l.head()._2));

    // Don't recreate database each time
    options.dbOptions.dbPath = new File("src/test/resources/testdb.mv.db");
    options.dbOptions.newDBPath = null;

    options.txFile = null;
    options.symbol = null;

    String dateStr1 = "2021-11-07";
    String dateStr2 = "2022-11-07";
//    options.tfOptions = prepTimeFilterOptions();
    options.timeFilter = new TimeFilter();
    options.timeFilter.date = LocalDate.parse(dateStr1);
    res = Analysiolo.value_(options);
    expValue = new BigDecimal("158375.242809");
    assertSuccess(res)
        .forEachOrFail(l -> assertEquals(expValue, l.head()._2))
        .forEach(Assertions::fail);

    options.timeFilter.date = null;
    options.timeFilter.period = java.util.List.of(dateStr1);
    res = Analysiolo.value_(options);
    assertSuccess(res)
        .forEachOrFail(l -> assertEquals(expValue, l.head()._2))
        .forEach(Assertions::fail);

    options.timeFilter.period = java.util.List.of(dateStr1, dateStr2);
    res = Analysiolo.value_(options);
    assertSuccess(res)
        .forEachOrFail(l -> {
          assertEquals(new BigDecimal("158375.242809"), l.head()._2);
          assertEquals(new BigDecimal("171784.663604"), l.tail().head()._2);})
        .forEach(Assertions::fail);

    // Clean up database
    assertSuccess(deleteDB(options.dbOptions));
  }

  static Result<List<List<BigDecimal>>> resultPrices(TimeFilter tf,
      java.util.List<String> symbols) {
    return Analysiolo.price_(tf, symbols)
        .map(outerL -> outerL.map(Tuple::_2)
            .map(innerL -> innerL.map(Tuple::_2)));
  }

  static boolean withinOnePercent(final BigDecimal expected, final BigDecimal actual) {
    BigDecimal exp = expected.setScale(6, RoundingMode.HALF_UP);
    BigDecimal act = actual.setScale(6, RoundingMode.HALF_UP);
    BigDecimal margin = exp.multiply(BigDecimal.valueOf(0.01)
                                               .setScale(6, RoundingMode.HALF_UP));
    return exp.subtract(act).abs().compareTo(margin) <= 0;
  }

  // price (date, period): db & tx file ignored, needs stock filter
  //  - no date or period -> current price of each stock
  //  - date -> price of each stock on date (make sure date is today or before)
  //  - period -> for each date (make sure date is today or before): price of each stock, add change
  //    metrics
  @Test
  void priceTest() {
    String dateStr1 = "2021-11-07";
    String dateStr2 = "2022-11-07";

    Options options = new Options();
    options.symbol = java.util.List.of("TSLA", "VTI");

    // no --date or --period -> current price
    var res = Analysiolo.price_(null, options.symbol)
        .map(l -> l.head()._2);
    Result<List<BigDecimal>> currPrices = assertSuccess(res
        .map(l -> l.map(Tuple::_2)));
    res.forEach(l -> l
        .forEach(t -> System.out.println("Current price of " + t.toString(":"))));

    // --date -> if today -> current price otherwise price on given date
    options.timeFilter = new TimeFilter();
    options.timeFilter.date = LocalDate.now();
    assertEquals(currPrices, resultPrices(options.timeFilter, options.symbol).map(List::head));

    options.symbol = java.util.List.of("TSLA");
    options.timeFilter.date = LocalDate.parse(dateStr1);
    assertEquals(Result.success(new BigDecimal("407.363342")),
        resultPrices(options.timeFilter, options.symbol).map(l -> l.head().head()));

    // --period -> if only size() == 1 && today: same as --date=today, if size() == 1: price on
    // first date and today, if size() == 2 price on both dates
    options.timeFilter.date = null;
    options.timeFilter.period = java.util.List.of(LocalDate.now().toString());
    options.symbol = java.util.List.of("TSLA", "VTI");
    Result<List<Tuple<BigDecimal, BigDecimal>>> combinedPrices =
        resultPrices(options.timeFilter, options.symbol).map(List::head)
                                                        .flatMap(resP -> currPrices.map(resP::zip));
    Result<Boolean> withinMargin = combinedPrices
        .map(lT -> lT
            .map(t -> withinOnePercent(t._1, t._2))
            .reduce(Boolean::logicalAnd));
    withinMargin.forEach(Assertions::assertTrue);


    options.timeFilter.period = java.util.List.of(dateStr1);
    Result<List<List<BigDecimal>>> resPrices1 = resultPrices(options.timeFilter, options.symbol);
    List<List<BigDecimal>> expPrices1 = List.of(
        List.of(new BigDecimal("407.363342"), new BigDecimal("242.360001")),
        currPrices.getOrThrow());

    Result<List<List<Tuple<BigDecimal, BigDecimal>>>> combinedData = resPrices1.map(resP -> List
        .zip(expPrices1, resP).map(outerT -> List
            .zip(outerT._1, outerT._2)));

    withinMargin = combinedData.map(outerL -> outerL
        .map(innerL -> innerL
            .map(pricesT -> withinOnePercent(pricesT._1, pricesT._2))
            .reduce(Boolean::logicalAnd))
        .reduce(Boolean::logicalAnd));
    withinMargin.forEach(Assertions::assertTrue);

    options.timeFilter.period = java.util.List.of(dateStr1, dateStr2);
    List<List<BigDecimal>> expPrices2 = List.of(
        List.of(new BigDecimal("407.363342"), new BigDecimal("242.360001")),
        List.of(new BigDecimal("197.080002"), new BigDecimal("190.660004")));
    assertEquals(Result.success(expPrices2), resultPrices(options.timeFilter, options.symbol));
  }

  // avgCost (date, period): avgCost for each stock over filtered transactions, always filter stocks
  //  - no date or period -> all transactions
  //  - date -> filter transactions up to & incl. date
  //  - period -> transactions between two dates (inclusive)
  @Test
  void avgCostTest() {
    Options options = new Options();
    options.dbOptions = prepDBOptions();
    options.timeFilter = null;
    String dateStr1 = "2021-10-12";
    String dateStr2 = "2022-11-07";

    // 1. new database, ingest txs -> stock filter AVUV
    options.dbOptions.newDBPath = new File("src/test/resources/testdb.mv.db");
    options.txFile = new File("src/test/resources/testdata.txt");
    options.symbol = java.util.List.of("AVUV");
    Result<Map<Symbol, List<BigDecimal>>> res = Analysiolo.avgCost_(options);

    Map<Symbol, List<BigDecimal>> expRes1 =
        Map.<Symbol, List<BigDecimal>>empty().put(Symbol.symbol("AVUV"),
            List.of(new BigDecimal("38.058824"),
                    new BigDecimal("29.000000"),
                    new BigDecimal("40.000000")));
    res.forEachOrFail(m -> assertEquals(expRes1, m)).forEach(Assertions::fail);

    // 2. existing database, no ingesting -> stock filter TSLA, VTI
    options.dbOptions.dbPath = new File("src/test/resources/testdb.mv.db");
    options.dbOptions.newDBPath = null;
    options.txFile = null;
    options.symbol = java.util.List.of("TSLA", "VTI");
    Map<Symbol, List<BigDecimal>> expRes2 =
        Map.<Symbol, List<BigDecimal>>empty().put(Symbol.symbol("VTI"),
            List.of(new BigDecimal("43.109451"),
                new BigDecimal("40.110000"),
                new BigDecimal("90.000000")));
    res = Analysiolo.avgCost_(options);
    res.forEachOrFail(m -> assertEquals(expRes2, m)).forEach(Assertions::fail);

    // 3. existing database, no ingesting -> date & single stock VTI
    options.timeFilter = new TimeFilter();
    options.timeFilter.date = LocalDate.parse(dateStr1);
    options.symbol = java.util.List.of("VTI");
    Map<Symbol, List<BigDecimal>> expRes3 =
        Map.<Symbol, List<BigDecimal>>empty().put(Symbol.symbol("VTI"),
            List.of(new BigDecimal("85.464545"),
                new BigDecimal("40.110000"),
                new BigDecimal("90.000000")));
    res = Analysiolo.avgCost_(options);
    res.forEachOrFail(m -> assertEquals(expRes3, m)).forEach(Assertions::fail);

    // 4. existing database, no ingesting -> two periods & single stock VTI
    options.timeFilter.date = null;
    options.timeFilter.period = List.of(dateStr1, dateStr2);
    Map<Symbol, List<BigDecimal>> expRes4 =
        Map.<Symbol, List<BigDecimal>>empty().put(Symbol.symbol("VTI"),
            List.of(new BigDecimal("43.121260"),
                new BigDecimal("41.200000"),
                new BigDecimal("90.000000")));
    res = Analysiolo.avgCost_(options);
    res.forEachOrFail(m -> assertEquals(expRes4, m)).forEach(Assertions::fail);

    // No transaction with filtered stock
    options.symbol = java.util.List.of("TSLA");
    res = Analysiolo.avgCost_(options);
    assertTrue(res.isEmpty());

    // Clean up database
    assertSuccess(deleteDB(options.dbOptions));
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

  @Test
  void growthFactorsTest() {
//    var stock = Stock.stock("VTI", LocalDate.parse("2022-06-10")).getOrThrow();
//    var foo = List.of(
//        new Tuple<>(LocalDate.parse("2022-08-10"), stock.getPriceOn(LocalDate.parse("2022-08-10"))),
//        new Tuple<>(LocalDate.parse("2022-09-10"), stock.getPriceOn(LocalDate.parse("2022-09-10"))),
//        new Tuple<>(LocalDate.parse("2022-10-10"), stock.getPriceOn(LocalDate.parse("2022-10-10"))),
//        new Tuple<>(LocalDate.parse("2022-11-10"), stock.getPriceOn(LocalDate.parse("2022-11-10"))),
//        new Tuple<>(LocalDate.parse("2022-11-28"), stock.getPriceOn(LocalDate.parse("2022-11-28"))));
//    List.flattenResult(foo.map(Tuple::flattenResultRight)).forEachOrFail(System.out::println)
//        .forEach(err -> System.out.println("err: " + err));

    /* VTI prices
    2022-08-10: 211.270004
    2022-09-10: 204.449997
    2022-10-10: 180.949997
    2022-11-10: 198.139999
    2022-11-28: 198.270004
    */

    List<Transaction> lTx = List.of(
        Transaction.transaction(LocalDate.parse("2022-08-10"), "VTI", 10,
            new BigDecimal("211.270004")),
        Transaction.transaction(LocalDate.parse("2022-09-10"), "VTI", -5,
            new BigDecimal("210.000000")),
        Transaction.transaction(LocalDate.parse("2022-10-10"), "VTI", 2,
            new BigDecimal("181.000000")),
        Transaction.transaction(LocalDate.parse("2022-11-10"), "VTI", -6,
            new BigDecimal("180.00000")));
    var f = Utilities.growthFactors(lTx, (LocalDate.parse("2022-11-28")));
    f.forEachOrFail(System.out::println).forEach(err -> System.out.println("err: " + err));
  }

  // twrr (date, period): 1) filtered transactions, always filter stocks 2) twrr until specific date
  //  - no date or period -> all transactions
  //  - date -> transactions up to & incl. date, twrr on date
  //  - period -> transactions inside period (inclusive), twrr on second/last date
  @Test
  void twrrTest() {
    Options options = new Options();
    options.dbOptions = prepDBOptions();
    String dateStr1 = "2021-10-12";
    String dateStr2 = "2022-11-07";

    // Test case 1: new DB, no filter -> TWRR over all transactions
    // Expected: current prices, corresponding TWRR (cannot be predicted)
    options.dbOptions.newDBPath = new File("src/test/resources/testdb.mv.db");
    options.dbOptions.dbPath = null;
    options.timeFilter = null;
    options.txFile = new File("src/test/resources/testdata.txt");
    options.symbol = null;

    Result<Tuple<List<LocalDate>, List<BigDecimal>>> res = Analysiolo.twrr_(options);

    res.forEachOrFail(tpl -> System.out.println("Current result " + tpl))
       .forEach(Assertions::fail);

    // Test case 2: existing DB, single date, no stock filter -> transactions up to given date, TWRR
    // on date
    // Expected: ((2021-02-18, 2021-10-12, NIL), (2055.000000, 147577.394754, 32.902135, NIL))
    options.txFile = null;
    options.dbOptions.newDBPath = null;
    options.dbOptions.dbPath = new File("src/test/resources/testdb.mv.db");
    options.timeFilter = new TimeFilter();
    options.timeFilter.date = LocalDate.parse(dateStr1);
    res = Analysiolo.twrr_(options);

    List<LocalDate> dates = List.of(LocalDate.parse("2021-02-18"), LocalDate.parse(dateStr1));
    List<BigDecimal> values = List.of(new BigDecimal("2055.000000"),
                                      new BigDecimal("147577.394754"),
                                      new BigDecimal("32.902135")); // expected TWRR
    Tuple<List<LocalDate>, List<BigDecimal>> expRes2 = new Tuple<>(dates, values);

    res.forEachOrFail(tpl -> assertEquals(expRes2, tpl))
       .forEach(Assertions::fail);

    // Test case 3: single date, filter VTI -> only VTI transactions up to given date, TWRR on date
    // Expected: ((2021-02-18, 2021-10-12, NIL), (2055.000000, 24481.400654, 11.368508, NIL))
    options.symbol = java.util.List.of("VTI");
    res = Analysiolo.twrr_(options);

    values = List.of(new BigDecimal("2055.000000"),
                     new BigDecimal("24481.400654"),
                     new BigDecimal("11.368508")); // expected TWRR
    Tuple<List<LocalDate>, List<BigDecimal>> expRes3 = new Tuple<>(dates, values);

    res.forEachOrFail(tpl -> assertEquals(expRes3, tpl))
       .forEach(Assertions::fail);

    // Test case 4: period -> VTI transactions inside period (inclusive), TWRR on second date
    // Expected: ((2021-10-12, 2022-11-07, NIL), (22460.000600, 170068.723568, 33.133043, NIL))
    options.timeFilter.date = null;
    options.timeFilter.period = List.of(dateStr1, dateStr2);
    res = Analysiolo.twrr_(options);

    dates = List.of(LocalDate.parse(dateStr1), LocalDate.parse(dateStr2));
    values = List.of(new BigDecimal("22460.000600"),
        new BigDecimal("170068.723568"),
        new BigDecimal("33.133043")); // expected TWRR
    Tuple<List<LocalDate>, List<BigDecimal>> expRes4 = new Tuple<>(dates, values);

    res.forEachOrFail(tpl -> assertEquals(expRes4, tpl))
       .forEach(Assertions::fail);
  }
}