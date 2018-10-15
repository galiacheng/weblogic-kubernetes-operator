// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static com.meterware.simplestub.Stub.createStrictStub;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.ProcessingConstants.SCRIPT_CONFIG_MAP;
import static oracle.kubernetes.operator.logging.MessageKeys.CM_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.CM_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.CM_REPLACED;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.work.AsyncCallTestSupport;
import oracle.kubernetes.operator.work.BodyMatcher;
import oracle.kubernetes.operator.work.CallTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConfigMapHelperTest {
  private static final String DOMAIN_NS = "namespace";
  private static final String OPERATOR_NS = "operator";
  static final String[] SCRIPT_NAMES = {
    "livenessProbe.sh",
    "readinessProbe.sh",
    "readState.sh",
    "start-server.py",
    "startServer.sh",
    "stop-server.py",
    "stopServer.sh",
    "introspectDomain.sh",
    "introspectDomain.py",
    "startNodeManager.sh",
    "traceUtils.py",
    "traceUtils.sh",
    "wlst.sh"
  };

  private static final String introspectResult =
      ">>>  /u01/introspect/domain1/userConfigNodeManager.secure\n"
          + "#WebLogic User Configuration File; 2\n"
          + "#Thu Oct 04 21:07:06 GMT 2018\n"
          + "weblogic.management.username={AES}fq11xKVoE927O07IUKhQ00d4A8QY598Dvd+KSnHNTEA\\=\n"
          + "weblogic.management.password={AES}LIxVY+aqI8KBkmlBTwkvAnQYQs4PS0FX3Ili4uLBggo\\=\n"
          + "\n"
          + ">>> EOF\n"
          + "\n"
          + "@[2018-10-04T21:07:06.864 UTC][introspectDomain.py:105] Printing file /u01/introspect/domain1/userKeyNodeManager.secure\n"
          + "\n"
          + ">>>  /u01/introspect/domain1/userKeyNodeManager.secure\n"
          + "BPtNabkCIIc2IJp/TzZ9TzbUHG7O3xboteDytDO3XnwNhumdSpaUGKmcbusdmbOUY+4J2kteu6xJPWTzmNRAtg==\n"
          + "\n"
          + ">>> EOF\n"
          + "\n"
          + "@[2018-10-04T21:07:06.867 UTC][introspectDomain.py:105] Printing file /u01/introspect/domain1/topology.yaml\n"
          + "\n"
          + ">>>  /u01/introspect/domain1/topology.yaml\n"
          + "domainValid: true\n"
          + "domain:\n"
          + "  name: \"base_domain\"\n"
          + "  adminServerName: \"admin-server\"\n"
          + "  configuredClusters:\n"
          + "    \"mycluster\":\n"
          + "      port: 8001\n"
          + "      servers:\n"
          + "        \"managed-server1\": {}\n"
          + "        \"managed-server2\": {}\n"
          + "  dynamicClusters: {}\n"
          + "  servers:\n"
          + "    \"admin-server\":\n"
          + "      port: 7001\n"
          + "\n"
          + ">>> EOF";

  private static final String[] PARTIAL_SCRIPT_NAMES = {"livenessProbe.sh", "additional.sh"};
  private static final String[] COMBINED_SCRIPT_NAMES = combine(SCRIPT_NAMES, PARTIAL_SCRIPT_NAMES);

  private final V1ConfigMap defaultConfigMap = defineDefaultConfigMap();
  private RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private List<LogRecord> logRecords = new ArrayList<>();

  private V1ConfigMap defineDefaultConfigMap() {
    return defineConfigMap(SCRIPT_NAMES);
  }

  private V1ConfigMap defineConfigMap(String... scriptNames) {
    return new V1ConfigMap()
        .apiVersion("v1")
        .kind("ConfigMap")
        .metadata(createMetadata())
        .data(nameOnlyScriptMap(scriptNames));
  }

  @SuppressWarnings("SameParameterValue")
  private static String[] combine(String[] first, String[] second) {
    return Stream.of(first, second).flatMap(Stream::of).distinct().toArray(String[]::new);
  }

  private static Map<String, String> nameOnlyScriptMap(String... scriptNames) {
    return Stream.of(scriptNames).collect(Collectors.toMap(s -> s, s -> ""));
  }

  private V1ObjectMeta createMetadata() {
    return new V1ObjectMeta()
        .name(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME)
        .namespace(DOMAIN_NS)
        .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V1)
        .putLabelsItem(LabelConstants.OPERATORNAME_LABEL, OPERATOR_NS)
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, CM_CREATED, CM_EXISTS, CM_REPLACED)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.installRequestStepFactory());
    mementos.add(TestComparator.install());
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  @Test
  public void whenUnableToReadConfigMap_reportFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadConfigMap().failingWithStatus(401);

    Step scriptConfigMapStep = ConfigMapHelper.createScriptConfigMapStep(OPERATOR_NS, DOMAIN_NS);
    testSupport.runSteps(scriptConfigMapStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenNoConfigMap_createIt() {
    expectReadConfigMap().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectSuccessfulCreateConfigMap(defaultConfigMap);

    testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(OPERATOR_NS, DOMAIN_NS));

    assertThat(logRecords, containsInfo(CM_CREATED));
  }

  @Test
  public void whenNoConfigMap_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadConfigMap().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectCreateConfigMap(defaultConfigMap).failingWithStatus(401);

    Step scriptConfigMapStep = ConfigMapHelper.createScriptConfigMapStep(OPERATOR_NS, DOMAIN_NS);
    testSupport.runSteps(scriptConfigMapStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptConfigMapStep));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void whenMatchingConfigMapExists_addToPacket() {
    expectReadConfigMap().returning(defaultConfigMap);

    Packet packet =
        testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(OPERATOR_NS, DOMAIN_NS));

    assertThat(packet, hasEntry(SCRIPT_CONFIG_MAP, defaultConfigMap));
    assertThat(logRecords, containsFine(CM_EXISTS));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void whenExistingConfigMapIsMissingData_replaceIt() {
    expectReadConfigMap().returning(defineConfigMap(PARTIAL_SCRIPT_NAMES));
    expectSuccessfulReplaceConfigMap(defineConfigMap(COMBINED_SCRIPT_NAMES));

    testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(OPERATOR_NS, DOMAIN_NS));

    assertThat(logRecords, containsInfo(CM_REPLACED));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void whenReplaceFails_scheduleRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadConfigMap().returning(defineConfigMap(PARTIAL_SCRIPT_NAMES));
    expectReplaceConfigMap(defineConfigMap(COMBINED_SCRIPT_NAMES)).failingWithStatus(401);

    Step scriptConfigMapStep = ConfigMapHelper.createScriptConfigMapStep(OPERATOR_NS, DOMAIN_NS);
    testSupport.runSteps(scriptConfigMapStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptConfigMapStep));
  }

  // @Test
  public void parseIntrospectorResult() {
    Map<String, String> result = ConfigMapHelper.parseIntrospectorResult(introspectResult);
    System.out.println("ConfigMapHelperTest.parseIntrospectorResult: " + result);
    assertEquals(3, result.size());
    assertTrue(result.containsKey("userConfigNodeManager.secure"));
    assertTrue(result.containsKey("userKeyNodeManager.secure"));
    assertTrue(result.containsKey("topology.yaml"));
  }

  // @Test
  public void readSingleFile() throws IOException {
    Map<String, String> map = new HashMap<>();
    String text =
        ">>>  /u01/introspect/domain1/userConfigNodeManager.secure\n"
            + "#WebLogic User Configuration File; 2\n"
            + "#Thu Oct 04 21:07:06 GMT 2018\n"
            + "weblogic.management.username={AES}fq11xKVoE927O07IUKhQ00d4A8QY598Dvd+KSnHNTEA\\=\n"
            + "weblogic.management.password={AES}LIxVY+aqI8KBkmlBTwkvAnQYQs4PS0FX3Ili4uLBggo\\=\n"
            + "\n"
            + ">>> EOF\n"
            + "\n"
            + "@[2018-10-04T21:07:06.864 UTC][introspectDomain.py:105] Printing file /u01/introspect/domain1/userKeyNodeManager.secure\n"
            + "\n";

    BufferedReader reader = new BufferedReader(new StringReader(text));
    String line = reader.readLine();
    System.out.println("ConfigMapHelperTest.readSingleFile line: " + line);
    assertTrue(line.startsWith(">>>"));
    String fileName = ConfigMapHelper.extractFilename(line);
    System.out.println("ConfigMapHelperTest.readSingleFile fileName: " + fileName);
    ConfigMapHelper.readFile(reader, fileName, map);
    System.out.println("ConfigMapHelperTest.readSingleFile map: " + map);
    assertEquals(1, map.size());
    assertTrue(map.containsKey("userConfigNodeManager.secure"));
  }

  private CallTestSupport.CannedResponse expectReadConfigMap() {
    return testSupport
        .createCannedResponse("readConfigMap")
        .withNamespace(DOMAIN_NS)
        .withName(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME);
  }

  @SuppressWarnings("unchecked")
  private void expectSuccessfulCreateConfigMap(V1ConfigMap expectedConfig) {
    expectCreateConfigMap(expectedConfig).returning(expectedConfig);
  }

  private CallTestSupport.CannedResponse expectCreateConfigMap(V1ConfigMap expectedConfig) {
    return testSupport
        .createCannedResponse("createConfigMap")
        .withNamespace(DOMAIN_NS)
        .withBody(new V1ConfigMapMatcher(expectedConfig));
  }

  @SuppressWarnings("unchecked")
  private void expectSuccessfulReplaceConfigMap(V1ConfigMap expectedConfig) {
    expectReplaceConfigMap(expectedConfig).returning(expectedConfig);
  }

  private CallTestSupport.CannedResponse expectReplaceConfigMap(V1ConfigMap expectedConfig) {
    return testSupport
        .createCannedResponse("replaceConfigMap")
        .withNamespace(DOMAIN_NS)
        .withName(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME)
        .withBody(new V1ConfigMapMatcher(expectedConfig));
  }

  class V1ConfigMapMatcher implements BodyMatcher {
    private V1ConfigMap expected;

    V1ConfigMapMatcher(V1ConfigMap expected) {
      this.expected = expected;
    }

    @Override
    public boolean matches(Object actualBody) {
      return actualBody instanceof V1ConfigMap && matches((V1ConfigMap) actualBody);
    }

    private boolean matches(V1ConfigMap actualBody) {
      return hasExpectedKeys(actualBody) && adjustedBody(actualBody).equals(actualBody);
    }

    private boolean hasExpectedKeys(V1ConfigMap actualBody) {
      return expected.getData().keySet().equals(actualBody.getData().keySet());
    }

    private V1ConfigMap adjustedBody(V1ConfigMap actualBody) {
      return new V1ConfigMap()
          .apiVersion(expected.getApiVersion())
          .kind(expected.getKind())
          .metadata(expected.getMetadata())
          .data(actualBody.getData());
    }
  }

  // An implementation of the comparator that tests only the keys in the maps
  static class TestComparator implements ConfigMapHelper.ConfigMapComparator {
    static Memento install() throws NoSuchFieldException {
      return StaticStubSupport.install(ConfigMapHelper.class, "COMPARATOR", new TestComparator());
    }

    @Override
    public boolean containsAll(V1ConfigMap actual, V1ConfigMap expected) {
      return actual.getData().keySet().containsAll(expected.getData().keySet());
    }
  }
}
