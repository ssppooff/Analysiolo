package Analysiolo;

import functionalUtilities.List;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Callable;
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
import stockAPI.Transaction;

/* TODO:
    - period = ytd, year-to-date, one-year
    - output/rendering
        - refactor into applyTheme(data, theme) & Utilities.basicTheme()/themeWithDelta -> Func
        - create basic theme for data w/ 1 price & themeWithDelta for 2 prices
        - add '->' vertical line to themeWithDelta
    - Input validation
    - implement dry-run

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
- price (date, period): db & tx file ignored, needs stock filter
    - no date or period -> current price of each stock
    - date -> price of each stock on date (make sure date is today or before)
    - period -> for each date (make sure date is today or before): price of each stock, add change metrics
- list (date, period): show transactions, always filter stocks
    - no date or period: all transactions
    - date -> every transactions up to & incl. date
    - period -> every transactions inside period (inclusive)
- value (date, period): 1) filtered transactions, always filter stocks 2) value on specific date
    - no date or period -> all transactions, value today
    - date -> transactions up to & incl. date, value on date
    - period -> compute for each date: transactions up to & incl. date, value on date
- avgCost (date, period): avgCost for each stock over filtered transactions, always filter stocks
    - no date or period -> all transactions
    - date -> filter transactions up to & incl. date
    - period -> transactions between two dates (inclusive)
- twrr (date, period): 1) filtered transactions, always filter stocks 2) twrr until specific date
    - no date or period -> all transactions
    - date -> transactions up to & incl. date, twrr on date

* Specifying time periods
* -> if only one date: taken as from-date with end-date today
Following equivalent
$ analysiolo <subcommand> -d demo.db (equiv)
$ analysiolo <subcommand> -d demo.db --period inception (equiv)
$ analysiolo <subcommand> -d demo.db --period inception now (equiv)
$ analysiolo <subcommand> -d demo.db --period 2021-10-10 (from-date until now)
$ analysiolo <subcommand> -d demo.db --period 2021-10-10 now (equiv to ^)
$ analysiolo <subcommand> -d demo.db --period inception 2021-10-10 (from- to end-date)

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
    static Result<List<Transaction>> prepTransactions(DB db, File txFile) {
        return Utilities.parseDbOption(db)
            .flatMap(ds -> Utilities.convertToResult(txFile)
                .flatMap(Utilities::checkTxIn)
                .flatMap(ds::insertTransactions)
                .mapEmpty(() -> ds))
            .flatMap(DataSource::getTransactions)
            .flatMap(t -> t._2.close()
                .map(ignoreReturn -> t._1));
    }

    static Result<List<Transaction>> list_(DB db, TimeFilter tf,
                                          java.util.List<String> symbols, File txFile) {
        return prepTransactions(db, txFile)
            .map(txs -> txs.filter(Utilities.symbolComparator(symbols)))
            .map(txs -> txs.filter(Utilities.timePeriodComparator(tf)))
            .mapEmptyCollection();
    }

    static Result<String> validationDBOptions(DB db) {
        try {
            if (db == null || db.opt == null)
                return Result.failure("No path to database given, exiting");
            else {
                String dbPath = Utilities
                    .removePossibleExtensions(db.opt.dbPath == null
                        ? db.opt.newDBPath.getCanonicalPath()
                        : db.opt.dbPath.getCanonicalPath());
                File dbFile = new File(dbPath + ".mv.db");

                if (db.opt.newDBPath != null)
                    return dbFile.exists()
                        ? Result.failure("Database already exists at " + dbPath)
                        : Result.success("Creating new database at" + dbPath);
                else
                    return !dbFile.exists()
                        ? Result.failure("No database found at " + dbPath)
                        : Result.success("Using existing database at" + dbPath);

            }
        } catch (Exception e) {
            return Result.failure(e);
        }
    }

    static void dryRunFile(File txFile) throws Exception {
        if (txFile != null)
            System.out.println("Ingesting transactions into database from file " + txFile.getCanonicalPath());
    }

    static void dryRunStockFilter(java.util.List<String> stockFilter) {
        if (stockFilter != null)
            System.out.println("Filtering transactions for following stocks " + stockFilter);
    }

    static void dryRunTimeFiler(TimeFilter tf) {
        if (tf != null && tf.opt != null) {
            List<LocalDate> f = Utilities.parseTimeFilter(tf);
            if (f.size() == 1)
                System.out.println("Filtering for transactions before " + f.get(0));
            else
                System.out.println("Filtering for transactions between "
                    + f.get(0) + " and " + f.get(1));
        }
    }

    static void dryRunOutput() {
        System.out.println("Outputting list");
    }

    @Command(name = "list")
    int list(@Mixin DB db, @Mixin TimeFilter tf) throws Exception {
        Result<String> dbValidation = validationDBOptions(db);
        if (dbValidation.isFailure()) {
            dbValidation.forEachOrFail(doNothing -> {}).forEach(System.out::println);
            return -1;
        }

        if (dryRun) {
            dbValidation.forEach(System.out::println);
            dryRunFile(txFile);
            dryRunStockFilter(stockFilter);
            dryRunTimeFiler(tf);
            dryRunOutput();
            return 0;
        } else {
            Result<List<Transaction>> lTx = list_(db, tf, stockFilter, txFile)
                .failIfEmpty("()");

            lTx.forEachOrFail(l -> l.forEach(System.out::println))
               .forEach(System.out::println);
            return lTx.isFailure() ? -1 : 0;
        }
    }

    static Result<List<Tuple<LocalDate, List<Tuple<Symbol, BigDecimal>>>>>
        price_(TimeFilter tf, java.util.List<String> symbols) {
            return List.flattenResult(Utilities
                .parseTimeFilter(tf)
                .map(date -> Stock.stocks(symbols)
                    .map(m -> m
                        .mapVal(stock -> stock.getPriceOn(date))
                        .toList()
                        .map(Tuple::flattenResultRight))
                    .flatMap(List::flattenResult)
                    .map(l -> new Tuple<>(date, l))));
    }

    @Command(name = "price")
    int price(@Mixin DB db, @Mixin TimeFilter tf) {
        if (stockFilter == null || stockFilter.isEmpty()) {
            System.out.println("At least one ticker symbol must be given");
            return -1;
        }

        if (tf != null && tf.opt != null) {
            if (tf.opt.date != null && tf.opt.date.isAfter(LocalDate.now())) {
                System.out.println("--date cannot be in the future");
                return -1;
            }

            if (tf.opt.period != null) {
                if (tf.opt.period.get(0).equals("inception")) {
                    System.out.println("--period inception not supported with command price");
                    return -1;
                }
                if (tf.opt.period.size() == 2) {
                    try {
                        LocalDate d = LocalDate.parse(tf.opt.period.get(1));
                        if (d.isAfter(LocalDate.now())) {
                            System.out.println("--period cannot include the future");
                            return -1;
                        }
                    } catch (DateTimeParseException ignore) {
                    }
                }
            }
        }

        if (db.opt != null)
            System.out.println("Database ignored with command price");

        if (txFile != null)
            System.out.println("Transactions ignored with command price");

        if (dryRun) {
            List<LocalDate> dates = Utilities.parseTimeFilter(tf);
            StringBuilder s = new StringBuilder();
            s.append("Getting prices for ")
             .append(renderStockList(stockFilter))
             .append(" for date(s) ")
             .append(dates.get(0));

            if (dates.size() == 2)
                s.append(", and ").append(dates.get(1))
                 .append("\n")
                 .append("Adding change metrics");

            s.append("\n")
             .append("Outputting table");
            System.out.println(s);
            return 0;
        } else {
            Result<String> output = price_(tf, stockFilter)
                .map(Utilities::changeFormat)
                .map(t -> {
                    if (t._1.size() == 1)
                        return Utilities.applyTheme(t, Utilities.themeSimple());
                    else {
                        t = t.mapRight(m -> m.mapVal(Utilities::addChangeMetrics))
                             .mapLeft(header -> List.concat(header, List.of("𝝙", "𝝙 (%)")));
                        return Utilities.applyTheme(t, Utilities.themeChangeMetrics());
                    }
                })
                .map(Utilities::renderTable);
            output.forEachOrFail(System.out::println).forEach(err -> System.out.println("Error:"
                + " " + err));
            return output.isFailure() ? -1 : 0;
        }
    }

    static String renderStockList(java.util.List<String> stockFilter) {
        if (stockFilter.size() == 1)
            return stockFilter.get(0);

        StringBuilder s = new StringBuilder();
        s.append(stockFilter.get(0));
        for (int i = 1; i < stockFilter.size() -1; i++)
            s.append(", ").append(stockFilter.get(i));

        return s.append(", and ").append(stockFilter.get(stockFilter.size() - 1)).toString();
    }

    static Result<List<Tuple<LocalDate, BigDecimal>>> value_(DB db, TimeFilter tf, java.util.List<String> symbols, File txFile) {
        Result<List<Transaction>> lTx = prepTransactions(db, txFile)
            .map(txs -> txs.filter(Utilities.symbolComparator(symbols)));

        return List.flattenResult(
            Utilities.parseTimeFilter(tf)
                .map(date -> lTx
                    .flatMap(txs -> valueOnDateFromTx(txs, date)
                        .map(value -> new Tuple<>(date, value)))));
    }

    static Result<BigDecimal> valueOnDateFromTx(final List<Transaction> lTx, final LocalDate date) {
        return Portfolio
            .portfolio(lTx.filter(tx -> tx.getDate().compareTo(date) <= 0), date)
            .flatMap(pf -> pf.valueOn(date));
    }

    @Command(name = "value")
    int value(@Mixin DB db, @Mixin TimeFilter tf) throws Exception {
        Result<String> dbValidation = validationDBOptions(db);
        if (dbValidation.isFailure()) {
            dbValidation.forEachOrFail(doNothing -> {}).forEach(System.out::println);
            return -1;
        }

        if (dryRun) {
            dbValidation.forEach(System.out::println);
            dryRunFile(txFile);
            dryRunStockFilter(stockFilter);

            List<LocalDate> dates = Utilities.parseTimeFilter(tf);
            dates.forEach(date ->
                System.out.println("Computing value of portfolio on date + " + date));
            if (dates.size() == 2)
                System.out.println("Adding change metrics");

            dryRunOutput();
            return 0;
        } else {
            Result<List<Tuple<LocalDate, BigDecimal>>> output = value_(db, tf, stockFilter, txFile);
            output.forEachOrFail(System.out::println).forEach(err -> System.out.println("Error:"
                + " " + err));
            return output.isFailure() ? -1 : 0;
        }
    }

    static Result<BigDecimal> avgCost_(DB db, File txFile) {
        var f = prepTransactions(db, txFile);
        // - avgCost (date, period): avgCost for each stock over filtered transactions, always filter stocks
        //    - no date or period -> all transactions
        //    - date -> filter transactions up to & incl. date
        //    - period -> transactions between two dates (inclusive)
        // TimeFilter date/period
        // Stockfilter
        return Result.failure("not implemented");
    }

    @Command(name = "avgCost")
    int avgCost(@Mixin DB db, @Mixin TimeFilter tf) {
        if (tf != null && tf.opt != null && tf.opt.date != null) {
            System.out.println("--date option not supported with avgCost command");
            return -1;
        }

        if (dryRun) {
            // -- TimeFilter date/period
            // -- Stockfilter
            // -- DB create, use existing
            // -- Ingest txFile
            dryRunOutput();
            return 0;
        } else {
            Result<BigDecimal> output = avgCost_(db, txFile);
            output.forEachOrFail(System.out::println).forEach(err -> System.out.println("Error:"
                + " " + err));
            return output.isFailure() ? -1 : 0;
        }
    }

    static Result<BigDecimal> twrr_(DB db, File txFile) {
        // - twrr (date, period): 1) filtered transactions, always filter stocks 2) twrr until specific date
        //    - no date or period -> all transactions
        //    - date -> transactions up to & incl. date, twrr on date
        return Result.failure("not implemented");
    }

    @Command(name = "twrr")
    int TWRR(@Mixin DB db, @Mixin TimeFilter tf) {
        if (tf != null && tf.opt != null && tf.opt.date != null) {
            System.out.println("--date option not supported with twrr command");
            return -1;
        }

        if (dryRun) {
            // -- TimeFilter date/period
            // -- Stockfilter
            // -- DB create, use existing
            // -- Ingest txFile
            dryRunOutput();
            return 0;
        } else {
            Result<BigDecimal> output = twrr_(db, txFile);
            output.forEachOrFail(System.out::println).forEach(err -> System.out.println("Error:"
                + " " + err));
            return output.isFailure() ? -1 : 0;
        }
    }

    // Business logic goes in here
    @Override
    public Integer call() throws Exception {
        System.out.println("No subcommand specified, assuming subcommand value");
        return value(db, tf);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Analysiolo()).execute(args);
        System.exit(exitCode);
    }
}