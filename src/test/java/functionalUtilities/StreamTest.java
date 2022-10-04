package functionalUtilities;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StreamTest {

  @Test
  void of() {
    List<String> l = List.of("one", "two", "three");
    Stream<String> s = Stream.of(l);
    Assertions.assertEquals("one", s.head());
    Assertions.assertEquals("two", s.tail().head());
    Assertions.assertEquals("three", s.tail().tail().head());
  }
}