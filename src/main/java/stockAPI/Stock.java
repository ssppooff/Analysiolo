package stockAPI;

import functionalUtilities.Tuple;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import yahoofinance.YahooFinance;

public class Stock {
  public String name;
  public String symbol;
  public CurrentQuote quote;
  public Stats stats;
  public List<Quote> quoteHistory;
  public List<Dividend> dividendHistory;
  public List<Split> splitHistory;

  private Stock(String symbol) {
    this.symbol = symbol;
    try {
      // yahoofinance.stockAPI.Stock YahooFinance.get(String symbol, Calendar from, Calendar to, Interval interval) throws IOException {
      yahoofinance.Stock result = YahooFinance.get(symbol, false);
    } catch (IOException ignored) {
    }
  }

  public static Stock stock(String symbol) {
    return new Stock(symbol);
  }

  public static Tuple<BigDecimal, Stock> price(Stock stock) {
    return new Tuple<>(Quote.price(stock.quote), stock);
  }
}

class Quote {
  private final String symbol;

  private TimeZone timeZone;

  private BigDecimal ask;
  private Long askSize;
  private BigDecimal bid;
  private Long bidSize;
  private BigDecimal price;

  private Long lastTradeSize;
  private String lastTradeDateStr;
  private String lastTradeTimeStr;
  private Calendar lastTradeTime;

  private BigDecimal open;
  private BigDecimal previousClose;
  private BigDecimal dayLow;
  private BigDecimal dayHigh;

  private BigDecimal yearLow;
  private BigDecimal yearHigh;
  private BigDecimal priceAvg50;
  private BigDecimal priceAvg200;

  private Long volume;
  private Long avgVolume;

  Quote(String symbol) {
    this.symbol = symbol;
  }

  public static BigDecimal price(Quote quote) {
    return quote.price;
  }
}

class CurrentQuote extends Quote {

  private CurrentQuote(String symbol) {
    super(symbol);
  }
}
class Stats {}
class Dividend {}
class Split {}
