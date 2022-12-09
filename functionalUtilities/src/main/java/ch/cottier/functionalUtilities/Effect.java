package ch.cottier.functionalUtilities;

public interface Effect<T> {
  void apply(T t);
}