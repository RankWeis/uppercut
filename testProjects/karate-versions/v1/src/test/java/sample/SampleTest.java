package sample;

import com.intuit.karate.junit5.Karate;

/**
 * Baseline: the idiomatic karate-junit5 (v1) entry point. Run with:
 * <pre>./gradlew -p testProjects/karate-versions :v1:test</pre>
 */
class SampleTest {

  @Karate.Test
  Karate testSample() {
    return Karate.run("classpath:sample");
  }
}
