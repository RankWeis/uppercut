package sample;

/**
 * Java called from a feature through {@code Java.type}, so a JDWP breakpoint has somewhere to land
 * that is the user's own code rather than karate-core's. Used by {@code JdwpClashProbe}: this is the
 * one thing an opt-in JVM debugger on a Karate run would be for.
 */
public class Helper {

  public static int compute(int seed) {
    int doubled = seed * 2;
    System.out.println("[HELPER] compute(" + seed + ") on " + Thread.currentThread());
    return doubled + 1;
  }
}
