package sample;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;

/**
 * Compiled (non-reflective) twin of what Uppercut's KarateV2TestRunner does reflectively: drive
 * io.karatelabs.core.Runner with a RunListener and dump every event + its toJson() payload.
 *
 * <p>Run with: <pre>../../gradlew -p testProjects/karate-v2 eventProbe</pre></p>
 *
 * <p>Things to verify in the output:</p>
 * <ul>
 *   <li>Which fields toJson() carries per event type (name, path, line numbers?)</li>
 *   <li>How the called.feature invocation appears (nested FEATURE_ENTER? caller info?)</li>
 *   <li>Whether returning true from onEvent suppresses Karate's own console output or not</li>
 *   <li>Whether SCENARIO_EXIT carries failure details when a step fails</li>
 * </ul>
 */
public class EventProbe {

  public static void main(String[] args) {
    String path = args.length > 0 ? args[0] : "classpath:sample";
    SuiteResult result = Runner.builder()
      .path(path)
      .workingDir(".")
      .listener(event -> {
        System.out.println("[PROBE] " + event.getType() + " " + event.toJson());
        return true;
      })
      .outputHtmlReport(false)
      .parallel(1);
    System.out.println("[PROBE] suite result: " + result);
  }
}
