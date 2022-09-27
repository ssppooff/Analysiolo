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
}