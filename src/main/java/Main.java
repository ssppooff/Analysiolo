import functionalUtilities.List;
import functionalUtilities.Result;
import functionalUtilities.Tuple;
import java.time.LocalDate;
import java.util.function.Function;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import java.util.concurrent.Callable;
import picocli.CommandLine.Parameters;
import stockAPI.Transaction;

/*
# CLI call options
* Create a new database and add some transactions
$ portfolio --new-database db-name(.db) --ingest file-with-transactions

* Add some transactions into the database
$ portfolio demo.db --ingest file-with-additional-transactions

* Compute the current value of the portfolio
$ portfolio demo.db
$ portfolio demo.db value

* Compute the value of the portfolio on a certain date
$ portfolio demo.db value 2021-12-31

* Compute the TWRR between since inception, 1 year, YTD, between two dates
$ portfolio demo.db twrr inception
$ portfolio demo.db twrr one-year
$ portfolio demo.db twrr ytd
$ portfolio demo.db twrr 2021-01-01 2021-10-31

* Compute the weighted avg acquisition price for each stock held: currently, at a specific date
$ portfolio demo.db avgCost
$ portfolio demo.db avgCost 2021-10-10
$ portfolio demo.db --filter=TSLA avgCost 2021-10-10

* Filter the used transactions to a specific stock
$ portfolio demo.db --filter=TSLA
$ portfolio demo.db --filter=TSLA,AVUV

* Get price of a specific stock: current, specific date
$ portfolio --get-price TSLA
$ portfolio --get-price TSLA 2021-10-10
* */
@Command(name = "pf-analysis", version = "pf-analysis 0.7", mixinStandardHelpOptions = true,
description = "Tool for simple analysis of a portfolio based on transactions.")
public class Main implements Callable<Integer> {

    // params: path to db, what else?
    @Parameters(index = 0, description = "The database with previously ingested transactions.")
    private final String dbPath = "";
    @Override
    public Integer call() throws Exception {
        return null;
    }

    // TODO: business logic goes in here
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
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
}