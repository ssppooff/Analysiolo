package stockAPI;

import functionalUtilities.List;
import functionalUtilities.Map;
import functionalUtilities.Tuple;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

  // Two ways to go about it: do it inside the db, or inside the app logic...
  // I choose to do it in the app logic
  public static Map<Symbol, BigDecimal> weightedAvgPrice(final List<Transaction> l) {
    return l.filter(tx -> tx.getNumShares() > 0)
        .groupBy(Transaction::getSymbol)
        .mapVal(lTxPerStock -> lTxPerStock
            .foldLeft(new Tuple<>(BigDecimal.ZERO, 0), t -> tx -> {
              BigDecimal cost = tx.getPrice().multiply(BigDecimal.valueOf(tx.getNumShares()));
              return new Tuple<>(t._1.add(cost), t._2 + tx.getNumShares());
            }))
        .mapVal(t -> t._1.divide(BigDecimal.valueOf(t._2), RoundingMode.HALF_UP));
  }
}