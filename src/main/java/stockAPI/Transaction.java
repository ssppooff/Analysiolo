package stockAPI;

import java.time.LocalDate;

public class Transaction {
  private final LocalDate date;
  private final String symbol;
  private final int numShares;
  private final int buyPrice;

  private Transaction(LocalDate date, String symbol, int numShares, int buyPrice) {
    this.date = date;
    this.symbol = symbol;
    this.numShares = numShares;
    this.buyPrice = buyPrice;
  }

  public static Transaction transaction(LocalDate date, String symbol, int numShares,
      int buyPrice) {
    return new Transaction(date, symbol, numShares, buyPrice);
  }

  public static Transaction transaction(LocalDate date, String symbol, int numShares,
      Double buyPrice) {
    //noinspection WrapperTypeMayBePrimitive
    Double basePrice = buyPrice * 100d;
    return new Transaction(date, symbol, numShares, basePrice.intValue());
  }

  @Override
  public boolean equals(Object obj) {
    if ( !(obj instanceof Transaction that) )
      return false;

    return this.date.equals(that.date)
        && this.symbol.equals(that.symbol)
        && this.numShares == that.numShares
        && this.buyPrice == that.buyPrice;
  }

  @Override
  public String toString() {
    return String.format("%TF %s %d @ %.2f", date, symbol, numShares, (double) buyPrice/100);
  }

  public Symbol getSymbol() {
    return Symbol.symbol(symbol);
  }
}