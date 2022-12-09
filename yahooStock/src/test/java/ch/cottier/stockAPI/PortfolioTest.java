package ch.cottier.stockAPI;

import static org.junit.jupiter.api.Assertions.*;

import ch.cottier.functionalUtilities.FileReader;
import ch.cottier.functionalUtilities.List;
import ch.cottier.functionalUtilities.Result;
import ch.cottier.functionalUtilities.Tuple;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PortfolioTest {
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

  @SuppressWarnings("unused")
  void listPerLine(List<Transaction> l) {
    System.out.println(
        l.foldLeft(new StringBuilder(), s -> tx ->
            s.append(tx.toString()).append("\n")));
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
  void TWRRtest() {
    LocalDate from = LocalDate.parse("2021-03-01");
    LocalDate to = LocalDate.parse("2021-12-01");

    Result<List<Transaction>> init = prepDataInDS().map(Tuple::_1);
    Result<List<Transaction>> listTx = init.map(l ->
        l.filter(tx -> tx.getDate().compareTo(from) >= 0 && tx.getDate().compareTo(to) < 0));
    Result<Portfolio> initPortfolio = init.map(l ->
            l.filter(tx -> tx.getDate().compareTo(from) < 0))
        .flatMap(l -> Portfolio.portfolio(l, from));

    Result<BigDecimal> twrr = Result.flatMap2(initPortfolio, listTx, pf -> lTx -> Portfolio.TWRR(pf, lTx, from, to));
    assertSuccess(twrr);

    BigDecimal expRes = new BigDecimal("0.119108982393583731528110761760249746037725179268929607253799680000");
    assertEquals(Result.success(expRes), twrr);
  }

  @Test
  void historicalNetValue() {
    LocalDate askDate1 = LocalDate.parse("2021-10-12");
    LocalDate askDate2 = LocalDate.parse("2022-10-12");
    Result<List<Transaction>> init = prepDataInDS().map(Tuple::_1);

    Result<BigDecimal> netValue1 = init.map(l -> l.filter(tx -> tx.getDate().compareTo(askDate1) <= 0))
        .flatMap(lTx -> Portfolio.valueOn(lTx, askDate1));
    Result<BigDecimal> netValue2 = init.map(l -> l.filter(tx -> tx.getDate().compareTo(askDate2) <= 0))
        .flatMap(lTx -> Portfolio.valueOn(lTx, askDate2));

    Result<BigDecimal> expRes1 = Result.success(new BigDecimal("147577.394754"));
    assertEquals(expRes1, netValue1);

    Result<BigDecimal> expRes2 = Result.success(new BigDecimal("161549.302703"));
    assertEquals(expRes2, netValue2);
  }

  @Test
  void currentNetValue() {
    Result<BigDecimal> netValue =  prepDataInDS().map(Tuple::_1)
        .flatMap(Portfolio::portfolio)
        .map(Portfolio::currentValue);
    assertSuccess(netValue);
    System.out.println(netValue);
  }
}