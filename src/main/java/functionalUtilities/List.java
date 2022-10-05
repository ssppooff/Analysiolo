package functionalUtilities;

public class List<T> extends java.util.ArrayList<T> {

  private List() {
    super();
  }

  public static <T> List<T> empty() {
    return new List<>();
  }

  public List<T> append(T t) {
    this.add(t);
    return this;
  }

  public static <T> Result<List<T>> flattenResult(List<Result<T>> listResults) {
    Result<List<T>> rList = Result.success(new List<>());
    for (Result<T> rElement : listResults) {
      if (rElement.isEmpty())
        continue;
      rList = appendElement(rList, rElement);
    }
    return rList;
  }
  private static <T> Result<List<T>> appendElement(Result<List<T>> rList, Result<T> rEl) {
    return rEl.flatMap(e -> rList.map(l -> l.append(e)));
  }
}