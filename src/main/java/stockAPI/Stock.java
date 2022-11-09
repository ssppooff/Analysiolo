package stockAPI;

import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Function;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

public class Stock {
  private final yahoofinance.Stock yfStock;
  private final LocalDate earliestDate;
  private final java.util.List<HistoricalQuote> priceHistory;

  private Stock(yahoofinance.Stock stock, java.util.List<HistoricalQuote> priceHistory, LocalDate earliestDate) {
    this.yfStock = stock;
    this.priceHistory = priceHistory;
    this.earliestDate = earliestDate;
  }

  private Stock(yahoofinance.Stock stock) {
    this.yfStock = stock;
    this.priceHistory = new ArrayList<>();
    this.earliestDate = LocalDate.now();
  }

  public Symbol getSymbol() {
    return Symbol.symbol(yfStock.getSymbol(), yfStock.getName());
  }

  public LocalDate getEarliestHistoricalDate() {
    return earliestDate;
 }

  public BigDecimal getPrice() {
    return yfStock.getQuote().getPrice();
  }

  /** If the price is outside the currently held historical data (which it probably will,
   * since this method is only invoked from the subcommand `price`) it will *not* be incorporated
   * into the price history held by the stock, because it is not a range of historical data, only
   * a point in time, and therefore would mess with the role of the `earliestDate` field/date.
   */
  public Result<BigDecimal> getPriceOn(LocalDate date) {
    if (earliestDate.compareTo(date) <= -1) {
      Calendar calDate = convertToCalendar(date, yfStock.getQuote().getTimeZone().toZoneId());
      return Result.success(
          priceHistory.stream()
              .filter(histQuote -> histQuote.getDate().compareTo(calDate) == 0)
              .toList().get(0)
              .getClose());
    }

    try {
      Calendar onDate = convertToCalendar(date, yfStock.getQuote().getTimeZone().toZoneId());
      Calendar toDate = (Calendar) onDate.clone();
      toDate.add(Calendar.DAY_OF_MONTH, 1);

      List<HistoricalQuote> price =
          yfStock.getHistory(onDate, toDate, Interval.DAILY);

      // reset history of YahooFinance API stock, as yfStock.getHistory overwrites its internal
      // price history
      yfStock.setHistory(priceHistory);

      if (price.isEmpty())
        return Result.failure("No price available for date " + date);

      return Result.success(price.get(0).getClose());
    } catch (IOException e) {
      return Result.failure(e);
    }
  }

  public Result<Stock> updatePrice() {
    try {
      yfStock.getQuote(true);
      return Result.success(new Stock(yfStock, priceHistory, earliestDate));
    } catch (IOException e) {
      return Result.failure(e);
    }
  }

  public Result<BigDecimal> historicalPrice(LocalDate date) {
    if (date.isBefore(earliestDate))
      return Result.failure("No price saved for " + getSymbol()+ " on date " + date);

    // Default time zone, since yahoofinance also uses the local time zone for historical quotes
    Calendar calDate = convertToCalendar(date, TimeZone.getDefault().toZoneId());
    java.util.List<HistoricalQuote> res = priceHistory.stream()
        .filter(quote -> quote.getDate().compareTo(calDate) <= 0)
        .toList();
    return Result.success(res.get(res.size() - 1).getClose());
  }

  public Result<Stock> fillHistoricalData(LocalDate from) {
    return fillHistory(yfStock, from, earliestDate.minusDays(1))
        .map(history ->
            new Stock(yfStock, history._1, history._2));
    if (from.compareTo(earliestDate) >= 0)
      return Result.success(this);
  }

  @Override
  public String toString() {
    return yfStock.toString();
  }

  public static Result<Map<Symbol, Stock>> stocks(List<String> symbol) {
    return getStock(symbol.toArray(new String[0]))
        .map(m -> m.mapVal(Stock::new));
  }

