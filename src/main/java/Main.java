import functionalUtilities.FileReader;
import functionalUtilities.Result;
import functionalUtilities.Stream;
import stockAPI.Parser;

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
}