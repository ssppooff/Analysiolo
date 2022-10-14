package stockAPI;

import functionalUtilities.Input;
import functionalUtilities.List;
import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Stream;
import functionalUtilities.Tuple;

public class Parser {
  private Parser() {
    super();
  }

  public static Result<Tuple<Transaction, Input>> createTx(Input input) {
    return input.nextDate()
        .flatMap(date -> date._2.nextStr()
            .flatMap(symbol -> symbol._2.nextInt()
                .flatMap(nShares -> nShares._2.nextBigDecimal()
                    .map(price ->
                        new Tuple<>(
                            Transaction.transaction(date._1, symbol._1, nShares._1, price._1),
                            price._2)))));
  }

  public static Result<Tuple<Result<Transaction>, Input>> createTxWithCheck(Input input) {
    return input.nextDate()
        .flatMap(date -> date._2.nextStr()
            .flatMap(txType -> txType._2.nextStr()
                .flatMap(symbol -> symbol._2.nextInt()
                    .flatMap(nShares -> nShares._2.nextBigDecimal()
                        .map(price ->
                            txType._1.equalsIgnoreCase("BUY") && nShares._1 > 0
                                || txType._1.equalsIgnoreCase("SELL") && nShares._1 < 0
                                ? new Tuple<>(Result.success(
                                Transaction.transaction(date._1, symbol._1, nShares._1, price._1)),
                                price._2)
                                : new Tuple<>(
                                    Result.failure("Buy or sell not correctly specified"),
                                    price._2))))));
  }

  public static Result<Map<Symbol, Integer>> checkForNegativeStocks(Map<Symbol, Integer> stocks) {
    Stream<Tuple<Symbol, Integer>> negativeStock = stocks.stream().filter(t -> t._2 < 0);
    return negativeStock.isEmpty()
        ? Result.success(stocks)
        : Result.failure("Input data contains stocks with negative number of shares: "
            + negativeStock.map(t -> t._1).toList());
  }

  public static Result<List<Transaction>> parseTransactions(Input input) {
    return List.flattenResult(List.unfold(input, Parser::createTxWithCheck));
  }

  public static Map<Symbol, Integer> parseStocks(List<Transaction> l) {
    return l.foldLeft(Map.empty(), acc -> e -> acc.put(
        e.getSymbol(),
        acc.get(e.getSymbol()).getOrElse(0) + e.getNumShares()));
  }
}