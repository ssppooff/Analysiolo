package ch.cottier.stockAPI;

import ch.cottier.functionalUtilities.Result;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class StockPosition {
  private final Stock stock;
  private final int nShares;

  private StockPosition(Stock stock, int nShares) {
    this.stock = stock;
    this.nShares = nShares;
  }

  public boolean isEmpty() {
    return nShares == 0;
  }

  public Symbol getSymbol() {
    return stock.getSymbol();
  }

  public BigDecimal getValue() {
    return stock.getPrice().multiply(BigDecimal.valueOf(nShares));
  }

  public StockPosition addShares(int num) {
    return new StockPosition(this.stock, this.nShares + num);
  }

  public Result<BigDecimal> getValueOn(LocalDate date) {
    return stock.historicalPrice(date).map(price ->
        price.setScale(6, RoundingMode.HALF_UP)
             .multiply(BigDecimal.valueOf(nShares)));
  }

  public static StockPosition position(Stock stock, int nShares) {
    return new StockPosition(stock, nShares);
  }

  @Override
  public String toString() {
    return String.format("(" + stock + ", " + nShares + ")");
  }

  @Override
  public int hashCode() {
    return stock.hashCode() + nShares + 42;
  }

  @Override
  public boolean equals(Object obj) {
    if ( !(obj instanceof StockPosition that) )
      return false;

    return this.nShares == that.nShares
        && this.stock.equals(that.stock);
  }
}