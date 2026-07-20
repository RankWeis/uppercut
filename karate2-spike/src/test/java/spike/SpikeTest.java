package spike;

import io.karatelabs.junit6.Karate;

/**
 * Baseline: the idiomatic karate-junit6 entry point. Run with:
 * <pre>../gradlew -p karate2-spike test</pre>
 */
class SpikeTest {

  @Karate.Test
  Karate testSpike() {
    return Karate.run("classpath:spike");
  }
}
