package ch.cottier.functionalUtilities;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cottier.functionalUtilities.List;
import ch.cottier.functionalUtilities.Result;
import ch.cottier.functionalUtilities.Stream;
import ch.cottier.functionalUtilities.Tuple;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ListTest {

  @Test
  void prepend() {
    List<Integer> l = List.list();
    l = l.prepend(1);
    assertEquals(1, l.head());
    l = l.prepend(2);
    assertEquals(2, l.head());
  }

  @Test
  void flattenResult() {
    List<Result<String>> l = List.of(Result.success("one"), Result.success("two"), Result.empty());
    Result<List<String>> exp = Result.success(List.of("one", "two"));
    assertEquals(exp, List.flattenResult(l));

    l = List.concat(l, List.of(Result.failure("Exception")));
    assertTrue(List.flattenResult(l).isFailure());
  }

  @Test
  void head() {
    assertEquals(1, List.of(1, 2, 3, 4).head());
  }

  @Test
  void tail() {
    assertEquals(List.of(2,3,4), List.of(1, 2, 3, 4).tail());
  }

  @Test
  void fpStream() {
    List<Integer> l = List.of(1, 2, 3);
    Stream<Integer> s = l.fpStream();
    Stream<Integer> exp = Stream.from(1).take(3);
    assertEquals(exp, s);
  }

  @Test
  void reverse() {
    List<Integer> l = List.of(1, 2, 3);
    assertEquals(List.of(3, 2, 1), l.reverse());
  }

  @Test
  void testToString() {
    List<Integer> l = List.list();
    assertEquals("(NIL)", l.toString());

    l = l.prepend(3).prepend(2).prepend(1);
    assertEquals("(1, 2, 3, NIL)", l.toString());
  }

  @Test
  void of() {
    List<Integer> expected = List.list();
    expected = expected.prepend(3).prepend(2).prepend(1);
    assertEquals(expected, List.of(1, 2, 3));

    assertEquals(expected, List.of(Arrays.asList(1, 2, 3)));
  }

  @Test
  void testGetAndGetAt() {
    List<Integer> l = List.of(1, 2, 3);
    l.getAt(0).forEachOrFail(node -> assertEquals(1, node.head())).forEach(Assertions::fail);
    l.getAt(1).forEachOrFail(node -> assertEquals(2, node.head())).forEach(Assertions::fail);
    l.getAt(2).forEachOrFail(node -> assertEquals(3, node.head())).forEach(Assertions::fail);

    assertEquals(1, l.get(0));
    assertEquals(2, l.get(1));
    assertEquals(3, l.get(2));
  }

  @Test
  void concat() {
    List<Integer> l1 = List.of(1, 2, 3, 45);
    List<Integer> l2 = List.of(6, 7, 8,9, 100);
    List<Integer> exp = List.of(1, 2, 3, 45, 6, 7, 8, 9, 100);
    assertEquals(exp, List.concat(l1, l2));
  }

  @Test
  void zip() {
    List<Integer> i = List.of(1, 2, 3, 4);
    List<String> s = List.of("one", "two", "three");
    List<Tuple<Integer, String>> exp = List.of(
        new Tuple<>(1, "one"),
        new Tuple<>(2, "two"),
        new Tuple<>(3, "three"));
    assertEquals(exp, List.zip(i, s));
  }

  @Test
  void size() {
    assertEquals(3, List.of(1, 2, 3).size());
  }

  @Test
  void filter() {
    List<Integer> l = Stream.from(1).take(10).toList();
    List<Integer> exp = List.list(2, 4, 6, 8, 10);
    assertEquals(exp, l.filter(i -> i % 2 == 0));
  }

  @Test
  void testReverse() {
    List<Integer> l = Stream.from(1).take(3).toList();
    List<Integer> exp = List.list(3, 2, 1);
    assertEquals(exp, l.reverse());
  }

  @Test
  void testSort() {
    List<Integer> l1 = List.of(3, 4, 2, 1, 2);
    List<Integer> l2 = List.of(1, 1, 2, 1, 1);
    List<Tuple<Integer, Integer>> lT = List.zip(l1, l2);

    List<Integer> exp1 = List.of(1, 2, 2, 3, 4);
    List<Integer> exp2 = List.of(1, 2, 1, 1, 1);
    List<Tuple<Integer, Integer>> lTexp = List.zip(exp1, exp2);

    assertEquals(exp1, l1.sortFP(Integer::compareTo));
    assertEquals(lTexp, lT.sortFP(Comparator.comparing(Tuple::_1)));
  }

  @Test
  void testLast() {
    List<Integer> l = List.of(1, 2, 3, 4);
    assertEquals(Result.success(4), l.last());
    assertEquals(Result.empty(), List.list().last());
  }

  @Test
  void toArrayTest() {
    List<Integer> l = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
    Integer[] expArr = new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
    assertArrayEquals(expArr, l.toArray());

    List<String> lstr = List.of("one", "two", "three", "four");
    String[] expStr = new String[]{"one", "two", "three", "four"};
    assertArrayEquals(expStr, lstr.toArray(new String[0]));
  }

  @Test
  void toArrayPaddedTest() {
    List<Integer> l = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
    Integer[] expArr = new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1};
    assertArrayEquals(expArr, l.toArrayPadded(new Integer[0], 12, -1));

    List<String> lstr = List.of("one", "two", "three", "four");
    String[] expStr = new String[]{"one", "two", "three", "four", "x", "x", "x"};
    assertArrayEquals(expStr, lstr.toArrayPadded(7, "x"));
  }

  @Test
  void insertTest() {
    String testStr = "inserted";
    List<String> l = List.of("one", "two", "three", "four");
    List<String> expL = List.of("one", "two", testStr, "three", "four");
    assertEquals(expL, l.insert(2, testStr));

    expL = List.of( "one", testStr, "two", "three", "four");
    assertEquals(expL, l.insert(1, testStr));

    expL = List.of(testStr, "one", "two", "three", "four");
    assertEquals(expL, l.insert(0, testStr));
  }
}