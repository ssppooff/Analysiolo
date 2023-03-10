package ch.cottier.stockAPI;

import ch.cottier.functionalUtilities.Input;
import ch.cottier.functionalUtilities.List;
import ch.cottier.functionalUtilities.Map;
import ch.cottier.functionalUtilities.Result;
import ch.cottier.functionalUtilities.Tuple;
import java.time.LocalDate;
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

  public static Result<Tuple<List<Transaction>, Input>> parseTransactions(Input input) {
    return List.flattenResult(List.unfold(input, Parser::createTxWithCheck))
        .map(l -> new Tuple<>(l, input));
  }

  public static Map<Symbol, Integer> parsePositions(List<Transaction> l) {
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

  public static Result<Map<Symbol, StockPosition>> parseStockPositions(List<Transaction> l) {
    return parseStockPositions(l, LocalDate.now());
  }

  public static Result<Map<Symbol, StockPosition>> parseStockPositions(List<Transaction> l, LocalDate historyFrom) {
    Map<Symbol, Integer> shares = parsePositions(l).filter(sym -> nShares -> nShares != 0);
    List<String> symbols = shares.toList(t -> ignoreVal -> t.toString());
    return Stock.stocks(symbols)
        .map(stocksMap -> stocksMap
            .zipValWith(shares, ignoreSym -> emptyStock -> nShares ->
                historyFrom.isEqual(LocalDate.now())
                    ? Result.success(StockPosition.position(emptyStock, nShares))
                    : (Result<StockPosition>) emptyStock.fillHistoricalData(historyFrom)
                        .map(filledStock -> StockPosition.position(filledStock, nShares))))
        .flatMap(Map::flattenResultVal);
  }
}