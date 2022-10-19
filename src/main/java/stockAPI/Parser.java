package stockAPI;

import functionalUtilities.Input;
import functionalUtilities.List;
import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.util.function.Function;

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

  public static Result<List<Transaction>> parseTransactions(Input input) {
    return List.flattenResult(List.unfold(input, Parser::createTxWithCheck));
  }

  public static Map<Symbol, Integer> parseStocks(List<Transaction> l) {
    return l.foldLeft(Map.empty(), acc -> e -> acc.put(
        e.getSymbol(),
        acc.get(e.getSymbol()).getOrElse(0) + e.getNumShares()));
  }

  public static Function<Transaction, Result<Map<Symbol, Integer>>> checkForNegativeStock(Result<Map<Symbol, Integer>> acc) {
    return e -> {
      Symbol sym = e.getSymbol();
      int totShares = acc.flatMap(m -> m.get(sym)).getOrElse(0) + e.getNumShares();
      return acc.flatMap(m -> totShares >= 0
          ? Result.success(m.put(sym, totShares))
          : Result.failure("Negative number of shares for " + sym
              + " during or after date " + e.getDate()));
    };
  }
}