package ch.cottier.app;

import ch.cottier.app.Analysiolo.DB;
import ch.cottier.app.Analysiolo.TimeFilter;
import ch.cottier.functionalUtilities.FileReader;
import ch.cottier.functionalUtilities.List;
import ch.cottier.functionalUtilities.Map;
import ch.cottier.functionalUtilities.Result;
import ch.cottier.functionalUtilities.Stream;
import ch.cottier.functionalUtilities.Tuple;
import ch.cottier.functionalUtilities.Tuple3;
import ch.cottier.stockAPI.DataSource;
import ch.cottier.stockAPI.Parser;
import ch.cottier.stockAPI.Symbol;
import ch.cottier.stockAPI.Transaction;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

final class Utilities {
    private enum Theme {
        Header ((cell, width) -> padCellToLen(cell, width, true)),
        RowName ((cell, width) -> padCellToLen(cell, width, false)),
        Data ((cell, width) -> padCellToLen(cell, width, true));

        private final BiFunction<String, Integer, String> theme;

        Theme(BiFunction<String, Integer, String> theme) {
            this.theme = theme;
        }

        public String apply(String cell, int width) {
            return theme.apply(cell, width);
        }

        public BiFunction<String, Integer, String> get() {
            return theme;
        }

    }

    private Utilities() {}

    static Result<DataSource> parseDbOption(DB db) {
        try {
            if (db.opt == null)
                return Result.failure("db.opt is null");

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
            return period.size() == 1
                ? period.append(LocalDate.now())
                : period;
        }
    }

