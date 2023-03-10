package ch.cottier.stockAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cottier.functionalUtilities.Result;
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

    if (stock.getSymbol().toString().equals("^GSPC")) {
      Result<BigDecimal> price = assertSuccess(stock.historicalPrice(date1));
      price.forEach(p -> assertEquals(new BigDecimal("4766.180176"), p));

      price = assertSuccess(stock.historicalPrice(date2));
      price.forEach(p -> assertEquals(new BigDecimal("4796.560059"), p));
    }
    if (stock.getSymbol().toString().equals("TSLA")) {
      Result<BigDecimal> price = assertSuccess(stock.historicalPrice(date1));
      price.forEach(p -> assertEquals(new BigDecimal("352.260010"), p));

      price = assertSuccess(stock.historicalPrice(date2));
      price.forEach(p -> assertEquals(new BigDecimal("399.926666"), p));
    }

  }

  @Test
  void getPriceOn() {
    LocalDate date = LocalDate.parse("2022-08-25");
    Result<BigDecimal> price = assertSuccess(stock.getPriceOn(date));
    if (stock.getSymbol().toString().equals("^GSPC"))
      price.forEach(p -> assertEquals(new BigDecimal("4199.120117"), p));
    if (stock.getSymbol().toString().equals("TSLA"))
      price.forEach(p -> assertEquals(new BigDecimal("296.070007"), p));
  }

  @Test
  void fillHistoricalDataTest() {
    // stock created with historical data beginning from "2021-12-28"
    LocalDate fillDate = LocalDate.parse("2021-01-01");
    stock = assertSuccess(stock.fillHistoricalData(fillDate)).getOrThrow();
    BigDecimal price = assertSuccess(stock.getPriceOn(fillDate.plusDays(2))).getOrThrow();

    if (stock.getSymbol().toString().equals("^GSPC"))
      assertEquals(new BigDecimal("3732.040039"), price);
    if (stock.getSymbol().toString().equals("TSLA"))
      assertEquals(new BigDecimal("231.593338"), price);
  }
}