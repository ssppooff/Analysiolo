package stockAPI;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Transaction {
  private final LocalDate date;
  private final String symbol;
  private final int numShares;
  private final BigDecimal price;

  private Transaction(LocalDate date, String symbol, int numShares, BigDecimal price) {
    this.date = date;
    this.symbol = symbol;
    this.numShares = numShares;
    this.price = price;
  }

  public static Transaction transaction(LocalDate date, String symbol, int numShares,
      BigDecimal buyPrice) {
    return new Transaction(date, symbol, numShares, buyPrice);
  }

  @Override
  public boolean equals(Object obj) {
    if ( !(obj instanceof Transaction that) )
      return false;

    return this.date.equals(that.date)
        && this.symbol.equals(that.symbol)
        && this.numShares == that.numShares
        && this.price.compareTo(that.price) == 0;
  }

  @Override
  public String toString() {
    return String.format("%TF %s %d @ %.2f", date, symbol, numShares, price);
  }

  public Symbol getSymbol() {
    return Symbol.symbol(symbol);
  }

  public int getNumShares() {
    return numShares;
  }

  public LocalDate getDate() {
    return date;
  }

  public BigDecimal getPrice() {
    return price;
  }
}