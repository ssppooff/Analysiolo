package ch.cottier.functionalUtilities;

import java.util.function.Function;

public class Tuple3<A, B, C> {
  public A _1;
  public B _2;
  public C _3;

  public Tuple3(A _1, B _2, C _3) {
    this._1 = _1;
    this._2 = _2;
    this._3 = _3;
  }

  public A _1() {
    return _1;
  }

  public B _2() {
    return _2;
  }

  public C _3() {
    return _3;
  }

  public <D> Tuple3<D, B, C> mapVal1(Function<A, D> f) {
    return new Tuple3<>(f.apply(_1), _2, _3);
  }

  public <D> Tuple3<A, D, C> mapVal2(Function<B, D> f) {
    return new Tuple3<>(_1, f.apply(_2), _3);
  }

  public <D> Tuple3<A, B, D> mapVal3(Function<C, D> f) {
    return new Tuple3<>(_1, _2, f.apply(_3));
  }

  @Override
  public int hashCode() {
    return 1 + _1.hashCode() + _2.hashCode() + _3.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if ( !(obj instanceof Tuple3<?, ?, ?> that) )
      return false;

    return _1.equals(that._1)
        && _2.equals(that._2)
        && _3.equals(that._3);
  }

  @Override
  public String toString() {
    return toString(",");
  }

  public String toString(String delimiter) {
    return "(" + _1 + delimiter + " " + _2 + delimiter + " " + _3 + ")";
  }
}