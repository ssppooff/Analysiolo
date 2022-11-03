import functionalUtilities.FileReader;
import functionalUtilities.List;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.regex.Pattern;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import stockAPI.DataSource;
import stockAPI.Parser;
import stockAPI.Portfolio;
import stockAPI.Symbol;
import stockAPI.Transaction;

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
- value (date, period): no date given -> current value, date -> value on date, period -> consider
 transactions inside period and value at end of period
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
description = "Tool for simple analysis of a stock portfolio based on transactions.")
public class Analysiolo implements Callable<Integer> {

    public Analysiolo() {}

    @ArgGroup()
    private DB db;

    static class DB {
        @Option(names = {"--create-database", "-c"}, description = "create a new database", required = true)
        File newDBPath;

        @Option(names = {"--database", "-d"}, description = "Path to database", required = true)
        File dbPath;
    }

    @SuppressWarnings("FieldMayBeFinal")
    @Option(names = {"--dry-run", "-n"})
    private boolean dryRun = false;

    @Option(names = {"--ingest", "--parse", "--file", "-f"}, description = "path to file with transactions to process")
    private File fileTransactions;

    @Option(names = "--filter", split = ",", arity = "1..*")
    private String[] stockFilter;

    @ArgGroup()
    private TimeFilter timeFilter;

    static class TimeFilter {
        @Option(names = "--period", arity = "1..2", required = true)
        java.util.List<String> period;

        @Option(names = "--date", arity = "1", required = true)
        LocalDate date;
    }

    // subcommand
    @Command(name = "price")
    int price() {
        // --filter inception not supported
        System.out.println("Subcommand: price");
        return 1;
    }

    @Command(name = "value")
    int value() {
        System.out.println("Subcommand: value");
        Result<DataSource> rDS = checkForDB(db);

        if (fileTransactions != null)
            rDS = rDS.flatMap(ds -> ingestTransactions(ds, fileTransactions));

        if (timeFilter != null) {
            System.out.print("Only considering transactions between/on date: ");
            if (timeFilter.date != null)
                System.out.println(prettyPrintList(List.of(timeFilter.date)));
            else
                System.out.println(prettyPrintList(timeFilter.period));
        }

        List<Symbol> symFilter = stockFilter == null
                                 ? List.list()
                                 : List.of(stockFilter).map(Symbol::symbol);
        if (!symFilter.isEmpty())
            System.out.println(
                "Only considering transactions from the following stocks: "
                    + prettyPrintList(symFilter));

        var rTXs = rDS.flatMap(DataSource::getTransactions)
            .flatMap(t -> t._2.close()
                .map(ignore -> t._1)
//                .map(l -> {System.out.println(l); return l;})
                .map(l -> l.filter(tx -> symFilter.contains(tx.getSymbol())))
                .map(l -> l.filter(timePeriodComparator(timeFilter)))
//                .map(l -> {System.out.println(l); return l;})
                .mapEmptyCollection()
                .flatMap(Portfolio::portfolio)
                .map(Portfolio::currentValue));

        rTXs.failIfEmpty("No transactions provided")
            .forEachOrFail(res -> System.out.println("Portfolio valued on " + computeDate(timeFilter) + " at " + res))
            .forEach(failure -> System.out.println("Error: " + failure));
        return 1;
    }

    @Command(name = "avgCost")
    void avgCost() {
        // --date not supported
        System.out.println("Subcommand: avgCost");
    }

    @Command(name = "twrr")
    void TWRR() {
        // --date not supported
        System.out.println("Subcommand: twrr");
    }

