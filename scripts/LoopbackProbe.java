import java.nio.channels.Selector;
public class LoopbackProbe {
  public static void main(String[] args) throws Exception {
    try (Selector selector = Selector.open()) {
      System.out.println("SELECTOR_OK " + selector.getClass().getName());
    }
  }
}
