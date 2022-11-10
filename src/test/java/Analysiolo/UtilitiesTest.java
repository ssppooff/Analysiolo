package Analysiolo;

import static org.junit.jupiter.api.Assertions.*;

import Analysiolo.Analysiolo.DB;
import Analysiolo.Analysiolo.DB.ExclusiveOptions;
import Analysiolo.Analysiolo.TimeFilter;
import Analysiolo.Analysiolo.TimeFilter.ExclusiveTFOptions;
import functionalUtilities.List;
import functionalUtilities.Result;
import java.io.File;
import java.math.BigDecimal;
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

  @Test
  void convertToArrayTest() {
    List<String> row1 = List.of("(1,1)", "(2,1)", "(3,1)", "(4,1)", "(5,1)", "(6,1)", "(7,1)");
    List<String> row2 = List.of("(1,2)", "(2,2)", "(3,2)", "(4,2)", "(5,2)", "(6,2)", "(7,2)");
    List<String> row3 = List.of("(1,3)", "(2,3)", "(3,3)", "(4,3)", "(5,3)", "(6,3)", "(7,3)");
    List<String> row4 = List.of("(1,4)", "(2,4)", "(3,4)", "(4,4)", "(5,4)", "(6,4)", "(7,4)");
    List<String> row5 = List.of("(1,5)", "(2,5)", "(3,5)", "(4,5)", "(5,5)", "(6,5)", "(7,5)");
    List<String> row6 = List.of("(1,6)", "(2,6)", "(3,6)", "(4,6)", "(5,6)", "(6,6)", "(7,6)");
    List<List<String>> table = List.of(row1, row2, row3, row4, row5, row6);
    Utilities.convertToArray(table);
  }
}