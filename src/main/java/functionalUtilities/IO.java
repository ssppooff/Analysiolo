package functionalUtilities;

public interface IO<A> {
  IO<Nothing> empty = () -> Nothing.instance;

  A run();
}