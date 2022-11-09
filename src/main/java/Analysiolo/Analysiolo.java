package Analysiolo;

import functionalUtilities.List;
import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.function.Function;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import stockAPI.DataSource;
import stockAPI.Portfolio;
import stockAPI.Stock;
import stockAPI.Symbol;

/* TODO:
    - change name: portfolio + analysis = analysiolio
    - print parsed options before continuing with computation

# CLI call options
* Example: Create a new database and add some transactions
$ analysiolo --create-database db-name(.db) --ingest file-with-transactions

* Syntactic sugar to compute the current value of the portfolio
$ analysiolo demo.db

* Flags
** Without/independent of subcommand
--dry-run/-n (boolean)
--create-database/-c (arity 1)
--ingest/--parse/--file/-f file-with-transactions (arity 1..*, split regex=' ')
--database/-d db-name.db (arity 1)

** For subcommands
--filter=`stock_name` (arity 1..*, split regex)
--filter=`stock1_name`,`stock2_name` <- picocli:Split Regex ','
--period=`start_date` <- picocli: arity 1..2
--period=`start_date-end_date`
--period=inception
--period=one-year
--period=ytd or year-to-date
--date closing_price_date (arity 1)

* Subcommands
- price (date, period)
- value (date, period): no date given -> current value, date -> transactions until date and value on
  date, period -> change in value between two dates ie. compute value at both dates
- avgCost (period)
- twrr (period)

* Specifying time periods
* -> if only one date: taken as from-date with end-date right now
Following equivalent
$ analysiolo <subcommand> -d demo.db (equiv)
$ analysiolo <subcommand> -d demo.db --period inception (equiv)
$ analysiolo <subcommand> -d demo.db --period inception now (equiv)
$ analysiolo <subcommand> -d demo.db --period 2021-10-10 (from-date until now)
$ analysiolo <subcommand> -d demo.db --period 2021-10-10 now (equiv to ^)
$ analysiolo <subcommand> -d demo.db --period inception 2021-10-10 (from- to end-date)
$ analysiolo <subcommand> -d demo.db --filter=TSLA,SP500 --period 2021-10-10
$ analysiolo <subcommand> -d demo.db --period 2021-10-10 --filter=TSLA,SP500

* Filter the used transactions to a specific stock
$ analysiolo <subcommand> -d demo.db --filter=TSLA,AVUV (1+ stock tickers)

* Get price of a specific stock: current, specific date
$ analysiolo price --filter=TSLA
$ analysiolo price --filter=TSLA --date 2021-10-10
$ analysiolo price --filter=TSLA --period 2021-10-10 (from_date until now, everything supported except 'inception')
$ analysiolo price --filter=TSLA --period inception (not supported)
* */

@SuppressWarnings("unused")
@Command(name = "analysiolo", version = "analysiolio 0.1", mixinStandardHelpOptions = true,
description = "Tool for simple analysis of a stock portfolio based on transactions.",
    subcommands = { HelpCommand.class })
public class Analysiolo implements Callable<Integer> {

    Analysiolo() {}

    @Mixin
    DB db;

    static class DB {
        @ArgGroup
        public ExclusiveOptions opt;

        static class ExclusiveOptions {
            @Option(names = {"--create-database",
                "-c"}, description = "create a new database")
            File newDBPath;

            @Option(names = {"--database", "-d"}, description = "Path to database")
            File dbPath;
        }
    }

    @Mixin
    TimeFilter tf;

    static class TimeFilter {
        @ArgGroup
        public ExclusiveTFOptions opt;

        static class ExclusiveTFOptions {
            @Option(names = "--period", arity = "1..2", required = true)
            java.util.List<String> period;

            @Option(names = "--date", arity = "1", required = true)
            LocalDate date;
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    @Option(names = {"--dry-run", "-n"}, scope = ScopeType.INHERIT)
    private boolean dryRun = false;

    @Option(names = {"--ingest", "--parse", "--file", "-f"}, description = "path to file with "
        + "transactions to process", scope = ScopeType.INHERIT)
    File txFile;

    @Option(names = "--filter", split = ",", arity = "1..*", scope = ScopeType.INHERIT)
    private java.util.List<String> stockFilter;

