package functionalUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StreamTest {

  @Test
  void of() {
    List<String> l = List.of("one", "two", "three");
    Stream<String> s = Stream.of(l);
    assertEquals("one", s.head());
    assertEquals("two", s.tail().head());
    assertEquals("three", s.tail().tail().head());
  }

  @Test
  void testFoldRightAbsorbElement() {
    Stream<Integer> l = Stream.of(1, 2, 3, 4, 5);
    Stream<Integer> res = l.foldRightAbsorbElement(Stream.empty(), 4, e -> acc -> Stream.cons(() -> e, () -> acc));
    assertEquals(List.of(1, 2, 3), res.toList());

    Function<Integer, Boolean> p = i -> i.equals(3);
    res = l.foldRightAbsorbElement(Stream.empty(), p, e -> acc -> Stream.cons(() -> e, () -> acc));
    assertEquals(List.of(1, 2), res.toList());
  }

  @Test
  void flattenResult() {
    Result<Integer> fail = Result.failure("Simulated failure");
    List<Result<Integer>> l = new ArrayList<>();
    l.add(Result.success(1));
    l.add(Result.success(2));
    l.add(fail);
    l.add(Result.success(3));
    l.add(Result.empty());
    assertTrue(Stream.flattenResult(Stream.of(l)).isFailure());

    l = new ArrayList<>();
    l.add(Result.success(1));
    l.add(Result.success(2));
    l.add(Result.empty());
    l.add(Result.success(3));
    l.add(Result.empty());
    Stream.flattenResult(Stream.of(l)).forEach(s -> assertEquals(List.of(1, 2, 3), s.toList()));
  }
}