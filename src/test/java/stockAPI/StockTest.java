package stockAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import functionalUtilities.Result;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StockTest {
  static Stock stock;

  @BeforeAll
  static void setup() {
//    stock = assertSuccess(Stock.stock("^GSPC", LocalDate.parse("2021-12-28"))).getOrThrow();
    stock = assertSuccess(Stock.stock("TSLA", LocalDate.parse("2021-12-28"))).getOrThrow();
  }


  @SuppressWarnings("unused")
  static <T> Result<T> assertSuccess(Result<T> r) {
    assertTrue(r.isSuccess(), r.toString());
    return r;
  }

  @SuppressWarnings("unused")
  static <T> Result<T> assertFailure(Result<T> r) {
    assertTrue(r.isFailure(), r.toString());
    return r;
  }

  @Test
  void getPrice() {
    LocalDate date1 = LocalDate.parse("2022-01-01");
    LocalDate date2 = LocalDate.parse("2022-01-03");

    if (stock.getSymbol().getSymbolStr().equals("^GSPC")) {
      Result<BigDecimal> price = assertSuccess(stock.historicalPrice(date1));
      price.forEach(p -> assertEquals(new BigDecimal("4766.180176"), p));

      price = assertSuccess(stock.historicalPrice(date2));
      price.forEach(p -> assertEquals(new BigDecimal("4796.560059"), p));
    }
    if (stock.getSymbol().getSymbolStr().equals("TSLA")) {
      Result<BigDecimal> price = assertSuccess(stock.historicalPrice(date1));
      price.forEach(p -> assertEquals(new BigDecimal("352.260010"), p));

      price = assertSuccess(stock.historicalPrice(date2));
      price.forEach(p -> assertEquals(new BigDecimal("399.926666"), p));
    }

  }
}