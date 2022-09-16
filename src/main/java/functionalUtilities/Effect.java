package functionalUtilities;

public interface Effect<T> {
  void apply(T t);
}