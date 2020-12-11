// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.TestAssertions;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.BuildApplication;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.kubernetes.client.util.Yaml.dump;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listServices;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.startOracleDB;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify cross domain transaction is successful")
@IntegrationTest
public class ItRjvmForward {

  private static final String WDT_MODEL_FILE_DOMAIN1 = "model-rjvm-crossdomaintransaction-domain1.yaml";
  private static final String WDT_MODEL_FILE_DOMAIN2 = "model-rjvm-crossdomaintransaction-domain2.yaml";

  private static final String WDT_MODEL_DOMAIN1_PROPS = "model-crossdomaintransaction-domain1.properties";
  private static final String WDT_MODEL_DOMAIN2_PROPS = "model-crossdomaintransaction-domain2.properties";
  private static final String WDT_IMAGE_NAME1 = "domain1-cdxaction-wdt-image";
  private static final String WDT_IMAGE_NAME2 = "domain2-cdxaction-wdt-image";
  private static final String PROPS_TEMP_DIR = RESULTS_ROOT + "/crossdomaintransactiontemp";
  private static final String WDT_MODEL_FILE_JMS = "model-cdt-jms.yaml";
  private static final String WDT_MODEL_FILE_JDBC = "model-cdt-jdbc.yaml";

  private static String opNamespace = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private String domainUid1 = "domain1";
  private String domainUid2 = "domain2";
  private final String domain1AdminServerPodName = domainUid1 + "-admin-server";
  private final String domain1ManagedServerPrefix = domainUid1 + "-managed-server";
  private final String domain2ManagedServerPrefix = domainUid2 + "-managed-server";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static final String ORACLEDBSUFFIX = ".svc.cluster.local:1521/devpdb.k8s";
  private static LoggingFacade logger = null;
  static String dbUrl;
  static int dbNodePort;
  int domain1ChannelPort;
  int domain2ChannelPort;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *     JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain1Namespace = namespaces.get(1);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domain2Namespace = namespaces.get(2);

    dbUrl = ORACLEDBURLPREFIX + domain2Namespace + ORACLEDBSUFFIX;
    createSecretForBaseImages(domain2Namespace);

