package ch.cottier.functionalUtilities;

import java.lang.reflect.ParameterizedType;
import java.util.AbstractSequentialList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.ListIterator;
import java.util.function.BiFunction;
import java.util.function.Function;

@SuppressWarnings("unused")
public abstract class List<E> extends AbstractSequentialList<E> {
  @SuppressWarnings("rawtypes")
  private static final List EMPTY = new Nil();

  public abstract E head();
  public abstract List<E> tail();
  @Override
  public abstract boolean isEmpty();
  public abstract Result<E> last();
  public abstract List<E> append(E e);
  protected abstract Class<E> getTypeArgument();
  private int size = -1;

  private List() {}

  public List<E> prepend(E e) {
    return new Node<>(e, this);
  }

  public static <E, S> List<E> unfold(S s, Function<S, Result<Tuple<E, S>>> f) {
    return f.apply(s).map(t -> unfold(t._2, f).prepend(t._1)).getOrElse(list());
  }

  public <T> List<T> map(Function<E, T> f) {
    return foldRight(List.list(), e -> acc -> acc.prepend(f.apply(e)));
  }

  public <T> List<T> mapWithIdx(Function<Integer, Function<E, T>> f) {
    return foldLeftWithIdx(List.<T>list(), idx -> acc -> e -> acc.prepend(f.apply(idx).apply(e)))
        .reverse();
  }

  public <T> T foldRight(T acc, Function<E, Function<T, T>> f) {
    return reverse().foldLeft(acc, accx -> e -> f.apply(e).apply(accx));
  }

  public <T> T foldRightWithIdx(T acc, Function<Integer, Function<E, Function<T, T>>> f) {
    return reverse().foldLeftWithIdx(acc, idx -> accx -> e -> f.apply(idx).apply(e).apply(accx));
  }

  public List<E> filter(Function<E, Boolean> p) {
    return foldRight(List.list(), e -> acc -> p.apply(e) ? acc.prepend(e) : acc);
  }

  public <K> Map<K, List<E>> groupBy(Function<E, K> f) {
    return foldRight(Map.empty(), e -> m -> {
      K key = f.apply(e);
      return m.put(key,
          m.get(key).getOrElse(list()).prepend(e));
    });
  }

  public <T> T reduce(T identity, BiFunction<T, E, T> f) {
    return reduce(identity, x -> y -> f.apply(x, y));
  }

  public <T> T reduce(T identity, Function<T, Function<E, T>> f) {
    return foldLeft(identity, acc -> e -> f.apply(acc).apply(e));
  }

  public E reduce(BiFunction<E, E, E> f) {
    return reduce(x -> y -> f.apply(x, y));
  }

  public E reduce(Function<E, Function<E, E>> f) {
    return tail().foldLeft(head(), acc -> e -> f.apply(acc).apply(e));
  }

  public <T> T foldLeftAbsorbEl(T acc, E zero, Function<T, Function<E, T>> f) {
    return foldLeftAbsorbEl(acc, zero::equals, f);
  }
  public <T> T foldLeftAbsorbEl(T acc, Function<E, Boolean> p, Function<T, Function<E, T>> f) {
    return foldLeftAbsorbEl_(this, acc, p, f).eval();
  }
  private static <E, T> TailCall<T> foldLeftAbsorbEl_(List<E> l, T acc, Function<E, Boolean> p, Function<T, Function<E, T>> f) {
    return p.apply(l.head()) || l.isEmpty()
        ? TailCall.ret(acc)
        : TailCall.suspend(() -> foldLeftAbsorbEl_(l.tail(), f.apply(acc).apply(l.head()), p, f));
  }

  public <T> T foldLeftAbsorbAcc(T acc, T zero, Function<T, Function<E, T>> f) {
    return foldLeftAbsorbAccPred(acc, zero::equals, f);
  }
  public <T> T foldLeftAbsorbAccPred(T acc, Function<T, Boolean> p, Function<T, Function<E, T>> f) {
    return foldLeftAbsorbAcc_(this, acc, p, f).eval();
  }
  private static <E, T> TailCall<T> foldLeftAbsorbAcc_(List<E> l, T acc, Function<T, Boolean> p, Function<T, Function<E, T>> f) {
    return p.apply(acc) || l.isEmpty()
        ? TailCall.ret(acc)
        : TailCall.suspend(() -> foldLeftAbsorbAcc_(l.tail(), f.apply(acc).apply(l.head()), p, f));
  }

  public <T> T foldLeftWithIdx(T acc, Function<Integer, Function<T, Function<E, T>>> f) {
    return foldLeftWithIdx_(this, 0, acc, f).eval();
  }
  private static <E, T> TailCall<T> foldLeftWithIdx_(List<E> l, int idx, T acc,
      Function<Integer, Function<T, Function<E, T>>> f) {
    return l.isEmpty()
        ? TailCall.ret(acc)
        : TailCall.suspend(() ->
            foldLeftWithIdx_(l.tail(), idx + 1, f.apply(idx).apply(acc).apply(l.head()), f));
  }

