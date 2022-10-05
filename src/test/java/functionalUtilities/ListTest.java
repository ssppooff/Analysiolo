package functionalUtilities;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ListTest {

  @Test
  void append() {
    List<Integer> l = List.empty();
    l = l.append(1);
    l = l.append(2);
    assertEquals(1, l.get(0));
    assertEquals(2, l.get(1));
  }

  @Test
  void flattenResult() {
    List<Result<String>> l = List.empty();
    l = l.append(Result.success("one"));
    l = l.append(Result.success("two"));
    assertEquals(Result.success("two"), l.get(1));
    l = l.append(Result.empty());
    assertNotEquals(Result.empty(), List.flattenResult(l));

    Result<List<String>> r = Result.success(List.empty());
    r = r.map(list -> list.append("one"));
    r = r.map(list -> list.append("two"));
    assertEquals(r, List.flattenResult(l));

    l = l.append(Result.failure("Exception"));
    assertTrue(List.flattenResult(l).isFailure());
  }
}