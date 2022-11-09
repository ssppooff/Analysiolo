package stockAPI;

import functionalUtilities.List;
import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class Portfolio {
  private final Map<Symbol, StockPosition> positions;

  private Portfolio(Map<Symbol, StockPosition> positions) {
    this.positions = positions;
  }

  public Result<Portfolio> updateWith(Transaction tx) {
    Result<StockPosition> updPositions = positions.containsKey(tx.getSymbol())
        ? positions.get(tx.getSymbol()).map(sp -> sp.addShares(tx.getNumShares()))
        : Stock.stock(tx.getSymbol(), tx.getDate())
            .map(stock -> StockPosition.position(stock, tx.getNumShares()));

    return updPositions.map(sp -> positions.put(tx.getSymbol(), sp))
        .map(Portfolio::new);
  }

  public Result<Portfolio> updateWith(List<Transaction> transactions) {
    Result<Map<Symbol, StockPosition>> updPositions = transactions
        .foldLeft(Result.success(positions), posx -> tx -> posx.flatMap(pos -> {
          Result<StockPosition> updStockPos = pos.containsKey(tx.getSymbol())
              ? pos.get(tx.getSymbol()).map(sp -> sp.addShares(tx.getNumShares()))
              : Stock.stock(tx.getSymbol(), tx.getDate())
                  .map(stock -> StockPosition.position(stock, tx.getNumShares()));

          return updStockPos.map(sp -> pos.put(tx.getSymbol(), sp));
        }));

    return updPositions.map(Portfolio::new);
  }

  public BigDecimal currentValue() {
    return positions.mapVal(StockPosition::getValue)
        .stream(ignoreSym -> stockPos -> stockPos)
        .foldLeft(BigDecimal.ZERO, totValue -> totValue::add);
  }

  public static Result<BigDecimal> valueOn(List<Transaction> l, LocalDate date) {
    List<Transaction> filteredTx = l.filter(tx -> tx.getDate().compareTo(date) <= 0);
    return filteredTx.isEmpty()
        ? Result.failure("No transaction happened before date " + date)
        : Portfolio.portfolio(l, date).flatMap(pf -> pf.valueOn(date));
  }

  public Result<BigDecimal> valueOn(LocalDate date) {
    return date.isEqual(LocalDate.now())
        ? Result.success(currentValue())
        : Map.flattenResultVal(positions.mapVal(sp -> sp.getValueOn(date)))
            .map(m -> m.stream(ignoreSym -> stockPos -> stockPos)
                .foldLeft(BigDecimal.ZERO, stockPos -> stockPos::add));
  }

  private Result<BigDecimal> rateOfReturn(LocalDate from, LocalDate to) {
    Result<BigDecimal> initValue = valueOn(from);
    Result<BigDecimal> endValue = valueOn(to);
    return positions.toList(ignoreSym -> StockPosition::isEmpty).reduce(b1 -> b2 -> b1 && b2)
        ? Result.success(BigDecimal.ONE)
        : Result.map2(initValue, endValue, iVal -> eVal -> eVal.divide(iVal, RoundingMode.HALF_UP));
  }

  public static Result<BigDecimal> TWRR(final Portfolio portfolio,
      final List<Transaction> lTx, final LocalDate from, final LocalDate to) {
    // Create pairs of (from date, to date inside tx)
    // drop date of last tx (or first before reverse()) as it will be provided when zipping with lTx
    List<Tuple<LocalDate, Transaction>> periodsAndTxs = lTx.foldLeft(List.list(from), l -> tx -> l.prepend(tx.getDate()))
        .tail().reverse()
        .zip(lTx);

    Result<Tuple<List<BigDecimal>, Portfolio>> init =
        Result.success(new Tuple<>(List.list(), portfolio));
    Result<Tuple<List<BigDecimal>, Portfolio>> result =
        periodsAndTxs.foldLeft(init, acc -> data -> acc
            .flatMap(accTuple -> accTuple._2.rateOfReturn(data._1, data._2.getDate())
                .flatMap(periodRoR -> accTuple._2.updateWith(data._2)
                    .map(updPf -> new Tuple<>(accTuple._1.prepend(periodRoR), updPf)))
            ));

    Result<List<BigDecimal>> growthFactors =
        result.flatMap(res -> lTx.last()
            .flatMap(lastTx -> res._2.rateOfReturn(lastTx.getDate(), to)
                .map(ror -> res._1().prepend(ror))))
            .map(List::reverse);
    return growthFactors.map(l -> l
        .foldLeft(BigDecimal.ONE, product -> product::multiply)
        .subtract(BigDecimal.ONE));
  }

  public static Result<Portfolio> portfolio(List<Transaction> transactions) {
      return Parser.parseStockPositions(transactions).map(Portfolio::new);
  }

  public static Result<Portfolio> portfolio(List<Transaction> transactions, LocalDate historyFrom) {
    return historyFrom.isEqual(LocalDate.now())
        ? portfolio(transactions)
        : Parser.parseStockPositions(transactions, historyFrom).map(Portfolio::new);
  }

  @Override
  public String toString() {
    return "Portfolio: " + positions.toString();
  }
}