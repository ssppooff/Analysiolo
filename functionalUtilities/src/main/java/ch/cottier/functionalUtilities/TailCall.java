package ch.cottier.functionalUtilities;

import java.util.function.Supplier;

public abstract class TailCall<T> {
  public abstract T eval();
  public abstract TailCall<T> resume();
  protected abstract boolean isSuspend();

  private static class Suspend<T> extends TailCall<T> {
    private final Supplier<TailCall<T>> suspend;

    public Suspend(Supplier<TailCall<T>> suspend) {
      this.suspend = suspend;
    }

    @Override
    public T eval() {
      TailCall<T> currCall = this;
      while (currCall.isSuspend())
        currCall = currCall.resume();
      return currCall.eval();
    }

    @Override
    public TailCall<T> resume() {
      return suspend.get();
    }

    @Override
    protected boolean isSuspend() {
      return true;
    }
  }
  private static class Return<T> extends TailCall<T> {
    private final T value;

    protected Return(T value) {
      this.value = value;
    }

    @Override
    public T eval() {
      return value;
    }

    @Override
    public TailCall<T> resume() {
      throw new IllegalStateException("Return has no resume");
    }

    @Override
    protected boolean isSuspend() {
      return false;
    }
  }

  public static <T> TailCall<T> suspend(Supplier<TailCall<T>> s) {
    return new Suspend<>(s);
  }

  public static <T> TailCall<T> ret(T val) {
    return new Return<>(val);
  }
}