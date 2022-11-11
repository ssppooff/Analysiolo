package Analysiolo;

import static Analysiolo.Analysiolo.TimeFilter;

import Analysiolo.Analysiolo.DB;
import functionalUtilities.FileReader;
import functionalUtilities.List;
import functionalUtilities.Result;
import functionalUtilities.Stream;
import functionalUtilities.Tuple;
import java.io.File;
import java.io.IOException;
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

    static String renderTable(List<List<String>> data, List<String> header) {
        return renderTable(data, header, "-");
    }

    static String renderTable(List<List<String>> data, List<String> header, String separator) {
        List<String> rule = header.map(hd -> separator.repeat(hd.length()));
        return renderTable(data.prepend(rule).prepend(header));
    }

    static String renderTable(List<List<String>> data) {
        String[][] paddedData = padData(convertToArray(data));

        StringBuilder s = new StringBuilder();
        for (final String[] line : paddedData) {
            for (final String cell : line)
                s.append(cell).append(" ");
            s.append("\n");
        }

        return s.toString();
    }

    static String[][] convertToArray(final List<List<String>> data) {
        int width = data.map(List::size)
            .foldLeft(0, max -> size -> max > size ? max : size);
        return data.map(row -> row.toArrayPadded(width, "")).toArray();

        /* procedural
        List<List<String>> remainingData = data;
        List<String> remainingRowData;
        for (int rowIdx = 0; rowIdx < height; rowIdx++) {
            remainingRowData = remainingData.head();
            remainingData = remainingData.tail();

            for (int colIdx = 0; colIdx < width; colIdx++) {
                if (remainingRowData.isEmpty()) {
                    r[colIdx][rowIdx] = "";
                } else {
                    r[colIdx][rowIdx] = remainingRowData.head();
                    remainingRowData = remainingRowData.tail();
                }
            }
        }
         */
    }

    static String[][] padData(final String[][] data) {
        int height = data.length;
        int width = data[0].length;
        String[][] r = new String[height][width];

        int[] colMax = new int[width];
        Arrays.fill(colMax, 0);

        for (int col = 0; col < width; col ++) {
            for (String[] line : data)
                colMax[col] = Math.max(line[col].length(), colMax[col]);

            for (int row = 0; row < height; row++) {
                String cell = data[row][col] == null ? "" : data[row][col];
                int padding = colMax[col] - cell.length();
                r[row][col] = cell + " ".repeat(padding);
            }
        }

        /* using streams
        String[][] r = new String[width][height];
        Stream<Integer> columns = Stream.from(0).take(width);
        Stream<Integer> rows = Stream.from(0).take(height);
        columns.forEach(col -> rows
            .map(row -> {
                colMax[col] = Math.max(data[col][row].length(), colMax[col]);
                return row;
            })
            .forEach(row -> {
                String cell = data[col][row];
                int padding = colMax[col] - cell.length();
                r[col][row] = cell + " ".repeat(padding);
            } ));
        */

        return r;
    }
}