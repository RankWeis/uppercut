package sample;

/**
 * Java called from a feature through {@code Java.type}, so a JDWP breakpoint has somewhere to land
 * that is the user's own code rather than karate-core's. Used by {@code JdwpClashProbe}: this is the
 * one thing an opt-in JVM debugger on a Karate run would be for.
 *
 * <p><b>Named apart from the v2 module's {@code sample.Helper} on purpose.</b> A java breakpoint
 * binds by class name and line - the JVM only ever reports {@code sample.Helper:11} - so two modules
 * defining the same fully-qualified name at the same line leave the IDE to guess which source file
 * to show, and it showed v1's while a v2 feature was running. Debugging a v2 run then looks like it
 * stopped in the wrong module. Keep these two names distinct.
 */
public class HelperV1 {

  public static int compute(int seed) {
    int doubled = seed * 2;
    System.out.println("[HELPER-V1] compute(" + seed + ") on " + Thread.currentThread());
    return doubled + 1;
  }
}
