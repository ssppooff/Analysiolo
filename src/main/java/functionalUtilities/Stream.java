package functionalUtilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class Stream<T> {
  @SuppressWarnings("rawtypes")
  private static final Stream EMPTY = new Empty();
  public abstract T head();
  public abstract Result<T> headOptional();
  public abstract Stream<T> tail();
  public abstract boolean isEmpty();
  protected abstract Stream<T> cons(T t);

  public static <T, S> Stream<T> unfold(S s, Function<S, Result<Tuple<T, S>>> f) {
    return f.apply(s).map(t -> cons(() -> t._1, () -> unfold(t._2, f))).getOrElse(empty());
  }

  public Stream<T> repeat(T value) {
    return unfold(value, s -> Result.success(new Tuple<>(value, value)));
  }

  public static Stream<Integer> from(int i) {
    return unfold(i, s -> Result.success(new Tuple<>(s, s + 1)));
  }

  public Stream<T> take(int i) {
    return null;
  }

  public List<T> toList() {
    return toList_(new ArrayList<>(), this).eval();
  }

  private static <T> TailCall<List<T>> toList_(List<T> acc, Stream<T> s) {
    Function<T, Function<List<T>, List<T>>> cons = t -> l -> {
      l.add(t);
      return l;
    };
    return s.isEmpty()
        ? TailCall.ret(acc)
        : TailCall.suspend(() -> toList_(cons.apply(s.head()).apply(acc), s.tail()));
  }

  public <U> Stream<U> map(Function<T, U> f) {
//    foldRight(Stream.empty(), e -> acc -> cons(() -> f.apply(e), () -> foldRight(acc.con)
    return cons(() -> f.apply(head()), () -> tail().map(f));
  }

  public void forEach(Effect<T> ef) {
//    forEach_(this, ef).eval();
    forEachRec_(this, ef);
  }


  private static <T> void forEachRec_(Stream<T> s, Effect<T> ef) {
    if (s.isEmpty())
      return;
    ef.apply(s.head());
    forEachRec_(s.tail(), ef);
  }
  private static <T> TailCall<IO<Nothing>> forEach_(Stream<T> s, Effect<T> ef) {
    ef.apply(s.head());
    return s.isEmpty()
        ? TailCall.ret(IO.empty)
        : TailCall.suspend(() -> forEach_(s.tail(), ef));
  }

  public <U> U foldRight(U acc, Function<T, Function<U, U>> f) {
    return this.isEmpty()
        ? acc
        : f.apply(head()).apply(tail().foldRight(acc, f));
  }

  public <U> U foldLeft(U acc, Function<U, Function<T, U>> f) {
//    return this.isEmpty()
//        ? acc
//        : tail().foldLeft(f.apply(acc).apply(head()), f);
    return foldLeft_(this, acc, f).eval();
  }

  public static <T, U> U foldLeft(Stream<T> stream, U acc, Function<U, Function<T, U>> f) {
    return foldLeft_(stream, acc, f).eval();
  }
  private static <T, U> TailCall<U> foldLeft_(Stream<T> s, U acc, Function<U, Function<T, U>> f) {
    return s.isEmpty()
        ? TailCall.ret(acc)
        : TailCall.suspend(() -> foldLeft_(s.tail(), f.apply(acc).apply(s.head()), f));
  }

  private static class Empty<T> extends Stream<T> {
    @Override
    public T head() {
      throw new IllegalStateException("head called on empty");
    }

    @Override
    public Result<T> headOptional() {
      return Result.failure(new IllegalStateException("head called on empty"));
    }

    @Override
    public Stream<T> tail() {
      return empty();
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    protected Stream<T> cons(T t) {
      return cons(() -> t, () -> empty());
    }
  }
  private static class Cons<T> extends Stream<T> {
    private final Supplier<T> head;
    private final Supplier<Stream<T>> tail;

    protected Cons(Supplier<T> head, Supplier<Stream<T>> tail) {
      this.head = head;
      this.tail = tail;
    }

    @Override
    public T head() {
      return head.get();
    }

    @Override
    public Result<T> headOptional() {
      return Result.success(head());
    }

    @Override
    public Stream<T> tail() {
      return tail.get();
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    protected Stream<T> cons(T t) {
      return new Cons(() -> t, tail);
    }
  }

  protected static <T> Stream<T> cons(Supplier<T> hd, Supplier<Stream<T>> tl) {
    return new Cons<>(hd, tl);
  }

  private static <T> Stream<T> cons(T hd, Stream<T> tl) {
    return new Cons<>(() -> hd, () -> tl);
  }

  @SuppressWarnings("unchecked")
  public static <T> Stream<T> empty() {
    return EMPTY;
  }

  public static <T> Stream<T> of(T... l) {
    // TODO
    System.out.println("Implement this better");

    var f = Arrays.asList(l);
    Collections.reverse(f);

    Stream<T> s = empty();
    for (T t : f)
      s = cons(t, s);
    return s;
  }

  public static <T> Stream<T> convert(java.util.stream.Stream<T> javaStream) {
    Iterator<T> i = javaStream.iterator();
    Stream<T> s = empty();
    while (i.hasNext()) {
      s = s.cons(i.next());
    }
    throw new IllegalStateException("Stream.convert not implemented");
//    return null;
  }
}