    // subcommand
    @Command(name = "price")
    int price(@Mixin DB db, @Mixin TimeFilter tf) {
        System.out.println("Subcommand: price");

        if (db.opt != null)
            System.out.println("Database ignored with command price");

        if (tf != null) {
            if (tf.opt == null) {
                System.out.println("Either a --date or --period must be given");
                return -1;
            }

            if (tf.opt.period != null && tf.opt.period.get(0).equals("inception")) {
                System.out.println("Option --period inception not supported with command price");
                return -1;
            }
            System.out.println(Utilities.msgTimeFilter(tf, false));
        }

        Function<Symbol, Function<BigDecimal, String>> strSymPrice =
            sym -> price -> String.format("%s: %,.2f", sym, price);
        Function<Symbol, Function<Tuple<BigDecimal, BigDecimal>, String>> strSymPriceDelta =
            sym -> prices -> String.format("%s: %,.2f -> %,.2f, delta %+,.2f", sym, prices._1,
                prices._2, prices._2.subtract(prices._1));
        Function<String, Function<String, String>> combineLines = msg -> line -> msg + "\n" + line;
        if (stockFilter == null || stockFilter.isEmpty()) {
            System.out.println("At least one ticker symbol must be given");
            return -1;
        } else {
            // TODO: changed parseTimeFilter behaviour when tf.opt == null
            List<LocalDate> period = Utilities.parseTimeFilter(tf);

            Result<Map<Symbol, Stock>> stocks = Stock.stocks(List.of(stockFilter));
            Result<String> result;
            if (period.isEmpty()) {
                result = stocks.map(m -> m
                    .mapVal(Stock::getPrice)
                    .toList(strSymPrice).reduce(combineLines));
            } else if (period.size() == 1) {
                result = stocks.map(m -> m
                        .mapVal(stock -> stock.getPriceOn(period.head())))
                    .flatMap(Map::flattenResultVal)
                    .map(m -> m.toList(strSymPrice).reduce(combineLines));
            } else { // (period.size() == 2)
                result =  stocks
                    .map(m -> m.mapVal(stock -> stock.getPriceOn(period.head())
                        .flatMap(initPrice -> stock.getPriceOn(period.tail().head())
                            .map(endPrice -> new Tuple<>(initPrice, endPrice)))))
                    .flatMap(Map::flattenResultVal)
                    .map(m -> m.toList(strSymPriceDelta).reduce(combineLines));
            }

            // TODO: changed parseTimeFilter behaviour when tf.opt == null
            outputResult(result, Utilities.parseTimeFilter(tf),
                "There was a problem (result empty)", "Error: ");
        }
        return 1;
    }

    private static void outputResult(Result<String> res, List<LocalDate> period, String emptyMsg,
                                     String errorMsg) {
        if (!period.isEmpty()) {
            String header = period.size() == 1
                ? String.format("Ticker: %tF", period.get(0))
                : String.format("Ticker: %tF -> %tF", period.get(0), period.get(1));
            System.out.println(header);
        }

        res.failIfEmpty(emptyMsg)
            .forEachOrFail(System.out::println)
            .forEach(failure -> System.out.println(errorMsg + failure));
    }

    @Command(name = "value")
    int value(@Mixin DB db, @Mixin TimeFilter tf) {
        Result<DataSource> rDS = Utilities.parseDbOption(db)
            .flatMap(ds -> Utilities.convertToResult(txFile)
                .flatMap(Utilities::checkTxIn)
                .flatMap(ds::insertTransactions));

        if (tf != null)
            System.out.println(Utilities.msgTimeFilter(tf, true));

        List<Symbol> symFilter = Utilities.parseStockFilter(stockFilter);
        if (!symFilter.isEmpty())
            System.out.println(
                "Only considering transactions from the following stocks: "
                    + Utilities.prettifyList(symFilter));

        var rTXs = rDS.flatMap(DataSource::getTransactions)
            .flatMap(t -> t._2.close()
                .map(ignore -> t._1)
//                .map(l -> {System.out.println(l); return l;})
                .map(l -> l.filter(tx -> symFilter.contains(tx.getSymbol())))
                .map(l -> l.filter(Utilities.timePeriodComparator(tf)))
//                .map(l -> {System.out.println(l); return l;})
                .mapEmptyCollection()
                .flatMap(Portfolio::portfolio)
                .map(Portfolio::currentValue));

        rTXs.failIfEmpty("No transactions provided")
            .forEachOrFail(res -> System.out.println("Portfolio valued on " + Utilities.computeDate(tf) + " at " + res))
            .forEach(failure -> System.out.println("Error: " + failure));
        return 1;
    }

    @Command(name = "avgCost")
    void avgCost(@Mixin DB db, @Mixin TimeFilter tf) {
        // --date not supported
        System.out.println("Subcommand: avgCost");
    }

    @Command(name = "twrr")
    void TWRR(@Mixin DB db, @Mixin TimeFilter tf) {
        // --date not supported
        System.out.println("Subcommand: twrr");
    }

    // TODO: business logic goes in here
    @Override
    public Integer call() {
        System.out.println("No subcommand specified, assuming subcommand value");
        return value(db, tf);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Analysiolo()).execute(args);
        System.exit(exitCode);
    }
}