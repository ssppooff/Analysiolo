import functionalUtilities.FileReader;
import functionalUtilities.Input;
import functionalUtilities.Result;
import functionalUtilities.Stream;
import functionalUtilities.Tuple;
import parser.Transaction;

public class Main {

    public static void main(String[] args) {
        String path = "src/main/resources/demodata.txt";
        Result<FileReader> fR = FileReader.read(path);
        var f = fR.map(input -> Stream.unfold(input, Main::createTx));
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

    public static Result<Tuple<Transaction, Input>> createTx(Input input) {
        return input.nextDate()
            .flatMap(date -> date._2.nextStr()
                .flatMap(txType -> txType._2.nextStr()
                    .flatMap(symbol -> symbol._2.nextInt()
                        .flatMap(nShares -> nShares._2.nextDbl()
                            .map(price ->
                                new Tuple<>(
                                    Transaction.transaction(date._1, symbol._1, nShares._1, price._1),
                                    price._2))))));
    }
}