    static LocalDate parsePeriod(String s) {
        return switch (s) {
            case "now" -> LocalDate.now();
            case "inception" -> LocalDate.parse("1000-01-01");
            case "one-year" -> LocalDate.now().minusYears(1L);
            case "ytd", "year-to-date" -> LocalDate.of(LocalDate.now().getYear(), 1, 1);
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
                    case "ytd", "year-to-date", "one-year" ->
                        tx -> tx.getDate().compareTo(parsePeriod(period.head())) >= 0;
                    default -> tx -> tx.getDate().compareTo(LocalDate.parse(period.head())) >= 0;
                };
            } else { // period.size() == 2
                List<LocalDate> dates = period.map(Utilities::parsePeriod);
                return tx -> tx.getDate().compareTo(dates.head()) >= 0
                    && tx.getDate().compareTo(dates.tail().head()) <= 0;
            }
        }
    }

    static List<List<String>> applyTheme(Tuple<List<String>, Map<Symbol, List<BigDecimal>>> data,
        Function<List<List<String>>, List<List<String>>> theme) {
        List<String> header = List.list();
        Map<Symbol, List<BigDecimal>> priceData = data._2;

        header = data._1.foldRight(header, colName -> hd -> hd.prepend(colName))
                        .prepend("");

        List<List<String>> strData = priceData
            .toList(sym -> prices -> prices
                .map(priceBD -> String.format("%.3f", priceBD))
                .prepend(sym.getSymbolStr()))
            .prepend(header);

        // Apply theme
        return theme.apply(strData);
    }

    static Function<List<List<String>>, List<List<String>>> themeSimple() {
        String horzSep = "-";
        return s -> {
            List<List<String>> res = padMissingCells(s);
            List<Integer> columnWidth = getColumnMaxWidth(s);

            res = applyThemes(res, columnWidth, Theme.Header, Theme.RowName, Theme.Data);

            // create & insert horiz rule
            List<String> horzRule = res.head().map(hd -> horzSep.repeat(hd.length()));
            return res.tail().prepend(horzRule).prepend(res.head());
        };
    }

    static Function<List<List<String>>, List<List<String>>> themeChangeMetrics() {
        String horzSep = "-";
        String vertDateSep = "~>";
        String vertResSep = "|";
        return s -> {
            List<List<String>> res = padMissingCells(s);
            List<Integer> columnWidth = getColumnMaxWidth(s);

            res = applyThemes(res, columnWidth, Theme.Header, Theme.RowName, Theme.Data);

            // create horiz rule with gap for first vert rule and '+' for second
            // insert both vertical rules, not in the horiz rule, except for the "+"
            int vertRulePos = 2;
            int vertRulePos2 = 4;
            List<String> horzRule = res.head().map(hd -> horzSep.repeat(hd.length()))
                                       .insert(vertRulePos, " ".repeat(vertDateSep.length()))
                                       .insert(vertRulePos2, "+".repeat(vertResSep.length()));

            return res.tail().map(row -> row.insert(vertRulePos, vertDateSep)
                                           .insert(vertRulePos2, vertResSep))
                     .prepend(horzRule)
                     .prepend(res.head()
                                 .insert(vertRulePos, vertDateSep)
                                 .insert(vertRulePos2, vertResSep));
        };
    }

    static List<List<String>> padMissingCells(final List<List<String>> s) {
        int maxRowSize = s.map(List::size).reduce(0, max -> rowLen -> Math.max(rowLen, max));
        return s.map(row -> {
            int padLength = maxRowSize - row.size();
            return padLength > 0
                ? List.concat(row, Stream.repeat("").take(padLength).toList())
                : row;
        });
    }

    private static List<List<String>> applyThemes(List<List<String>> s, List<Integer> columnWidth,
        Theme themeHeader,
        Theme themeRowName,
        Theme themeData) {
        // You can change whether you want the top-left cell to either be styled
        // according to the header or the row-name by first applying either one or the other
        return s.map(row -> row.tail()
                               .prepend(themeRowName.apply(row.head(), columnWidth.head())))
                .tail().map(row -> List.zipWith(row, columnWidth, themeData.get()))
                .prepend(List.zipWith(s.head(), columnWidth, themeHeader.get()));
    }

    static String padCellToLen(String cell, int length, boolean rightJustified) {
        String padding = " ".repeat(length - cell.length());
        return rightJustified
            ? padding + cell
            : cell + padding;
    }

    static List<Integer> getColumnMaxWidth(final List<List<String>> rowData) {
        return rowData.map(row -> row.map(String::length))
                                            .reduce(row1 -> row2 -> row1.zipWith(row2, Math::max));
    }

    static Tuple<List<String>, Map<Symbol, List<BigDecimal>>> changeFormat(
        List<Tuple<LocalDate, List<Tuple<Symbol, BigDecimal>>>> data) {
        List<String> colNames = data.map(Tuple::_1).map(LocalDate::toString);

        List<Tuple3<Symbol, LocalDate, BigDecimal>> flattenedData =
            data.foldLeft(List.list(), resList -> outerT ->
                outerT._2.foldLeft(resList, innerResList -> innerT ->
                    innerResList.prepend(new Tuple3<>(innerT._1, outerT._1, innerT._2))));

        Map<Symbol, List<BigDecimal>> priceData =
            flattenedData.groupBy(Tuple3::_1)
                         .mapVal(lT3 -> lT3
                             .sortFP(Comparator.comparing(Tuple3::_2))
                             .map(Tuple3::_3));

        return new Tuple<>(colNames, priceData);
    }

    static List<BigDecimal> addChangeMetrics(List<BigDecimal> prices) {
        BigDecimal origPrice = prices.get(0).setScale(6, RoundingMode.HALF_UP);
        BigDecimal newPrice = prices.get(1).setScale(6, RoundingMode.HALF_UP);
        BigDecimal abs = newPrice.subtract(origPrice);
        BigDecimal percent = abs.divide(origPrice, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
        return List.of(origPrice, newPrice, abs, percent);
    }

    static String renderTable(List<List<String>> data) {
        return data.reduce("", outerStr -> row ->
            outerStr
                + row.reduce(" ", innerStr -> el -> innerStr + el + " ")
                + "\n");
    }
}