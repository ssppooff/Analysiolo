package Analysiolo;

import static org.junit.jupiter.api.Assertions.*;

import Analysiolo.Analysiolo.DB;
import Analysiolo.Analysiolo.DB.ExclusiveOptions;
import Analysiolo.Analysiolo.TimeFilter;
import Analysiolo.Analysiolo.TimeFilter.ExclusiveTFOptions;
import functionalUtilities.List;
import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import stockAPI.Symbol;
import stockAPI.Transaction;

class UtilitiesTest {
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

  @Test
  void fileNameVerifier() {
    assertEquals("", Utilities.removePossibleExtensions(""));

    String path = "absolute/path/to/testdb";
    String expPath;
    assertEquals(path, Utilities.removePossibleExtensions(path));

    path = "absolute/path/to/testdb.db";
    expPath = "absolute/path/to/testdb";
    assertEquals(expPath, Utilities.removePossibleExtensions(path));

    path = "absolute/path/to/testdb.mv.db";
    expPath = "absolute/path/to/testdb";
    assertEquals(expPath, Utilities.removePossibleExtensions(path));

    path = "absolute/path.db/to/testdb.mv.db";
    expPath = "absolute/path.db/to/testdb";
    assertEquals(expPath, Utilities.removePossibleExtensions(path));

    path = "absolute/path.db/to/testdb";
    expPath = "absolute/path.db/to/testdb";
    assertEquals(expPath, Utilities.removePossibleExtensions(path));

    path = "absolute with spaces/path.db/to/testdb.db";
    expPath = "absolute with spaces/path.db/to/testdb";
    assertEquals(expPath, Utilities.removePossibleExtensions(path));
  }

  @Test
  void periodTimeFilterTest() {
//  possibilities:
//  -d demo.db (equiv)
//  -d demo.db --period inception (equiv)
//  -d demo.db --period inception now (equiv)
//  -d demo.db --period now (only txs from today)
//  -d demo.db --period 2021-10-10 (from-date until now)
//  -d demo.db --period 2021-10-10 now (equiv to ^)
//  -d demo.db --period inception 2021-10-10 (from- to end-date)

    Analysiolo.TimeFilter tf = new Analysiolo.TimeFilter();
    tf.opt = new Analysiolo.TimeFilter.ExclusiveTFOptions();
    tf.opt.date = null;
    tf.opt.period = List.of("now");
    Function<LocalDate, Boolean> comp = date -> Utilities.timePeriodComparator(tf)
                                                         .apply(Transaction.transaction(date, "TLSA", 0, BigDecimal.ZERO));
    assertFalse(comp.apply(LocalDate.parse("2020-01-01")));
    assertTrue(comp.apply(LocalDate.now()));

    tf.opt.period = List.of("inception");
    comp = date -> Utilities.timePeriodComparator(tf)
                            .apply(Transaction.transaction(date, "TLSA", 0, BigDecimal.ZERO));
    assertTrue(comp.apply(LocalDate.parse("1000-01-01")));
    assertTrue(comp.apply(LocalDate.now()));

    tf.opt.period = List.of("inception", "now");
    comp = date -> Utilities.timePeriodComparator(tf)
                            .apply(Transaction.transaction(date, "TLSA", 0, BigDecimal.ZERO));
    assertTrue(comp.apply(LocalDate.parse("1000-01-01")));
    assertTrue(comp.apply(LocalDate.now()));

    tf.opt.period = List.of("now", "inception");
    comp = date -> Utilities.timePeriodComparator(tf)
                            .apply(Transaction.transaction(date, "TLSA", 0, BigDecimal.ZERO));
    assertFalse(comp.apply(LocalDate.parse("1000-01-02")));
    assertFalse(comp.apply(LocalDate.now()));

    tf.opt.period = List.of("2021-10-10");
    comp = date -> Utilities.timePeriodComparator(tf)
                            .apply(Transaction.transaction(date, "TLSA", 0, BigDecimal.ZERO));
    assertTrue(comp.apply(LocalDate.parse("2021-10-11")));
    assertTrue(comp.apply(LocalDate.now()));

    tf.opt.period = List.of("2021-10-10", "now");
    comp = date -> Utilities.timePeriodComparator(tf)
                            .apply(Transaction.transaction(date, "TLSA", 0, BigDecimal.ZERO));
    assertTrue(comp.apply(LocalDate.parse("2021-10-11")));
    assertTrue(comp.apply(LocalDate.now()));

    tf.opt.period = List.of("inception", "2021-10-10");
    comp = date -> Utilities.timePeriodComparator(tf)
                            .apply(Transaction.transaction(date, "TLSA", 0, BigDecimal.ZERO));
    assertFalse(comp.apply(LocalDate.parse("2021-10-11")));
    assertTrue(comp.apply(LocalDate.parse("2021-10-10")));
  }

