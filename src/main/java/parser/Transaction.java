package parser;

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
    if (obj == null || !(obj instanceof Transaction))
      return false;

    Transaction oTx = (Transaction) obj;
    return this.date.equals(oTx.date)
        && this.symbol.equals(oTx.symbol)
        && this.numShares == oTx.numShares
        && this.buyPrice == oTx.buyPrice;
  }
}