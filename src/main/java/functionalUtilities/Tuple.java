package functionalUtilities;

import java.util.function.Function;

public class Tuple<A, B> {
  public A _1;
  public B _2;

  public Tuple(A _1, B _2) {
    this._1 = _1;
    this._2 = _2;
  }

  public A _1() {
    return _1;
  }

  public B _2() {
    return _2;
  }

  public static <A, B> Result<Tuple<A, B>> flattenResult(Tuple<Result<A>, Result<B>> t) {
    return Result.map2(t._1, t._2, a -> b -> new Tuple<>(a, b));
  }

  public static <A, B> Result<Tuple<A, B>> flattenResultLeft(Tuple<Result<A>, B> t) {
    return t._1.map(a -> new Tuple<>(a, t._2));
  }

  public static <A, B> Result<Tuple<A, B>> flattenResultRight(Tuple<A, Result<B>> t) {
    return t._2.map(b -> new Tuple<>(t._1, b));
  }

  public <C> Tuple<C, B> mapLeft(Function<A, C> f) {
    return new Tuple<>(f.apply(_1), _2);
  }

  public <C> Tuple<A, C> mapRight(Function<B, C> f) {
    return new Tuple<>(_1, f.apply(_2));
  }

  @Override
  public int hashCode() {
    return 1 + _1.hashCode() + _2.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if ( !(obj instanceof Tuple<?, ?> that) )
      return false;

    return _1.equals(that._1)
        && _2.equals(that._2);
  }

  @Override
  public String toString() {
    return toString(",");
  }

  public String toString(String delimiter) {
    return "(" + _1 + delimiter + " " + _2 + ")";
  }
}