  public static Result<Stock> stock(String symbol) {
    return getStock(symbol).map(Stock::new);
  }

  public static Result<Stock> stock(Symbol symbol, LocalDate from) {
    return stock(symbol.getSymbolStr(), from);
  }

  public static Result<Stock> stock(String symbol, LocalDate from) {
    return getStock(symbol).flatMap(stock -> fillHistory(stock, from).map(history ->
        new Stock(stock, history._1, history._2)));
  }

  private static Result<Map<Symbol, yahoofinance.Stock>> getStock(String[] symbols) {
    try {
      // includes only the stocks that could successfully be retrieved from Yahoo Finance
      java.util.Map<String, yahoofinance.Stock> yfStocks = YahooFinance.get(symbols);

      Set<String> reqSym = new HashSet<>(Arrays.asList(symbols));
      reqSym.removeAll(yfStocks.keySet());
      if (!reqSym.isEmpty())
        return Result.failure("Couldn't get data on stock(s) " + reqSym);

      Map<Symbol, yahoofinance.Stock> res = Map.empty();
      yahoofinance.Stock tmpStock;
      for (String symStr : symbols) {
        if ( (tmpStock = yfStocks.get(symStr)) != null)
          res = res.put(Symbol.symbol(symStr), tmpStock);
        else
          return Result.failure("Couldn't get data on stock " + symStr);
      }

      return Result.success(res);
    } catch (IOException e) {
      return Result.failure(e);
    }
  }

  private static Result<yahoofinance.Stock> getStock(String symbol) {
    try {
      yahoofinance.Stock yfStock = YahooFinance.get(symbol);
      if (yfStock == null)
        return Result.failure("Couldn't find stock on Yahoo Finance");

      return Result.success(yfStock);
    } catch (IOException e) {
      return Result.failure(e);
    }
  }

  private static Result<Tuple<List<HistoricalQuote>, LocalDate>> fillHistory(yahoofinance.Stock stock, LocalDate from) {
    return fillHistory(stock, from, LocalDate.now());
  }
  private static Result<Tuple<List<HistoricalQuote>, LocalDate>> fillHistory(yahoofinance.Stock stock, LocalDate from, LocalDate to) {
    try {
      Function<LocalDate, Calendar> toCalendar = d -> convertToCalendar(d, stock.getQuote().getTimeZone().toZoneId());
      Calendar dateFrom = toCalendar.apply(from);
      Calendar dateTo = toCalendar.apply(to);

      List<HistoricalQuote> priceHistory = stock.getHistory(dateFrom, dateTo, Interval.DAILY);

      Calendar firstDate = priceHistory.get(0).getDate();
      Calendar actualFromDate = (Calendar) dateFrom.clone();
      if (firstDate.compareTo(dateFrom) > 0) {
        actualFromDate.add(Calendar.MONTH, -1);
        Calendar adjustedToDate = (Calendar) dateFrom.clone();
        adjustedToDate.add(Calendar.DAY_OF_MONTH, -1);
        List<HistoricalQuote> earlierPriceHistory = stock.getHistory(actualFromDate, adjustedToDate,
            Interval.DAILY);
        priceHistory.addAll(earlierPriceHistory);
        priceHistory.sort(Comparator.comparing(HistoricalQuote::getDate));

        firstDate = priceHistory.get(0).getDate();
        if (firstDate.compareTo(dateFrom) > 0)
          return Result.failure("No price history as far back as " + from);
      }

      LocalDate adjustedFrom = LocalDate.ofInstant(actualFromDate.toInstant(),
          actualFromDate.getTimeZone().toZoneId());
      return Result.success(new Tuple<>(priceHistory, adjustedFrom));
    } catch (IOException e) {
      return Result.failure(e);
    }
  }
  private static Calendar convertToCalendar(LocalDate date, ZoneId timeZone) {
    return GregorianCalendar.from(date.atStartOfDay(timeZone));
  }
}