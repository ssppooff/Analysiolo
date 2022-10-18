import functionalUtilities.FileReader;
import functionalUtilities.List;
import functionalUtilities.Result;
import functionalUtilities.Stream;
import functionalUtilities.Tuple;
import java.time.LocalDate;
import java.util.function.Function;
import stockAPI.Parser;
import stockAPI.Transaction;

public class Main {

    public static void main(String[] args) {
        String path = "src/test/java/testdata.txt";
        Result<FileReader> fR = FileReader.read(path);
        var f = fR.map(input -> Stream.unfold(input, Parser::createTx));
//        Result<Stream<String>> rStr = FileReader.read(path);

//        var f = rStr.map(str ->
//                str.map(Main::readTransaction))
//            .map(Stream::toList)
//            .getOrElse(new ArrayList<>())
//            ;

//        functionalUtilities.Stream<String> str = functionalUtilities.Stream
//            .unfold();
//        Scanner s = new Scanner(System.in);
//        Tuple<Transaction, Scanner> t = readTransaction(s.nextLine());
//        t = readTransaction(t._2.nextLine());
        // CLI args
        // if no args, show help
        // if only db.sqlite, show prompt
        // if db.sqlite & file, parse file and add transactions to db        try {
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
                : Result.<Tuple<LocalDate, Integer>>failure("Wrong date after line " + t._2)
            ));
        return res.map(ignored -> l);
    }
}