package sample;

/**
 * Java called from a feature through {@code Java.type}, so a JDWP breakpoint has somewhere to land
 * that is the user's own code rather than karate-core's. Used by {@code JdwpClashProbe}: this is the
 * one thing an opt-in JVM debugger on a Karate run would be for.
 *
 * <p><b>Deliberately the same fully-qualified name as the v2 module's copy, at the same line.</b> A
 * java breakpoint binds by class name and line - the JVM reports {@code sample.Helper:11} and
 * nothing about which module the source is in - so this pair is what proves the debug session is
 * scoped to the run's module: without that, a v2 run stops showing <i>this</i> file. The print
 * prefix differs so a console says which one actually ran. Do not "fix" this by renaming.
 */
public class Helper {

  public static int compute(int seed) {
    int doubled = seed * 2;
    System.out.println("[HELPER-V1] compute(" + seed + ") on " + Thread.currentThread());
    return doubled + 1;
  }
}
