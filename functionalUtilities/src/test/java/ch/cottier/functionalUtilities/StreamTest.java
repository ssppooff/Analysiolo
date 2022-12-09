package ch.cottier.functionalUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cottier.functionalUtilities.List;
import ch.cottier.functionalUtilities.Result;
import ch.cottier.functionalUtilities.Stream;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StreamTest {

  @Test
  void of() {
    Stream<String> s = Stream.of(List.of("one", "two", "three"));
    Stream<String> exp = Stream.of("one", "two", "three");
    assertEquals(exp, s);
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
    List<Result<Integer>> l = List.of(
        Result.success(1),
        Result.success(2),
        Result.failure("Simulated failure"),
        Result.empty(),
        Result.success(3));
    assertTrue(Stream.flattenResult(Stream.of(l)).isFailure());

    l = List.of(
        Result.success(1),
        Result.success(2),
        Result.empty(),
        Result.success(3));
    Stream.flattenResult(Stream.of(l))
        .forEachOrFail(s -> assertEquals(List.of(1, 2, 3), s.toList()))
        .forEach(Assertions::fail);
  }

  @Test
  void toList() {
    var s = Stream.from(2).take(4);
    assertEquals(List.of(2, 3, 4, 5), s.toList());

    var r = s.filter(i -> i % 2 == 0);
    assertEquals(List.of(2, 4), r.toList());
  }

  @Test
  void takeFrom() {
    assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
        Stream.from(1).take(10).toList());
    assertEquals(List.of(1, 2, 3),
        Stream.from(1).take(3).take(10).toList());
  }

  @Test
  void testEquals() {
    List<Integer> l1 = List.of(1, 2, 3);
    List<Integer> l2 = List.of(0, 1, 2, 3);
    assertNotEquals(l1, l2);
    assertEquals(l1, l2.tail());

    List<String> ls = List.of("one", "two", "three");
    assertNotEquals(l1, ls);
  }
}