package ch.cottier.stockAPI;

import ch.cottier.functionalUtilities.Result;

public class Symbol {
  private final String symbol;
  private final Result<String> name;

  private Symbol(String symbol, String name) {
    this.symbol = symbol;
    this.name = name.isEmpty()
        ? Result.empty()
        : Result.success(name);
  }

  public String getSymbolStr() {
    return symbol;
  }

  public Result<String> getName() {
    return name;
  }


  public static Symbol symbol(String symbol) {
    return new Symbol(symbol, "");
  }

  public static Symbol symbol(String symbol, String name) {
    return new Symbol(symbol, name);
  }
  @Override
  public String toString() {
    return symbol;
  }

  @Override
  public int hashCode() {
    return symbol.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (! (obj instanceof Symbol that) )
      return false;

    return symbol.equals(that.symbol);
  }
}