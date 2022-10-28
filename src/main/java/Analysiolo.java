import functionalUtilities.List;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.io.File;
import java.time.LocalDate;
import java.util.function.Function;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import java.util.concurrent.Callable;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ScopeType;
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
- value (date, period)
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

@Command(name = "analysiolo", version = "analysiolio 0.1", mixinStandardHelpOptions = true,
description = "Tool for simple analysis of a stock portfolio based on transactions.")
public class Analysiolo implements Callable<Integer> {

    @ArgGroup(exclusive = true)
    private DB db;

    static class DB {
        @Option(names = {"--create-database", "-c"}, description = "create a new database", required = true)
        String newDBPath;

        @Option(names = {"--database", "-d"}, description = "Path to database", required = true)
        String dbPath;
    }

    @Option(names = {"--dry-run", "-n"})
    private final boolean dryRun = false;

    @Option(names = {"--ingest", "--parse", "--file", "-f"}, description = "path to file with transactions to process")
    private File fileTransactions;

    @Option(names = "--filter", arity = "1..*")
    private String[] stockFilter;

    @ArgGroup(exclusive = true)
    private TimeFilter timeFilter;

    static class TimeFilter {
        @Option(names = "--period", arity = "1..2")
        String[] period;

        @Option(names = "--date", arity = "0..1", required = true)
        LocalDate date;
    }

    // subcommand
    @Command(name = "price")
    void price() {
        // --filter inception not supported
    }

    @Command(name = "value")
    void value() {}

    @Command(name = "avgCost")
    void avgCost() {
        // --date not supported
    }

    @Command(name = "twrr")
    void TWRR() {
        // --date not supported
    }

    // TODO: business logic goes in here
    @Override
    public Integer call() throws Exception {
        return null;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Analysiolo()).execute(args);
        System.exit(exitCode);
    }

    // Helper methods
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
}