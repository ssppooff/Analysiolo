package functionalUtilities;

import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class Map<K, V> {
  private final ConcurrentHashMap<K, V> m = new ConcurrentHashMap<>();

  private Map() {
    super();
  }

  public static <K, V> Map<K, V> empty() {
    return new Map<K, V>();
  }

  public boolean containsKey(K key) {
    return m.containsKey(key);
  }
  public Result<V> get(K key) {
    return m.containsKey(key)
        ? Result.success(m.get(key))
        : Result.empty();
  }

  public static <K, V> Map<K, V> add(Map<K, V> m, K key, V val) {
    m.m.put(key, val);
    return m;
  }

  public Map<K, V> put(K key, V val) {
    return add(this, key, val);
  }

  public java.util.Map<K, V> getView() {
    return Collections.unmodifiableMap(m);
  }

  public Stream<Tuple<K, V>> stream() {
    Function<Tuple<Enumeration<K>, ConcurrentHashMap<K, V>>,
        Result<Tuple<Tuple<K, V>, Tuple<Enumeration<K>, ConcurrentHashMap<K, V>>>>> foo =
        t1 -> {
          if (t1._1.hasMoreElements()) {
            K key = t1._1.nextElement();
            return Result.success(new Tuple<>(new Tuple<>(key, m.get(key)), t1));
          } else {
            return Result.empty();
          }
        };

    Tuple<Enumeration<K>, ConcurrentHashMap<K, V>> init = new Tuple<>(m.keys(), m);
    return Stream.unfold(init, foo);
  }

  @Override
  public int hashCode() {
    return super.hashCode() + 42;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;

    if (!(obj instanceof Map<?, ?> that))
      return false;

    return m.size() == that.m.size()
        && this.stream().equals(that.stream());
  }

  public <U> Map<K, U> mapVal(Function<V, U> f) {
    return this.map(ignoreKey -> f);
  }

  public <U> Map<K, U> mapKey(Function<K, U> f) {
    return this.map(key -> ignoreVal -> f.apply(key));
   }

   public <U> Map<K, U> map(Function<K, Function<V, U>> f) {
     Map<K, U> res = empty();
     for (K key : m.keySet())
       res.put(key, f.apply(key).apply(m.get(key)));
     return res;
   }
}