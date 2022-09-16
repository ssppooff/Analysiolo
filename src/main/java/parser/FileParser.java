package parser;

import functionalUtilities.Result;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileParser {
  private final String path;

  private FileParser(String path) {
    this.path = path;
  }

  public static Result<FileParser> fileParser(String path) {
    try {
      Result.success(Files.lines(Paths.get(path)));
    } catch (IOException e) {
      Result.failure(e);
    }
//    return new FileParser(path);
    return null;
  }
}