    //Start oracleDB
    assertDoesNotThrow(() -> {
      startOracleDB(DB_IMAGE_TO_USE_IN_SPEC, 0, domain2Namespace);
      String.format("Failed to start Oracle DB");
    });
    dbNodePort = getDBNodePort(domain2Namespace, "oracledb");
    logger.info("DB Node Port = {0}", dbNodePort);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domain1Namespace, domain2Namespace);

  }

  private void updatePropertyFile() {
    //create a temporary directory to copy and update the properties file
    Path target = Paths.get(PROPS_TEMP_DIR);
    Path source1 = Paths.get(MODEL_DIR, WDT_MODEL_DOMAIN1_PROPS);
    Path source2 = Paths.get(MODEL_DIR, WDT_MODEL_DOMAIN2_PROPS);
    logger.info("Copy the properties file to the above area so that we can add namespace property");
    assertDoesNotThrow(() -> {
      Files.createDirectories(target);
      Files.copy(source1, target.resolve(source1.getFileName()), StandardCopyOption.REPLACE_EXISTING);
      Files.copy(source2, target.resolve(source2.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    });

    assertDoesNotThrow(() -> {
      addToPropertyFile(WDT_MODEL_DOMAIN1_PROPS, domain1ChannelPort);
      String.format("Failed to update %s ",
          WDT_MODEL_DOMAIN1_PROPS);
    });
    assertDoesNotThrow(() -> {
      addToPropertyFile(WDT_MODEL_DOMAIN2_PROPS, domain2ChannelPort);
      String.format("Failed to update %s",
          WDT_MODEL_DOMAIN2_PROPS);
    });

  }

  private void addToPropertyFile(String propFileName, int channelListenPort) throws IOException {
    FileInputStream in = new FileInputStream(PROPS_TEMP_DIR + "/" + propFileName);
    Properties props = new Properties();
    props.load(in);
    in.close();

    FileOutputStream out = new FileOutputStream(PROPS_TEMP_DIR + "/" + propFileName);
    props.setProperty("K8S_NODEPORT_HOST", K8S_NODEPORT_HOST);
    props.setProperty("DBPORT", Integer.toString(dbNodePort));
    props.setProperty("CHANNEL_LISTEN_PORT", Integer.toString(channelListenPort));
    props.store(out, null);
    out.close();
  }

  /*
   * This test verifies cross domain transaction is successful. domain in image using wdt is used
   * to create 2 domains in different namespaces. An app is deployed to both the domains and the servlet
   * is invoked which starts a transaction that spans both domains.
   * The application consists of a servlet front-end and a remote object that defines a method to register
   * a simple javax.transaction.Synchronization object. When the servlet is invoked, a global transaction
   * is started, and the specified list of server URLs is used to look up the remote object and register
   * a Synchronization object on each server.  Finally, the transaction is committed.  If the server
   * listen-addresses are resolvable between the transaction participants, then the transaction should
   * complete successfully
   */
  @Test
  @DisplayName("Check cross domain transaction works")
  public void testRjvmForward() {

    // Get a free port to assign a listen port to t3 channel
    domain1ChannelPort = getNextFreePort(30200, 30300);
    domain2ChannelPort = getNextFreePort(30700, 30800);

    // Update the model property file with the listenPort for the t3 channel
    updatePropertyFile();

    //build application archive
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "txforward"), null, null,
        "build", domain1Namespace);
    logger.info("distDir is {0}", distDir.toString());
    assertTrue(Paths.get(distDir.toString(),
        "txforward.ear").toFile().exists(),
        "Application archive is not available");
    String appSource = distDir.toString() + "/txforward.ear";
    logger.info("Application is in {0}", appSource);

    //build application archive
    distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "cdtservlet"), null, null,
        "build", domain1Namespace);
    logger.info("distDir is {0}", distDir.toString());
    assertTrue(Paths.get(distDir.toString(),
        "cdttxservlet.war").toFile().exists(),
        "Application archive is not available");
    String appSource1 = distDir.toString() + "/cdttxservlet.war";
    logger.info("Application is in {0}", appSource1);

    // create admin credential secret for domain1
    logger.info("Create admin credential secret for domain1");
    String domain1AdminSecretName = domainUid1 + "-weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        domain1AdminSecretName, domain1Namespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret %s failed for %s", domain1AdminSecretName, domainUid1));

    // create admin credential secret for domain2
    logger.info("Create admin credential secret for domain2");
    String domain2AdminSecretName = domainUid2 + "-weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        domain2AdminSecretName, domain2Namespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret %s failed for %s", domain2AdminSecretName, domainUid2));

    // build the model file list for domain1
    final List<String> modelListDomain1 = Arrays.asList(
        MODEL_DIR + "/" + WDT_MODEL_FILE_DOMAIN1,
        MODEL_DIR + "/" + WDT_MODEL_FILE_JMS);

    final List<String> appSrcDirList1 = Arrays.asList(appSource, appSource1);

    logger.info("Creating image with model file and verify");
    String domain1Image = createImageAndVerify(
        WDT_IMAGE_NAME1, modelListDomain1, appSrcDirList1, WDT_MODEL_DOMAIN1_PROPS, PROPS_TEMP_DIR, domainUid1);
    logger.info("Created {0} image", domain1Image);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(domain1Image);

    // build the model file list for domain2
    final List<String> modelListDomain2 = Arrays.asList(
        MODEL_DIR + "/" + WDT_MODEL_FILE_DOMAIN2,
        MODEL_DIR + "/" + WDT_MODEL_FILE_JDBC);

    final List<String> appSrcDirList2 = Collections.singletonList(appSource);

    logger.info("Creating image with model file and verify");
    String domain2Image = createImageAndVerify(
        WDT_IMAGE_NAME2, modelListDomain2, appSrcDirList2, WDT_MODEL_DOMAIN2_PROPS, PROPS_TEMP_DIR, domainUid2);
    logger.info("Created {0} image", domain2Image);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(domain2Image);

    //create domain1
    createDomain(domainUid1, domain1Namespace, domain1AdminSecretName, domain1Image, domain2ChannelPort, domainUid2);
    //create domain2
    createDomain(domainUid2, domain2Namespace, domain2AdminSecretName, domain2Image,domain1ChannelPort, domainUid1);

    // Create a Node Port Service to expose the channel Port which will be used by the RJVM proxy
    createNodePortService("domain1-cluster-cluster-1-ext", domainUid1, domain1Namespace, domain1ChannelPort);
    createNodePortService("domain2-cluster-cluster-1-ext", domainUid2, domain2Namespace, domain2ChannelPort);

    logger.info("Getting admin server external service node port");
    int adminServiceNodePort = assertDoesNotThrow(
        () -> getServiceNodePort(domain1Namespace, getExternalServicePodName(domain1AdminServerPodName), "default"),
        "Getting admin server node port failed");

    String curlRequest = String.format("curl -v --show-error --noproxy '*' "
            + "http://%s:%s/TxForward/TxForward?urls=t3://%s:7001,t3://%s1:8001,t3://%s1:8001",
        K8S_NODEPORT_HOST, adminServiceNodePort, domain1AdminServerPodName,
        domain1ManagedServerPrefix, domain2ManagedServerPrefix);

    ExecResult result = null;
    logger.info("curl command {0}", curlRequest);
    result = assertDoesNotThrow(
        () -> exec(curlRequest, true));
    if (result.exitValue() == 0) {
      logger.info("\n HTTP response is \n " + result.stdout());
      logger.info("curl command returned {0}", result.toString());
      assertTrue(result.stdout().contains("Status=Committed"), "crossDomainTransaction failed");
    }

  }

  /*
   * This test verifies cross domain transaction is successful and able to re-establish connection when
   * one domain is shutdown. Domain in image with wdt is used to create 2 domains in different namespaces.
   * A servlet is deployed to the admin server of domain1. This servlet starts a transaction with
   * TMAfterTLogBeforeCommitExit transaction property set. The servlet inserts data into oracleDB table and
   * sends a message to a JMS queue as part of a same transaction.The coordinator (server in domain2)
   * should exit before commit and the domain1 admin server should be able to re-establish connection
   * with domain2 and the transaction should commit.
   *
   */
  @Test
  @Disabled("temporarily")
  @DisplayName("Check cross domain transaction with TMAfterTLogBeforeCommitExit property commits")
  public void testCrossDomainTransactionWithFailInjection() {

    logger.info("Getting admin server external service node port");
    int domain1AdminServiceNodePort = assertDoesNotThrow(
        () -> getServiceNodePort(domain1Namespace, domain1AdminServerPodName + "-external", "default"),
        "Getting admin server node port failed");

    String curlRequest = String.format("curl -v --show-error --noproxy '*' "
            + "http://%s:%s/cdttxservlet/cdttxservlet?namespaces=%s,%s",
        K8S_NODEPORT_HOST, domain1AdminServiceNodePort, domain1Namespace, domain2Namespace);

    ExecResult result = null;
    logger.info("curl command {0}", curlRequest);
    result = assertDoesNotThrow(
        () -> exec(curlRequest, true));
    if (result.exitValue() == 0) {
      logger.info("\n HTTP response is \n " + result.stdout());
      logger.info("curl command returned {0}", result.toString());
      assertTrue(result.stdout().contains("Status=SUCCESS"),
          "crossDomainTransaction with TMAfterTLogBeforeCommitExit failed");
    }
  }

  private void createDomain(String domainUid, String domainNamespace, String adminSecretName,
                            String domainImage, int channelListenPort, String otherDomain) {
    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create the domain CR
    createDomainResource(domainUid, domainNamespace, adminSecretName, OCIR_SECRET_NAME,
        replicaCount, domainImage, channelListenPort, otherDomain);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));

    // check admin server pod exists
    logger.info("Check for admin server pod {0} existence in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods exist
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managed server pod {0} existence in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    logger.info("BR: list Servicesc {0} ", dump(Kubernetes.listServices(domainNamespace)));

    logger.info("BR: AdminServiceName {0} ", getExternalServicePodName(adminServerPodName));
    logger.info("Getting node port");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(domainNamespace,
        getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");
    logger.info("Admin service node port = {0} ", serviceNodePort);

    logger.info("Validating WebLogic admin server access by login to console");
    boolean loginSuccessful = assertDoesNotThrow(() -> {
      return TestAssertions.adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    }, "Access to admin server node port failed");
    assertTrue(loginSuccessful, "Console login validation failed");

  }

  private void createDomainResource(String domainUid, String domNamespace, String adminSecretName,
                                    String repoSecretName, int replicaCount, String domainImage, int channelListenPort,
                                    String otherDomain) {
    logger.info("Image to be used is {0}", domainImage);
    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("Image")
            .image(domainImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.transaction.EnableInstrumentedTM=true -Dweblogic.StdoutDebugEnabled=false"
                        + "-Dweblogic.debug.DebugJTAXA=true "
                        + "-Dweblogic.debug.DebugJTA2PC=true "
                        + "-Dweblogic.rjvm.debug81949=true "
                        + "-Dweblogic.rjvm.enableprotocolswitch=true "
                        + "-Dweblogic.debug.DebugConnection=true "
                        + "-Dweblogic.debug.DebugMessaging=true "
                        + "-Dweblogic.kernel.debug=true "
                        + "-Dweblogic.rjvm.domain.proxy." + otherDomain
                        + "=t3://bravicha-1" + ":" + channelListenPort))

                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName("cluster-1")
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS"))
                .introspectorJobActiveDeadlineSeconds(300L)));

    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
        + "for %s in namespace %s", domainUid, domNamespace));
  }

  private static Integer getDBNodePort(String namespace, String dbName) {
    logger.info(dump(Kubernetes.listServices(namespace)));
    List<V1Service> services = listServices(namespace).getItems();
    for (V1Service service : services) {
      if (service.getMetadata().getName().startsWith(dbName)) {
        return service.getSpec().getPorts().get(0).getNodePort();
      }
    }
    return -1;
  }

  private void createNodePortService(String serviceName, String domainName, String namespace, int port) {

    boolean service = false;
    try {
      Map<String, String> selector = new HashMap<>();
      selector.put("weblogic.domainUID", domainName);
      selector.put("weblogic.clusterName", "cluster-1");
      selector.put("weblogic.createdByOperator", "true");
      Map<String, String> labels = new HashMap<>();
      labels.put("weblogic.domainUID", domainName);
      service = TestActions.createService(new V1Service()
          .metadata(new V1ObjectMeta()
              .name(serviceName)
              .namespace(namespace)
              .labels(labels))
          .spec(new V1ServiceSpec()
              .type("NodePort")
              .externalTrafficPolicy("Cluster")
              .ports(Arrays.asList(new V1ServicePort()
                  .port(port)
                  .protocol("TCP")
                  .targetPort(new IntOrString(port))
                  .nodePort(port)))
              .selector(selector)));
    } catch (ApiException ex) {
      logger.log(Level.SEVERE, null, ex);
    }
    assertTrue(service, "NodePort Service creation failed");
  }

}
