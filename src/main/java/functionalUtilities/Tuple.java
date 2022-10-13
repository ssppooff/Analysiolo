package functionalUtilities;

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

  @Override
  public String toString() {
    return String.format("(" + _1 + ", " + _2 + ")");
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
}