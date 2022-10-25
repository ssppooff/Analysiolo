package functionalUtilities;

import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class Map<K, V> {
  private final ConcurrentHashMap<K, V> m;

  private Map() {
    this(new ConcurrentHashMap<>());
  }

  private Map(ConcurrentHashMap<K, V> m) {
    this.m = m;
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
    return stream(key -> val -> new Tuple<>(key, val));
  }

  public <U> Stream<U> stream(Function<K, Function<V, U>> f) {
    Function<Tuple<Enumeration<K>, ConcurrentHashMap<K, V>>,
        Result<Tuple<U, Tuple<Enumeration<K>, ConcurrentHashMap<K, V>>>>> next =
        t -> {
          if (t._1.hasMoreElements()) {
            K key = t._1.nextElement();
            return Result.success(new Tuple<>(f.apply(key).apply(m.get(key)), t));
          } else {
            return Result.empty();
          }
        };

    Tuple<Enumeration<K>, ConcurrentHashMap<K, V>> init = new Tuple<>(m.keys(), m);
    return Stream.unfold(init, next);
  }

  public List<Tuple<K, V>> toList() {
    return this.stream().toList();
  }

  public <U> List<U> toList(Function<K, Function<V, U>> f) {
    return this.stream(f).toList();
  }


  @Override
  public String toString() {
    return toList(key -> val -> String.format("%s: %s", key.toString(), val.toString())).toString();
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

  public <U> Map<K, U> mapKeyToVal(Function<K, U> f) {
    return this.map(key -> ignoreVal -> f.apply(key));
  }

  public <U> Map<U, V> mapKey(Function<K, U> f) {
    ConcurrentHashMap<U, V> m2 = new ConcurrentHashMap<>();
    for (java.util.Map.Entry<K, V> entry : m.entrySet())
      m2.put(f.apply(entry.getKey()), entry.getValue());
    return new Map<>(m2);
   }

   public <U> Map<K, U> map(Function<K, Function<V, U>> f) {
     Map<K, U> res = empty();
     for (K key : m.keySet())
       res.put(key, f.apply(key).apply(m.get(key)));
     return res;
   }

   public static <K, V> Result<Map<K, V>> flattenResultKey(Map<Result<K>, V> map) {
     Result<Map<K, V>> resMap = Result.success(Map.empty());
     for (java.util.Map.Entry<Result<K>, V> entry : map.getView().entrySet())
       resMap = putResultKey(resMap, entry.getKey(), entry.getValue());
     return resMap;
   }
   private static <K, V> Result<Map<K, V>> putResultKey(Result<Map<K, V>> rMap, Result<K> rKey, V val) {
     return rKey.flatMap(k -> rMap.map(m -> m.put(k, val)));
   }

   public static <K, V> Result<Map<K, V>> flattenResultVal(Map<K, Result<V>> map) {
     Result<Map<K, V>> resMap = Result.success(Map.empty());
     for (java.util.Map.Entry<K, Result<V>> entry : map.m.entrySet())
       resMap = putResultVal(resMap, entry.getKey(), entry.getValue());
     return resMap;
   }
   private static <K, V> Result<Map<K, V>> putResultVal(Result<Map<K, V>> map, K key, Result<V> val) {
     return val.flatMap(v -> map.map(m -> m.put(key, v)));

   }
}