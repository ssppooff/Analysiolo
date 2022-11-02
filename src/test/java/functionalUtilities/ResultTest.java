package functionalUtilities;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import org.junit.jupiter.api.Test;

class ResultTest {

  @Test
  void mapEmptyCollection() {
    Result<List<Integer>> l = Result.success(List.of(1, 2, 3));
    l = l.mapEmptyCollection();
    assertFalse(l.isEmpty());

    l = Result.success(List.list());
    l = l.mapEmptyCollection();
    assertTrue(l.isEmpty());
  }
}