  @Test
  void computeDateTest() {
    Analysiolo.TimeFilter tf = new Analysiolo.TimeFilter();
    tf.opt = new ExclusiveTFOptions();
    tf.opt.date = LocalDate.parse("2021-10-11");
    String expStr = "2021-10-11";
    assertEquals(expStr, Utilities.computeDate(tf));

    tf.opt.date = null;
    tf.opt.period = List.of("now");
    expStr = LocalDate.now().toString();
    assertEquals(expStr, Utilities.computeDate(tf));

    tf.opt.period = List.of("inception", "now");
    expStr = LocalDate.now().toString();
    assertEquals(expStr, Utilities.computeDate(tf));

    tf.opt.period = List.of("inception", "2021-10-11");
    expStr = "2021-10-11";
    assertEquals(expStr, Utilities.computeDate(tf));

    tf.opt.period = List.of("2021-10-11");
    expStr = "2021-10-11";
    assertEquals(expStr, Utilities.computeDate(tf));

  }

  @Test
  void parseDbOptionTest() {
    DB db = new DB();
    assertFailure(Utilities.parseDbOption(db));

    db.opt = new ExclusiveOptions();
    db.opt.dbPath = null;
    db.opt.newDBPath =
        new File("src/test/java/testdb.mv.db");
    assertSuccess(Utilities.parseDbOption(db));
    assertTrue(db.opt.newDBPath.exists());
    assertTrue(db.opt.newDBPath.delete());
  }

  @Test
  void parseStockFilterTest() {
    List<Symbol> res = Utilities.parseStockFilter(java.util.List.of("VTI", "AVUV"));
    List<Symbol> expRes = List.of(Symbol.symbol("VTI"), Symbol.symbol("AVUV"));
    assertEquals(expRes, res);
  }

  @Test
  void parseTimeFilterTest() {
    TimeFilter tf = new TimeFilter();
    List<LocalDate> res = Utilities.parseTimeFilter(tf);
    List<LocalDate> expRes = List.of(LocalDate.now());
    assertEquals(expRes, res);

    String dateStr1 = "2021-10-10";
    String dateStr2 = "2021-12-10";
    tf.opt = new ExclusiveTFOptions();
    tf.opt.date = LocalDate.parse(dateStr1);
    res = Utilities.parseTimeFilter(tf);
    expRes = List.of(LocalDate.parse(dateStr1));
    assertEquals(expRes, res);

    tf.opt.date = null;
    tf.opt.period = java.util.List.of(dateStr1);
    res = Utilities.parseTimeFilter(tf);
    expRes = List.of(LocalDate.parse(dateStr1), LocalDate.now());
    assertEquals(expRes, res);

    tf.opt.period = java.util.List.of(dateStr1, dateStr2);
    res = Utilities.parseTimeFilter(tf);
    expRes = List.of(LocalDate.parse(dateStr1), LocalDate.parse(dateStr2));
    assertEquals(expRes, res);

    tf.opt.period = java.util.List.of("inception", dateStr2);
    res = Utilities.parseTimeFilter(tf);
    expRes = List.of(LocalDate.parse("1000-01-01"), LocalDate.parse(dateStr2));
    assertEquals(expRes, res);
  }

