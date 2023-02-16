package ch.cottier.stockAPI;

import static org.junit.jupiter.api.Assertions.*;

import ch.cottier.functionalUtilities.FileReader;
import ch.cottier.functionalUtilities.List;
import ch.cottier.functionalUtilities.Map;
import ch.cottier.functionalUtilities.Result;
import ch.cottier.functionalUtilities.Tuple;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionTest {
  String path = "src/test/resources/testdata.txt";

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
  void weightedAvgPrice() {
    Result<Map<Symbol, BigDecimal>> avgPrice = prepDataInDS()
        .map(Tuple::_1)
        .map(Transaction::weightedAvgPrice);
    Map<Symbol, BigDecimal> expMap = Map.<Symbol, BigDecimal>empty()
        .put(Symbol.symbol("VXUS"), new BigDecimal("40.000000"))
        .put(Symbol.symbol("AVUV"), new BigDecimal("38.058824"))
        .put(Symbol.symbol("VTI"), new BigDecimal("43.109451"));
    avgPrice.forEach(m -> assertEquals(expMap, m));
  }
}