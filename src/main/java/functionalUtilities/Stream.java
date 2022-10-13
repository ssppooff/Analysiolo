package functionalUtilities;

import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public abstract class Stream<T> {
  @SuppressWarnings("rawtypes")
  private static final Stream EMPTY = new Empty();
  public abstract T head();
  public abstract Result<T> headOptional();
  public abstract Stream<T> tail();
  public abstract boolean isEmpty();
  protected abstract Stream<T> cons(T t);
  public abstract Stream<T> take(int i);

  public static <T, S> Stream<T> unfold(S s, Function<S, Result<Tuple<T, S>>> f) {
    return f.apply(s).map(t -> cons(() -> t._1, () -> unfold(t._2, f))).getOrElse(empty());
  }

  public Stream<T> repeat(T value) {
    return unfold(value, s -> Result.success(new Tuple<>(value, value)));
  }

  public static Stream<Integer> from(int i) {
    return unfold(i, s -> Result.success(new Tuple<>(s, s + 1)));
  }

  public List<T> toList() {
    return foldRight(List.<T>list(), e -> acc -> acc.prepend(e));
  }

  public <U> Stream<U> map(Function<T, U> f) {
//    foldRight(Stream.empty(), e -> acc -> cons(() -> f.apply(e), () -> foldRight(acc.con)
    return cons(() -> f.apply(head()), () -> tail().map(f));
  }

  public Stream<T> filter(Function<T, Boolean> p) {
    Stream<T> s = dropWhile(e -> !p.apply(e));
    return s.isEmpty()
        ? s
        : cons(s::head, () -> s.tail().filter(p));
  }

  public static <T> Stream<T> dropWhile(Stream<T> s, Function<T, Boolean> p) {
    return s.dropWhile(p);
  }
  public Stream<T> dropWhile(Function<T, Boolean> p) {
    return dropWhile_(this, p).eval();
  }
  private static <T> TailCall<Stream<T>> dropWhile_(Stream<T> s, Function<T, Boolean> p) {
    return s.isEmpty()
        ? TailCall.ret(s)
        : p.apply(s.head())
          ? TailCall.suspend(() -> dropWhile_(s.tail(), p))
          : TailCall.ret(s);
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

  public <U> U foldRightAbsorbElement(U acc, T zero, Function<T, Function<U, U>> f) {
    return foldRightAbsorbElement(acc, e -> e.equals(zero), f);
  }

  public <U> U foldRightAbsorbElement(U acc, Function<T, Boolean> p, Function<T, Function<U, U>> f) {
    return this.isEmpty() || p.apply(head())
        ? acc
        : f.apply(head()).apply(tail().foldRightAbsorbElement(acc, p, f));
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

  // Ignores empty values
  public static <T> Result<Stream<T>> flattenResult(Stream<Result<T>> s) {
    //noinspection unchecked
    return s.isEmpty()
        ? Result.success(empty())
        : s.head().isEmpty()
          ? flattenResult(s.tail())
          : s.head().isFailure()
            ? (Result<Stream<T>>) s.head()
            : flattenResult(s.tail()).flatMap(acc -> s.head().map(el ->
                cons(() -> el, () -> acc)));
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
      return cons(() -> t, Stream::empty);
    }

    @Override
    public Stream<T> take(int i) {
      return this;
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
      return new Cons<>(() -> t, () -> this);
    }

    @Override
    public Stream<T> take(int i) {
      return i <= 0
          ? empty()
          : cons(head, () -> tail().take(i - 1));
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

  public static <T> Stream<T> of(List<T> l) {
    return l.isEmpty()
        ? empty()
        : new Cons<>(l::head, () -> Stream.of(l.tail()));
  }

  @SafeVarargs
  public static <T> Stream<T> of(T... arr) {
    Stream<T> res = empty();
    for (int i = arr.length -1; i >= 0; i--) {
      res = res.cons(arr[i]);
    }
    return res;
  }

  @Override
  public boolean equals(Object obj) {
    if ( !(obj instanceof Stream<?> that) )
      return false;

    List<T> l1 = this.toList();
    List<?> l2 = that.toList();
    return l1.size() != l2.size()
        ? false
        : l1.zipWith(l2, h1 -> h2 -> h1.equals(h2))
            .foldLeft(true, acc -> e-> acc && e);
  }
}