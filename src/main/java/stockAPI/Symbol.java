package stockAPI;

public class Symbol {
  String name;

  private Symbol(String name) {
    this.name = name;
  }

  public static Symbol symbol(String name) {
    return new Symbol(name);
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (! (obj instanceof Symbol that) )
      return false;

    return name.equals(that.name);
  }
}