  public <T> T foldLeft(T acc, Function<T, Function<E, T>> f) {
    return foldLeft_(this, acc, f).eval();
  }
  private static <E, T> TailCall<T> foldLeft_(List<E> l, T acc, Function<T, Function<E, T>> f) {
    return l.isEmpty()
        ? TailCall.ret(acc)
        : TailCall.suspend(() -> foldLeft_(l.tail(), f.apply(acc).apply(l.head()), f));
  }

  public List<E> reverse() {
    return foldLeft(list(), acc -> acc::prepend);
  }

  public static <E> List<E> concat(List<E> l1, List<E> l2) {
    return l1.reverse().foldLeft(l2, acc -> acc::prepend);
  }

  public List<E> insert(int idx, E value) {
    return insert_(list(), this, value, idx).eval();
  }
  private TailCall<List<E>> insert_(List<E> acc, List<E> l, E cell, int count) {
    return count <= 0
        ? TailCall.ret(acc.foldLeft(l.prepend(cell), list -> list::prepend))
        : TailCall.suspend(() -> insert_(acc.prepend(l.head()), l.tail(), cell, count - 1));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public List<E> sortFP(Comparator<? super E> c) {
    Object[] a = this.toArray();
    Arrays.sort(a, (Comparator) c);
    return List.of((E[]) a);
  }

  @SuppressWarnings("unchecked")
  @Override
  public E[] toArray() {
    E[] r = (E[]) java.lang.reflect.Array.newInstance(getTypeArgument(), size());
    int i = 0;
    for (List<E> currHd = this; !currHd.isEmpty(); currHd = currHd.tail())
      r[i++] = currHd.head();
    return r;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T[] toArray(T[] a) {
    T[] r = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size());
    zip(Stream.from(0).take(size()).toList(), this)
        .forEach(t -> r[t._1] = (T) t._2);
    return r;
  }

  @SuppressWarnings("unchecked")
  public E[] toArrayPadded(int length, E padding) {
    int actualLength = length > size() ? length : size();
    E[] r = (E[]) java.lang.reflect.Array.newInstance(getTypeArgument(), actualLength);
    int i = 0;
    for (List<E> currHd = this; !currHd.isEmpty(); currHd = currHd.tail())
      r[i++] = currHd.head();
    Stream.from(size()).take(actualLength - size())
        .forEach(idx -> r[idx] = padding);
    return r;
  }

  @SuppressWarnings("unchecked")
  public <T> T[] toArrayPadded(T[] a, int length, T padding) {
    T[] r = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), length);
    zip(Stream.from(0).take(size()).toList(), this)
        .forEach(t -> r[t._1] = (T) t._2);
    Stream.from(size()).take(length - size())
        .forEach(idx -> r[idx] = padding);
    return r;
  }

  @Override
  public void sort(Comparator<? super E> c) {
    throw new IllegalStateException(
        "Cannot apply void sort() on immutable list, use sortFP()");
  }

  @Override
  public E get(int index) {
    return getAt(index).getOrThrow().head();
  }

  public Result<List<E>> getAt(int i) {
    return getAt_(this, i).eval();
  }
  private static <E> TailCall<Result<List<E>>> getAt_(List<E> l, int i) {
    return l.isEmpty()
        ? TailCall.ret(Result.failure("Index out of bounds"))
        : i == 0
            ? TailCall.ret(Result.success(l))
            : TailCall.suspend(() -> getAt_(l.tail(), i - 1));
  }

  public Stream<E> fpStream() {
    return Stream.unfold(this, l ->
        l.isEmpty()
            ? Result.empty()
            : Result.success(new Tuple<>(l.head(), l.tail())));
  }

  public static <E> Result<List<E>> flattenResult(List<Result<E>> listResults) {
    Result<List<E>> rList = Result.success(list());
    for (Result<E> rElement : listResults.reverse()) {
      if (rElement.isEmpty())
        continue;
      rList = prependResult(rElement, rList);
    }
    return rList;
  }

  private static <T> Result<List<T>> prependResult(Result<T> rEl, Result<List<T>> rList) {
    return rEl.flatMap(e -> rList.map(l -> l.prepend(e)));
  }

  public <T> List<Tuple<E, T>> zip(List<T> l) {
    return zip(this, l);
  }
  public static <E, T> List<Tuple<E, T>> zip(List<E> l1, List<T> l2) {
    return zipWith(l1, l2, h1 -> h2 -> new Tuple<>(h1, h2));
  }

  public <T, U> List<U> zipWith(List<T> l, BiFunction<E, T, U> f) {
    return zipWith(this, l, f);
  }
  public static <E, T, U> List<U> zipWith(List<E> l1, List<T> l2, BiFunction<E, T, U> f) {
    return zipWith(l1, l2, e -> t -> f.apply(e, t));
  }

  public <T, U> List<U> zipWith(List<T> l, Function<E, Function<T, U>> f) {
    return zipWith(this, l, f);
  }
  public static <E, T, U> List<U> zipWith(List<E> l1, List<T> l2, Function<E, Function<T, U>> f) {
    return l1.isEmpty() || l2.isEmpty()
        ? list()
        : new Node<>(f.apply(l1.head()).apply(l2.head()), zipWith(l1.tail(), l2.tail(), f));
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    s.append("(");
    return isEmpty()
        ? s.append("NIL)").toString()
        : toString_(s.append(head()), this.tail()).eval().append(")").toString();
  }
  private static <E> TailCall<StringBuilder> toString_(StringBuilder acc, List<E> l) {
    return l.isEmpty()
        ? TailCall.ret(acc.append(", NIL"))
        : TailCall.suspend(() -> toString_(acc.append(", ").append(l.head()), l.tail()));
  }

  private static final class Nil<E> extends List<E> {
    @Override
    public E head() {
      throw new IllegalStateException("head called on empty");
    }

    @Override
    public List<E> tail() {
      throw new IllegalStateException("tail called on empty");
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public Result<E> last() {
      return Result.empty();
    }

    @Override
    public List<E> append(final E e) {
      return prepend(e);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<E> getTypeArgument() {
    return (Class<E>) ((ParameterizedType) getClass().getGenericSuperclass())
        .getActualTypeArguments()[0];
    }
  }
  private static final class Node<E> extends List<E> {
    private final E head;
    private final List<E> tail;

    private Node(E head, List<E> tail) {
      this.head = head;
      this.tail = tail;
    }

    @Override
    public E head() {
      return head;
    }

    @Override
    public List<E> tail() {
      return tail;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public Result<E> last() {
      return last_(this).eval();
    }

    @Override
    public List<E> append(final E e) {
      return reverse().prepend(e).reverse();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<E> getTypeArgument() {
      return (Class<E>) head.getClass();
    }

    private static <E> TailCall<Result<E>> last_(List<E> l) {
      return l.tail().isEmpty()
          ? TailCall.ret(Result.success(l.head()))
          : TailCall.suspend(() -> last_(l.tail()));

    }
  }

  @Override
  public void add(int index, E element) {
    throw new IllegalStateException("add() not possible on immutable list");
  }

  @Override
  public E remove(int index) {
    throw new IllegalStateException("remove not possible on immutable list");
  }

  @Override
  public int size() {
    if (size == -1)
      return size = isEmpty()
          ? 0
          : size_(0, this).eval();
    else
      return size;
  }
  private static <E> TailCall<Integer> size_(int acc, List<E> l) {
    return l.isEmpty()
        ? TailCall.ret(acc)
        : TailCall.suspend(() -> size_(acc + 1, l.tail()));
  }

  @Override
  public ListIterator<E> listIterator(int index) {
    return new LItr<>(this, index);
  }

  private static class LItr<E> implements ListIterator<E> {
    private List<E> currNode;
    private int currIndex;

    private LItr(List<E> l, int i) {
      super();

      this.currNode = l;
    }

    @Override
    public boolean hasNext() {
      return !currNode.isEmpty();
    }

    @Override
    public E next() {
      E currHead = currNode.head();
      currNode = currNode.tail();
      currIndex += 1;
      return currHead;
    }

    @Override
    public boolean hasPrevious() {
      return false;
    }

    @Override
    public E previous() {
      throw new IllegalStateException("previous called on singly linked list");
    }

    @Override
    public int nextIndex() {
      return currIndex + 1;
    }

    @Override
    public int previousIndex() {
      throw new IllegalStateException("previousIndex called on singly linked list");
    }

    @Override
    public void remove() {
      throw new IllegalStateException("remove in not implemented");
    }

    @Override
    public void set(E e) {
      throw new IllegalStateException("set not supported on immutable list");
    }

    @Override
    public void add(E e) {
      throw new IllegalStateException("add not supported on immutable list");
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> List<T> list() {
    return EMPTY;
  }

  @SafeVarargs
  public static <E> List<E> list(E... es) {
    return of(es);
  }

  public static <E> List<E> list(Collection<? extends E> c) {
    return of(c);
  }

  @SafeVarargs
  public static <E> List<E> of(E... es) {
    List<E> l = list();
    for (int i = es.length - 1; i >= 0; i--)
      l = new Node<>(es[i], l);
    return l;
  }

  public static <E> List<E> of(Collection<? extends E> c) {
    ListIterator<? extends E> it = c.stream().toList().listIterator(c.size());
    List<E> res = list();
    while (it.hasPrevious())
      res = new Node<>(it.previous(), res);
    return res;
  }
}