    // TODO: business logic goes in here
    @Override
    public Integer call() {
        System.out.println("No subcommand specified, assuming subcommand value");
        return value();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Analysiolo()).execute(args);
        System.exit(exitCode);
    }

    // Helper methods
    protected static Function<Transaction, Boolean> timePeriodComparator(final TimeFilter filter) {
        if (filter == null)
            return tx -> true;
        if (filter.date != null)
            return tx -> tx.getDate().compareTo(filter.date) == 0;
        else {
            List<String> period = List.of(filter.period);
            if (period.size() == 1) {
                return switch (period.head()) {
                    case "now" -> tx -> tx.getDate().equals(LocalDate.now());
                    case "inception" -> tx -> true;
                    default -> tx -> tx.getDate().compareTo(LocalDate.parse(period.head())) >= 0;
                };
            } else { // period.size() == 2
                List<LocalDate> dates = period.map(Analysiolo::parsePeriod);
                return tx -> tx.getDate().compareTo(dates.head()) >= 0
                    && tx.getDate().compareTo(dates.tail().head()) <= 0;
            }
        }
    }

    private static LocalDate parsePeriod(String s) {
        return switch (s) {
            case "now" -> LocalDate.now();
            case "inception" -> LocalDate.parse("1000-01-01");
            default -> LocalDate.parse(s);
        };
    }

    protected static String computeDate(final TimeFilter filter) {
        if (filter == null)
            return LocalDate.now().toString();
        if (filter.date != null)
            return filter.date.toString();
        else {
            String lastElement = filter.period.get(filter.period.size() - 1);
            return lastElement.equals("inception")
                   ? LocalDate.now().toString()
                   : parsePeriod(lastElement).toString();
        }
    }

    private <E> String prettyPrintList(java.util.List<E> l) {
        StringBuilder s = new StringBuilder();
        for (E e : l)
            s.append(e).append(", ");

        return l.isEmpty()
               ? ""
               : s.substring(0, s.length() - 2);

    }

    private Result<DataSource> ingestTransactions(DataSource ds, File path) {
        System.out.println("Ingesting transactions from file " + path);
        return readTxFromFile(path).flatMap(ds::insertTransactions);
    }

    private Result<List<Transaction>> readTxFromFile(File path) {
        Result<FileReader> fR = FileReader.read(path);
        Result<List<Transaction>> listTx = fR.flatMap(Parser::parseTransactions)
            .flatMap(t -> t._1.isEmpty()
                          ? Result.empty()
                          : Result.success(t._1))
            .map(Analysiolo::getOrderSeq)
            .flatMap(t -> Analysiolo.checkCorrectSequence(t._2, t._1))
            .map(l -> l.sortFP(Comparator.comparing(Transaction::getDate)));
        return fR.flatMap(FileReader::close).flatMap(ignore -> listTx);
    }

    private Result<DataSource> checkForDB(DB db) {
        if (db == null)
            return Result.failure("No path to database given, exiting");

        try {
            String dbPath = removePossibleExtensions(db.dbPath == null
                                                     ? db.newDBPath.getCanonicalPath()
                                                     : db.dbPath.getCanonicalPath());
            File dbFullPath = new File(dbPath + ".mv.db");

            if (db.newDBPath != null)
                System.out.println("Creating a new database at: " + dbFullPath.getCanonicalPath());
            else
                System.out.println("Using database at: " + dbFullPath);


            return db.dbPath == null
                                     ? !dbFullPath.exists() // creating a new database
                                       ? DataSource.open(dbPath)
                                       : Result.failure("Database " + dbFullPath.getCanonicalPath()
                                                            + " already exists, aborting")
                                     : dbFullPath.exists() // using a pre-existing database
                                       ? DataSource.openIfExists(dbPath)
                                       : Result.failure("Database does not exist, aborting");
        } catch (IOException e) {
            return Result.failure(e);
        }
    }

    public static LocalDate getNextDate(List<Transaction> l) {
        LocalDate first = l.head().getDate();
        Function<LocalDate, Boolean> isNextDate = d -> first.compareTo(d) != 0;
        return l.tail().foldLeftAbsorbAccPred(first, isNextDate, ignored -> Transaction::getDate);
    }

    // Default: true for ascending and all the same date
    public static Tuple<Boolean, List<Transaction>> getOrderSeq(List<Transaction> l) {
        LocalDate first = l.head().getDate();
        Boolean res = l.isEmpty() || l.tail().isEmpty()
            ? true
            : getNextDate(l).isBefore(first)
                ? false
                : true;
        return new Tuple<>(res, l);
    }

    public static Result<List<Transaction>> checkCorrectSequence(List<Transaction> l, boolean ASC) {
        Function<Integer, Boolean> invertIfNecessary = ASC
            ? i -> i >= 0
            : i -> i <= 0;
        Result<Tuple<LocalDate, Integer>> res = l.foldLeftAbsorbAccPred(
            Result.success(new Tuple<>(l.head().getDate(), 0)),
            Result::isFailure,
            acc -> e -> acc.flatMap(t -> invertIfNecessary.apply(e.getDate().compareTo(t._1))
                ? Result.success(new Tuple<>(e.getDate(), t._2 + 1))
                : Result.failure("Wrong date after line " + t._2)
            ));
        return res.map(ignored -> l);
    }

    static String removePossibleExtensions(String path) {
        Pattern p1 = Pattern.compile(".*\\.mv\\.db$");
        Pattern p2 = Pattern.compile(".*\\.db$");

        return p1.matcher(path).matches()
            ? path.substring(0, path.length() - 6)
            : p2.matcher(path).matches()
                ? path.substring(0, path.length() - 3)
                  : path;
    }
}