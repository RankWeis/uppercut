package sample;

import io.karatelabs.junit6.Karate;

/**
 * Baseline: the idiomatic karate-junit6 entry point. Run with:
 * <pre>../../gradlew -p testProjects/karate-versions :v2: test</pre>
 */
class SampleTest {

  @Karate.Test
  Karate testSample() {
    return Karate.run("classpath:sample");
  }
}
