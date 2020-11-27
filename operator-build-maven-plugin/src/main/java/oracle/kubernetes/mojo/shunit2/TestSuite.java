// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojo.shunit2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.logging.Log;

class TestSuite {
  private static final Pattern TESTS_RUN_PATTERN  = Pattern.compile("Ran (\\d+) tests.");
  private static final Pattern TEST_FAILED_PATTERN  = Pattern.compile("ASSERT:(.*)");

  @SuppressWarnings("FieldMayBeFinal") // not final to allow unit test to change it
  private static Function<String, BashProcessBuilder> builderFunction = BashProcessBuilder::new;
  private final String scriptPath;
  private final Log log;
  private final Map<String, String> environmentVariables;

  private int numTestsRun;
  private int numFailures;
  private int numErrors;

  public TestSuite(String scriptPath, Log log, Map<String, String> environmentVariables) {
    this.scriptPath = scriptPath;
    this.log = log;
    this.environmentVariables = environmentVariables;
  }

  void run() {
    try {
      final Process process = createProcess();
      process.waitFor();
      processResults(process);
    } catch (IOException | InterruptedException e) {
      throw new TestSuiteFailedException("Unable to run test script " + scriptPath, e);
    }
  }

  int numTestsRun() {
    return numTestsRun;
  }

  int numFailures() {
    return numFailures;
  }

  public int getNumErrors() {
    return numErrors;
  }

  private Process createProcess() {
    final BashProcessBuilder builder = builderFunction.apply(scriptPath);
    for (Map.Entry<String, String> variable : environmentVariables.entrySet()) {
      builder.addEnvironmentVariable(variable.getKey(), variable.getValue());
    }
    return builder.build();
  }

  private void processResults(Process process) throws IOException {
    processOutputMessages(process);
    processErrorMessages(process);

    logSummaryLine();
  }

  private void logSummaryLine() {
    log.info(styleResults(AnsiUtils.text("Tests run: " + numTestsRun)).format()
              + String.format(", Failures: %d, Errors: %d - in %s", numFailures, numErrors, scriptPath));
  }

  private AnsiUtils.AnsiFormatter styleResults(AnsiUtils.AnsiFormatter text) {
    return wasSuccess() ? text.asGreen() : text.asBold().asRed();
  }

  private boolean wasSuccess() {
    return numErrors == 0 && numFailures == 0;
  }

  private void processOutputMessages(Process process) throws IOException {
    try (final InputStream inputStream = process.getInputStream()) {
      new BufferedReader(new InputStreamReader(inputStream)).lines()
            .map(AnsiUtils::withoutAnsiEscapeChars)
            .filter(this::isAllowedOutput)
            .forEach(this::processOutputLine);
    }
  }

  // Returns true if the line should be logged as output.
  private boolean isAllowedOutput(String outputLine) {
    final Matcher testsRunMatcher = TESTS_RUN_PATTERN.matcher(outputLine);
    if (testsRunMatcher.find()) {
      numTestsRun = Integer.parseInt(testsRunMatcher.group(1));
      return false;
    } else {
      return !outputLine.contains("FAILED ");
    }
  }

  private void processOutputLine(String line) {
    final Matcher testFailedMatcher = TEST_FAILED_PATTERN.matcher(line);
    if (testFailedMatcher.find()) {
      numFailures++;
      line = AnsiUtils.text("FAILED: ").asRed().asBold().format() + testFailedMatcher.group(1);
    }

    log.info(line);
  }

  private void processErrorMessages(Process process) throws IOException {
    try (final InputStream errorStream = process.getErrorStream()) {
      new BufferedReader(new InputStreamReader(errorStream)).lines()
            .map(AnsiUtils::withoutAnsiEscapeChars)
            .filter(this::allowedError)
            .forEach(this::logError);
    }
  }

  private void logError(String line) {
    numErrors++;
    log.error(line);
  }

  private boolean allowedError(String errorLine) {
    return !errorLine.contains("returned non-zero return code");
  }

}