  @Test
  void convertToResultTest() {
    assertSuccess(Utilities.convertToResult(new File("test")));
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
    assertSuccess(Utilities.checkCorrectSequence(lTx, true));

    Result<List<Transaction>> res = assertFailure(Utilities.checkCorrectSequence(lTxError, true));
    Result<List<Transaction>> expRes = Result.failure("Wrong date after line 2");
    assertEquals(expRes, res);

    lTxError = List.of(
        Transaction.transaction(LocalDate.parse("2022-12-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-10-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-11-01"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-09-01"), "SP500", 10, BigDecimal.ONE));
    res = assertFailure(Utilities.checkCorrectSequence(lTxError, false));
    assertEquals(expRes, res);
  }

  @Test
  void getOrderSeqTest() {
    List<Transaction> lDates = List.of(
        Transaction.transaction(LocalDate.parse("2022-12-12"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-13"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-20"), "SP500", 10, BigDecimal.ONE));
    assertTrue(Utilities.getOrderSeq(lDates)._1);
    lDates = List.of(
        Transaction.transaction(LocalDate.parse("2022-12-20"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-13"), "SP500", 10, BigDecimal.ONE),
        Transaction.transaction(LocalDate.parse("2022-12-12"), "SP500", 10, BigDecimal.ONE));
    assertFalse(Utilities.getOrderSeq(lDates)._1);
  }

  @Test
  void checkSequenceError() {
    // Read input data (simulates text file or directly from CLI)
    // sort the transactions
    Result<List<Transaction>> err = Utilities.checkTxIn(new File(pathAdditionalError));
    assertFailure(err);
  }

  private List<String> getHeader() {
    return List.of("(0,0)hd", "(0,1)header", "(0,2)hdFull", "(0,3)blah", "(0,4)testi",
        "(0,5)nopeaf", "(0,6)");
  }

  private List<List<String>> getTable() {
    List<String> row1 = List.of("(1,0)", "(1,1)", "(1,2)", "(1,3)", "(1,4)", "(1,5)", "(1,6)");
    List<String> row2 = List.of("(2,0)", "(2,1)", "(2,2)", "(2,3)", "(2,4)", "(2,5)");
    List<String> row3 = List.of("(3,0)", "(3,1)", "(3,2)", "(3,3)", "(3,4)", "(3,5)", "(3,6)");
    List<String> row4 = List.of("(4,0)", "(4,1)", "(4,2)", "(4,3)", "(4,4)", "(4,5)", "(4,6)");
    List<String> row5 = List.of("(5,0)", "(5,1)", "(5,2)", "(5,3)", "(5,4)", "(5,5)", "(5,6)");
    List<String> row6 = List.of("(6,0)", "(6,1)", "(6,2)", "(6,3)", "(6,4)", "(6,5)", "(6,6)");
    return List.of(row1, row2, row3, row4, row5, row6);
  }

//  @Test
//  void padDataTest() {
//    List<List<String>> table = getTable().prepend(getHeader());
//    String[][] padded = Utilities.padCells(table).map(List::toArray).toArray();
//    List<String> currHd = table.head();
//    for (int col = 0; col < table.head().size(); col++) {
//      assertEquals(currHd.head().length(), padded[0][col].length());
//      currHd = currHd.tail();
//    }
//  }

//  @Test
//  void renderTableTest() {
//    List<List<String>> table = getTable().prepend(getHeader());
//    String r = Utilities.renderTable(Utilities.padCells(table));
//    System.out.println(r);
//  }

  @Test
  void addChangeMetricsTest() {
    BigDecimal origPrice = new BigDecimal("80.000");
    BigDecimal newPrice = new BigDecimal("100.000");
    List<BigDecimal> expRes = List.of(origPrice, newPrice,
        new BigDecimal("20"), new BigDecimal("25"));
    expRes.zip(Utilities.addChangeMetrics(List.of(origPrice, newPrice)))
        .forEach(t -> assertEquals(0, t._1.compareTo(t._2)));
  }

  @Test
  void changeFormatTest() {
    String symStr1 = "TSLA";
    String symStr2 = "VTI";
    Symbol symbol1 = Symbol.symbol(symStr1);
    Symbol symbol2 = Symbol.symbol(symStr2);
    String dateStr1 = "2021-01-01";
    String dateStr2 = "2022-01-01";
    LocalDate date1 = LocalDate.parse(dateStr1);
    LocalDate date2 = LocalDate.parse(dateStr2);
    List<BigDecimal> prices1 = List.of(new BigDecimal("800"), new BigDecimal("1000"))
                                   .map(num -> num.setScale(6, RoundingMode.HALF_UP));
    List<BigDecimal> prices2 = List.of(new BigDecimal("35"), new BigDecimal("50"))
                                   .map(num -> num.setScale(6, RoundingMode.HALF_UP));

    var entry1 = new Tuple<>(date1, List.of(
        new Tuple<>(symbol1, prices1.head()),
        new Tuple<>(symbol2, prices2.head())));
    var entry2 = new Tuple<>(date2, List.of(
        new Tuple<>(symbol1, prices1.tail().head()),
        new Tuple<>(symbol2, prices2.tail().head())));

    Map<Symbol, List<BigDecimal>> m = Map.empty();
    Tuple<List<LocalDate>, Map<Symbol, List<BigDecimal>>> exp =
        new Tuple<>(List.of(date1, date2), m.put(symbol1, prices1).put(symbol2, prices2));

    assertEquals(exp, Utilities.changeFormat(List.of(entry1, entry2)));
  }

  @Test
  void themeTwoPricesWithDeltaTest() {
    List<List<String>> data = List.of(
        List.of("", "2021-10-10", "2022-10-10", "delta", "delta (%)"),
        List.of("TSLA", "100.000", "200.000", "100.000", "1.00"),
        List.of("VTI", "2000.000", "20.000", "-1980.000", "-0.49"));
    var f= Utilities.themeTwoPricesWithDelta().apply(data);
    System.out.println(Utilities.renderTable(f));
  }

  @Test
  void themeOnePriceTest() {
    List<List<String>> data = List.of(
        List.of("", "2021-10-10"),
        List.of("TSLA", "100.000"),
        List.of("VTI", "2000.000"));
    var f = Utilities.themeOnePrice().apply(data);
    System.out.println(Utilities.renderTable(f));
  }
}