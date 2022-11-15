package Analysiolo;

import static Analysiolo.Analysiolo.TimeFilter;

import Analysiolo.Analysiolo.DB;
import functionalUtilities.FileReader;
import functionalUtilities.List;
import functionalUtilities.Map;
import functionalUtilities.Result;
import functionalUtilities.Stream;
import functionalUtilities.Tuple;
import functionalUtilities.Tuple3;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;
import java.util.regex.Pattern;
import stockAPI.DataSource;
import stockAPI.Parser;
import stockAPI.Symbol;
import stockAPI.Transaction;

final class Utilities {
    private Utilities() {}

    static String computeDate(final TimeFilter filter) {
        if (filter.opt == null)
            return LocalDate.now().toString();
        if (filter.opt.date != null)
            return filter.opt.date.toString();
        else {
            String lastElement = filter.opt.period.get(filter.opt.period.size() - 1);
            return lastElement.equals("inception")
                ? LocalDate.now().toString()
                : parsePeriod(lastElement).toString();
        }
    }

    static <E> String prettifyList(java.util.List<E> l) {
        StringBuilder s = new StringBuilder();
        for (E e : l)
            s.append(e).append(", ");

        return l.isEmpty()
            ? ""
            : s.substring(0, s.length() - 2);

    }

    static Result<DataSource> parseDbOption(DB db) {
        if (db == null || db.opt == null)
            return Result.failure("No path to database given, exiting");

        try {
            String dbPath = removePossibleExtensions(db.opt.dbPath == null
                ? db.opt.newDBPath.getCanonicalPath()
                : db.opt.dbPath.getCanonicalPath());
            File dbFullPath = new File(dbPath + ".mv.db");

            return db.opt.dbPath == null
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

    static String removePossibleExtensions(String path) {
        Pattern p1 = Pattern.compile(".*\\.mv\\.db$");
        Pattern p2 = Pattern.compile(".*\\.db$");

        return p1.matcher(path).matches()
            ? path.substring(0, path.length() - 6)
            : p2.matcher(path).matches()
                ? path.substring(0, path.length() - 3)
                : path;
    }

    static Result<File> convertToResult(File path) {
        return path == null
            ? Result.empty()
            : Result.success(path);
    }

    static Result<List<Transaction>> checkTxIn(File path) {
        Result<FileReader> fR = FileReader.read(path);
        Result<List<Transaction>> listTx =fR
            .flatMap(Parser::parseTransactions)
            .flatMap(t -> t._1.isEmpty()
                ? Result.empty()
                : Result.success(t._1))
            .map(Utilities::getOrderSeq)
            .flatMap(t -> Utilities.checkCorrectSequence(t._2, t._1))
            .map(l -> l.sortFP(Comparator.comparing(Transaction::getDate)));
        return fR.flatMap(FileReader::close).flatMap(ignore -> listTx);
    }

    // Default: true for ascending and all the same date
    static Tuple<Boolean, List<Transaction>> getOrderSeq(List<Transaction> l) {
        LocalDate first = l.head().getDate();
        Boolean res = l.isEmpty() || l.tail().isEmpty()
            ? true
            : getNextDate(l).isBefore(first)
                ? false
                : true;
        return new Tuple<>(res, l);
    }

    static LocalDate getNextDate(List<Transaction> l) {
        LocalDate first = l.head().getDate();
        Function<LocalDate, Boolean> isNextDate = d -> first.compareTo(d) != 0;
        return l.tail().foldLeftAbsorbAccPred(first, isNextDate, ignored -> Transaction::getDate);
    }

    static Result<List<Transaction>> checkCorrectSequence(List<Transaction> l,
                                                                  boolean ASC) {
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

    static List<LocalDate> parseTimeFilter(final TimeFilter tf) {
        if (tf == null || tf.opt == null)
            return List.of(LocalDate.now());
        else if (tf.opt.date != null)
            return List.of(tf.opt.date);
        else {
            List<LocalDate> period = List.of(tf.opt.period).map(Utilities::parsePeriod);
            return period.size() == 2 || period.head().equals(LocalDate.now())
                ? period
                : period.append(LocalDate.now());
        }
    }

    static List<LocalDate> parsePeriod(TimeFilter tf) {
        return tf.opt.period.size() == 2
            ? List.of(tf.opt.period).map(Utilities::parsePeriod)
            : List.of(parsePeriod(tf.opt.period.get(0)), LocalDate.now()); // size() == 2
    }
    static LocalDate parsePeriod(String s) {
        return switch (s) {
            case "now" -> LocalDate.now();
            case "inception" -> LocalDate.parse("1000-01-01");
            default -> LocalDate.parse(s);
        };
    }

    static Function<Transaction, Boolean> symbolComparator(final java.util.List<String> symbols) {
        return symbols == null
            ? tx -> true
            : tx -> symbols.contains(tx.getSymbol().getSymbolStr());
    }

    static List<Symbol> parseStockFilter(java.util.List<String> filter) {
        return filter == null
            ? List.list()
            : List.of(filter).map(Symbol::symbol);
    }

    static Function<Transaction, Boolean> timePeriodComparator(final TimeFilter filter) {
        if (filter == null || filter.opt == null)
            return tx -> true;
        if (filter.opt.date != null)
            return tx -> tx.getDate().compareTo(filter.opt.date) <= 0;
        else {
            List<String> period = List.of(filter.opt.period);
            if (period.size() == 1) {
                return switch (period.head()) {
                    case "now" -> tx -> tx.getDate().equals(LocalDate.now());
                    case "inception" -> tx -> true;
                    default -> tx -> tx.getDate().compareTo(LocalDate.parse(period.head())) >= 0;
                };
            } else { // period.size() == 2
                List<LocalDate> dates = period.map(Utilities::parsePeriod);
                return tx -> tx.getDate().compareTo(dates.head()) >= 0
                    && tx.getDate().compareTo(dates.tail().head()) <= 0;
            }
        }
    }

    static String msgTimeFilter(TimeFilter tf, boolean tx) {
        String word = tx ? "transactions" : "prices";
        System.out.print("Only considering " + word + " between/on date: ");
        return Utilities.prettifyList(Utilities.parseTimeFilter(tf));
    }

    static List<List<String>> formatDataWithHeader(List<Tuple<LocalDate, List<Tuple<Symbol, BigDecimal>>>> data) {
        List<LocalDate> dates = data.map(Tuple::_1);
        List<String> header = dates.size() >= 1
            ? List.of("𝝙", "𝝙 (%)")
            : List.list();
        header = dates.foldRight(header, date -> hd -> hd.prepend(date.toString()));
        header = header.prepend("Ticker");

        // TODO data.stream()
        List<Tuple3<Symbol, LocalDate, BigDecimal>> flattenedData =
            data.foldLeft(List.list(), resList -> outerT ->
                outerT._2.foldLeft(resList, innerResList -> innerT ->
                    innerResList.prepend(new Tuple3<>(innerT._1, outerT._1, innerT._2))));

        Map<Symbol, List<BigDecimal>> priceData =
            flattenedData.groupBy(Tuple3::_1)
                         .mapVal(lT3 -> lT3
                             .sortFP(Comparator.comparing(Tuple3::_2))
                             .map(Tuple3::_3));

        if (dates.size() >= 1)
            priceData = priceData.mapVal(Utilities::addChangeMetrics);

        return priceData.toList(sym -> prices ->
                            prices.map(bd -> String.format("%.3f", bd))
                                  .prepend(sym.getSymbolStr()))
                        .prepend(header);
    }

    static List<BigDecimal> addChangeMetrics(List<BigDecimal> prices) {
        BigDecimal origPrice = prices.get(0).setScale(6, RoundingMode.HALF_UP);
        BigDecimal newPrice = prices.get(1).setScale(6, RoundingMode.HALF_UP);
        BigDecimal abs = newPrice.subtract(origPrice);
        BigDecimal percent = abs.divide(origPrice, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
        return List.of(origPrice, newPrice, abs, percent);
    }

    static List<List<String>> addWithSeparator(final List<String> header, final List<List<String>> data) {
        String separator = "-";
        List<String> rule = header.map(hd -> separator.repeat(hd.length()));
        return data.prepend(rule).prepend(header);
    }

    static String renderTable(List<List<String>> data) {
        return renderTable(data.map(List::toArray).toArray());
    }

    static String renderTable(String[][] data) {
        StringBuilder s = new StringBuilder();
        for (final String[] line : data) {
            for (final String cell : line)
                s.append(cell).append(" ");
            s.append("\n");
        }

        return s.toString();
    }

    static List<List<String>> padCells(final List<List<String>> data) {
    return padCells(data, true);
    }

    static List<List<String>> padCells(final List<List<String>> data, boolean rightJustified) {
        int height = data.size();
        int width = data.head().size();

        Integer[][] strLen =
            data.map(row -> row.map(String::length).toArrayPadded(width, 0)).toArray();

        int[] colMax = new int[width];
        Arrays.fill(colMax, 0);
        Stream.from(0).take(width).forEach(col ->
            Stream.from(0).take(height).forEach(row ->
                colMax[col] = Math.max(strLen[row][col], colMax[col])));

        return data.map(row -> row
            .mapWithIdx(colIdx -> cell -> {
                String padding = " ".repeat(colMax[colIdx] - cell.length());
                return rightJustified ? padding + cell : cell + padding;
            }));
    }
}