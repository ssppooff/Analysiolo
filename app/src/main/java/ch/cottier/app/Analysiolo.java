package ch.cottier.app;

import ch.cottier.app.FooOptions.TFOptions;
import ch.cottier.functionalUtilities.List;
import ch.cottier.functionalUtilities.Map;
import ch.cottier.functionalUtilities.Result;
import ch.cottier.functionalUtilities.Tuple;
import ch.cottier.functionalUtilities.Tuple3;
import ch.cottier.stockAPI.Portfolio;
import ch.cottier.stockAPI.Stock;
import ch.cottier.stockAPI.Symbol;
import ch.cottier.stockAPI.Transaction;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/* TODO:

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
    - period -> transactions between two dates (inclusive)
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
    - period -> transactions inside period (inclusive), twrr on second/last date

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

class FooOptions {
    @SuppressWarnings("FieldMayBeFinal")
    @Option(names = {"--dry-run", "-n"})
    boolean dryRun = false;

    @Option(names = {"--ingest", "--parse", "--file", "-f"},
        description = "path to file with transactions to process")
    File txFile;

    @Option(names = "--filter", split = ",", arity = "1..*")
    java.util.List<String> stockFilter;

    @ArgGroup(exclusive = true)
    public DBOptions dbOptions;
    static class DBOptions {
        @Option(names = {"--create-database",
            "-c"}, description = "create a new database")
        File newDBPath;

        @Option(names = {"--database", "-d"}, description = "Path to database")
        File dbPath;
    }

    @ArgGroup(exclusive = true)
    public TFOptions tfOptions;
    static class TFOptions {
        @Option(names = "--period", arity = "1..2")
        java.util.List<String> period;

        @Option(names = "--date", arity = "1")
        LocalDate date;
    }
}

@SuppressWarnings("unused")
@Command(name = "analysiolo", version = "analysiolio 0.1", mixinStandardHelpOptions = true,
    description = "Tool for simple analysis of a stock portfolio based on transactions.",
    subcommands = { HelpCommand.class })
public class Analysiolo {

    Analysiolo() {}

    FooOptions fooOptions;

    // Business logic goes in here
    public Integer call() {
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Analysiolo()).execute(args);
        System.exit(exitCode);
    }

    static void dryRunFile(File txFile) throws Exception {
        if (txFile != null)
            System.out.println("Ingesting transactions into database from file " + txFile.getCanonicalPath());
    }

    static void dryRunStockFilter(java.util.List<String> stockFilter) {
        if (stockFilter != null)
            System.out.println("Filtering transactions for following stocks " + stockFilter);
    }

    static void dryRunTimeFiler(TFOptions tf) {
        if (tf != null) {
            List<LocalDate> f = Utilities.parseTimeFilter(tf);
            if (f.size() == 1)
                System.out.println("Filtering for transactions before " + f.get(0));
            else
                System.out.println("Filtering for transactions between "
                    + f.get(0) + " and " + f.get(1));
        }
    }

    static void dryRunOutput() {
        System.out.println("Outputting result");
    }

    static Result<List<Transaction>> list_(FooOptions fooOptions) {
        return Utilities.prepTransactions(fooOptions.dbOptions, fooOptions.txFile)
                        .map(txs -> txs.filter(Utilities.symbolComparator(fooOptions.stockFilter)))
                        .map(txs -> txs.filter(Utilities.timePeriodComparator(fooOptions.tfOptions)))
                        .mapEmptyCollection();
    }

    @Command(name = "list",
        description = "Tool for simple analysis of a stock portfolio based on transactions.")
    int list(@Mixin FooOptions fooOptions) throws Exception {
        Result<String> dbValidation = Utilities.validationDBOptions(fooOptions.dbOptions);
        if (dbValidation.isFailure()) {
            dbValidation.forEachOrFail(doNothing -> {}).forEach(System.out::println);
            return -1;
        }

        if (fooOptions.dryRun) {
            dbValidation.forEach(System.out::println);
            dryRunFile(fooOptions.txFile);
            dryRunStockFilter(fooOptions.stockFilter);
            dryRunTimeFiler(fooOptions.tfOptions);
            dryRunOutput();
            return 0;
        } else {
            Result<List<Transaction>> lTx = list_(fooOptions);

            lTx.failIfEmpty("No transaction corresponds to filter criteria")
               .forEachOrFail(l -> l.forEach(System.out::println))
               .forEach(err -> System.out.println("Error: " + err));
            return lTx.isFailure() ? -1 : 0;
        }
    }

    static Result<List<Tuple<LocalDate, List<Tuple<Symbol, BigDecimal>>>>>
        price_(FooOptions.TFOptions tf, java.util.List<String> symbols) {
            return List.flattenResult(
                Utilities.parseTimeFilter(tf)
                    .map(date -> Stock.stocks(symbols)
                        .map(m -> m
                            .mapVal(stock -> stock.getPriceOn(date))
                            .toList()
                            .map(Tuple::flattenResultRight))
                        .flatMap(List::flattenResult)
                        .map(l -> new Tuple<>(date, l))));
    }

    @Command(name = "price")
    int price(@Mixin FooOptions fooOptions) {
        java.util.List<String> stockFilter = fooOptions.stockFilter;
        FooOptions.TFOptions tf = fooOptions.tfOptions;
        FooOptions.DBOptions db = fooOptions.dbOptions;
        if (stockFilter == null || stockFilter.isEmpty()) {
            System.out.println("At least one ticker symbol must be given");
            return -1;
        }

        if (tf != null) {
            if (tf.date != null && tf.date.isAfter(LocalDate.now())) {
                System.out.println("--date cannot be in the future");
                return -1;
            }

            if (tf.period != null) {
                if (tf.period.get(0).equals("inception")) {
                    System.out.println("--period inception not supported with command price");
                    return -1;
                }
                if (tf.period.size() == 2) {
                    LocalDate d = LocalDate.parse(tf.period.get(1));
                    if (d.isAfter(LocalDate.now())) {
                        System.out.println("--period cannot include the future");
                        return -1;
                    }
                }
            }
        }

        if (db != null)
            System.out.println("Database ignored with command price");

        if (fooOptions.txFile != null)
            System.out.println("Transactions ignored with command price");

        if (fooOptions.dryRun) {
            List<LocalDate> dates = Utilities.parseTimeFilter(tf);
            StringBuilder s = new StringBuilder();
            s.append("Getting prices for ")
             .append(Utilities.renderStockList(stockFilter))
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
            Result<String> output = price_(fooOptions.tfOptions, fooOptions.stockFilter)
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

    static Result<List<Tuple<LocalDate, BigDecimal>>> value_(FooOptions options) {
        Result<List<Transaction>> lTx =
            Utilities.prepTransactions(options.dbOptions, options.txFile)
                     .map(txs -> txs.filter(Utilities.symbolComparator(options.stockFilter)));

        return List.flattenResult(
            Utilities.parseTimeFilter(options.tfOptions)
                .map(date -> lTx
                    .flatMap(txs -> valueOnDateFromTx(txs, date)
                        .map(value -> new Tuple<>(date, value)))))
            .mapEmptyCollection();
    }

    static Result<BigDecimal> valueOnDateFromTx(final List<Transaction> lTx, final LocalDate date) {
        return Portfolio
            .portfolio(lTx.filter(tx -> tx.getDate().compareTo(date) <= 0), date)
            .flatMap(pf -> pf.valueOn(date));
    }

    @Command(name = "value")
    int value(@Mixin FooOptions fooOptions) throws Exception {
        Result<String> dbValidation = Utilities.validationDBOptions(fooOptions.dbOptions);
        if (dbValidation.isFailure()) {
            dbValidation.forEachOrFail(doNothing -> {}).forEach(System.out::println);
            return -1;
        }

        if (fooOptions.dryRun) {
            dbValidation.forEach(System.out::println);
            dryRunFile(fooOptions.txFile);
            dryRunStockFilter(fooOptions.stockFilter);

            List<LocalDate> dates = Utilities.parseTimeFilter(fooOptions.tfOptions);
            dates.forEach(date ->
                System.out.println("Computing value of portfolio on date + " + date));
            if (dates.size() == 2)
                System.out.println("Adding change metrics");

            dryRunOutput();
            return 0;
        } else {
            Result<List<Tuple<LocalDate, BigDecimal>>> output = value_(fooOptions);
            output.failIfEmpty("No transaction corresponds to filter criteria")
                  .forEachOrFail(System.out::println).forEach(err -> System.out.println("Error:"
                      + " " + err));
            return output.isFailure() ? -1 : 0;
        }
    }

    static Result<Map<Symbol, List<BigDecimal>>> avgCost_(FooOptions options) {
        Result<Map<Symbol, List<Transaction>>> filteredTxs = list_(options)
            .map(lTx -> lTx.filter(tx -> tx.getNumShares() > 0))
            .mapEmptyCollection()
            .map(lTx -> lTx.groupBy(Transaction::getSymbol));

        return filteredTxs
            .map(m -> m
                .mapVal(lTx -> lTx
                    .foldLeft(new Tuple3<>(new Tuple<>(BigDecimal.ZERO, 0),
                            lTx.head().getPrice(), lTx.head().getPrice()),
                        t3 -> tx -> {
                            BigDecimal min = tx.getPrice().min(t3._2);
                            BigDecimal max = tx.getPrice().max(t3._3);
                            BigDecimal totCost = t3._1._1.add(
                                tx.getPrice().multiply(new BigDecimal(tx.getNumShares())));
                            int totNum = t3._1._2 + tx.getNumShares();
                            return new Tuple3<>(new Tuple<>(totCost, totNum), min, max);
                        })
                    .mapVal1(t -> t._1.divide(new BigDecimal(t._2), RoundingMode.HALF_UP)))
                .mapVal(t3 -> List.of(t3._1, t3._2, t3._3)))
            .mapEmptyCollection();
    }

    @Command(name = "avgCost")
    int avgCost(@Mixin FooOptions fooOptions) throws Exception {
        if (fooOptions.tfOptions != null && fooOptions.tfOptions.date != null) {
            System.out.println("--date option not supported with avgCost command");
            return -1;
        }

        Result<String> dbValidation = Utilities.validationDBOptions(fooOptions.dbOptions);
        if (dbValidation.isFailure()) {
            dbValidation.forEachOrFail(doNothing -> {}).forEach(System.out::println);
            return -1;
        }

        if (fooOptions.dryRun) {
            dbValidation.forEach(System.out::println);
            dryRunFile(fooOptions.txFile);
            dryRunStockFilter(fooOptions.stockFilter);
            dryRunTimeFiler(fooOptions.tfOptions);
            System.out.println("Computing avg purchase cost over filtered transactions for each "
                + "stock");
            dryRunOutput();
            return 0;
        } else {
            List<String> colNames = List.of("avg cost", "min", "max");
            Result<Map<Symbol, List<BigDecimal>>> data =
                avgCost_(fooOptions);
            var output = data
                .map(out -> new Tuple<>(colNames, out))
                .map(t -> Utilities.applyTheme(t, Utilities.themeSimple()))
                .map(Utilities::renderTable);
            output.failIfEmpty("No transaction corresponds to filter criteria")
                  .forEachOrFail(System.out::println)
                  .forEach(err -> System.out.println("Error: " + err));
            return data.isFailure() ? -1 : 0;
        }
    }

    static Result<BigDecimal> twrr_(FooOptions options) {
        // - twrr (date, period): 1) filtered transactions, always filter stocks 2) twrr until specific date
        //    - no date or period -> all transactions
        //    - date -> transactions up to & incl. date, twrr on date
        //    - period -> transactions inside period (inclusive), twrr on second/last date

        // TODO twrr_
        // Filter transactions based on filters
        List<Transaction> lTx = list_(options).getOrThrow();

        LocalDate endDate = Utilities.parseTimeFilter(options.tfOptions).last().getOrThrow();
        // TODO: fail-fast for Res.fail & Res.empty
        // TODO list() -> streams()
        Result<List<BigDecimal>> growthFactors = Utilities.growthFactors(lTx, endDate);

        return growthFactors.map(l -> l.reduce(BigDecimal::multiply)
                                       .subtract(BigDecimal.ONE));

        /* How to compute TWRR
           - The time-weighted return breaks up the return on an investment portfolio into separate
             intervals based on whether money was added or withdrawn from the fund.
           - The time-weighted return measure is also called the geometric mean return, which is a
             complicated way of stating that the returns for each sub-period are multiplied by each
             other.
         */
    }

    @Command(name = "twrr")
    int TWRR(@Mixin FooOptions fooOptions) {
        if (fooOptions.tfOptions != null && fooOptions.tfOptions.date != null) {
            System.out.println("--date option not supported with twrr command");
            return -1;
        }

        if (fooOptions.dryRun) {
            // TODO twrr dry-run
            // -- TimeFilter date/period
            // -- Stockfilter
            // -- DB create, use existing
            // -- Ingest txFile
            dryRunOutput();
            return 0;
        } else {
            // TODO twrr correct output
            Result<BigDecimal> output = twrr_(fooOptions);
            output.forEachOrFail(System.out::println).forEach(err -> System.out.println("Error:"
                + " " + err));
            return output.isFailure() ? -1 : 0;
        }
    }
}