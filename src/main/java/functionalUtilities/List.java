package functionalUtilities;

import java.util.AbstractSequentialList;
import java.util.Collection;
import java.util.ListIterator;
import java.util.function.Function;

public abstract class List<E> extends AbstractSequentialList<E> {
  @SuppressWarnings("rawtypes")
  private static final List EMPTY = new Nil();

  public abstract E head();
  public abstract List<E> tail();
  @Override
  public abstract boolean isEmpty();

  private List() {}

  public List<E> prepend(E e) {
    return new Node<>(e, this);
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
    return foldLeft(empty(), acc -> e -> new Node<>(e, acc));
  }

  public static <E> List<E> concat(List<E> l1, List<E> l2) {
    return l1.reverse().foldLeft(l2, acc -> acc::prepend);
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
    // TODO
    return Stream.unfold(this, l ->
        l.isEmpty()
            ? Result.empty()
            : Result.success(new Tuple<>(l.head(), l.tail())));
  }

  public static <T> Result<List<T>> flattenResult(List<Result<T>> listResults) {
    Result<List<T>> rList = Result.success(empty());
    for (Result<T> rElement : listResults.reverse()) {
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

  public <T, U> List<U> zipWith(List<T> l, Function<E, Function<T, U>> f) {
    return zipWith(this, l, f);
  }
  public static <E, T, U> List<U> zipWith(List<E> l1, List<T> l2, Function<E, Function<T, U>> f) {
    return l1.isEmpty() || l2.isEmpty()
        ? empty()
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
    return size_(0, this).eval();
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
  public static <T> List<T> empty() {
    return EMPTY;
  }

  @SafeVarargs
  public static <E> List<E> of(E... es) {
    List<E> l = empty();
    for (int i = es.length - 1; i >= 0; i--)
      l = new Node<>(es[i], l);
    return l;
  }

  public static <E> List<E> of(Collection<? extends E> c) {
    ListIterator<? extends E> it = c.stream().toList().listIterator(c.size());
    List<E> res = empty();
    while (it.hasPrevious())
      res = new Node<>(it.previous(), res);
    return res;
  }
}