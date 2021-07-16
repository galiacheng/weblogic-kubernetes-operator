// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.List;
import java.util.Map;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.utils.ItMiiSampleHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;

/**
 * Tests to verify MII sample.
 */
@DisplayName("Test model in image sample with WLS domain")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
public class ItMiiSampleWlsMain {

  private static String domainType = "WLS";
  private static String imageType = "MAIN";

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *        JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void init(@Namespaces(3) List<String> namespaces) {
    ItMiiSampleHelper.setDomainType(domainType);
    ItMiiSampleHelper.setImageType(imageType);
    ItMiiSampleHelper.initAll(namespaces);
  }

  /**
   * Generate sample and verify that this matches the source
   * checked into the mii sample git location.
   */
  @Test
  @Order(1)
  @DisabledIfEnvironmentVariable(named = "SKIP_CHECK_SAMPLE", matches = "true")
  @DisplayName("Test to verify MII Sample source")
  public void testCheckMiiSampleSource() {
    Map<String, String> envMap = ItMiiSampleHelper.getEnvMap();
    envMap.remove("BASE_IMAGE_NAME");
    ItMiiSampleHelper.execTestScriptAndAssertSuccess("-check-sample",
        "Sample source doesn't match with the generated source");
    envMap.put("BASE_IMAGE_NAME", WEBLOGIC_IMAGE_NAME);
    ItMiiSampleHelper.setEnvMap(envMap);
  }

  /**
   * Test to verify MII sample WLS initial use case. 
   * Builds image required for the initial use case, creates secrets, and
   * creates domain resource.
   * Verifies all WebLogic Server pods are ready, are at the expected 
   * restartVersion, and have the expected image.
   * Verifies the sample application is running
   * (response includes "Hello World!").
   */
  @Test
  @Order(2)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS initial use case")
  public void testWlsInitialUseCase() {
    ItMiiSampleHelper.callInitialUseCase();
  }

  /**
   * Test to verify WLS update1 use case. 
   * Adds a data source to initial domain via a configmap and updates the 
   * domain resource restartVersion.
   * Verifies all WebLogic Server pods roll to ready, roll to the expected 
   * restartVersion, and have the expected image.
   * Verifies the sample application is running
   * and detects the new datasource (response includes
   * "mynewdatasource").
   */
  @Test
  @Order(3)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update1 use case")
  public void testWlsUpdate1UseCase() {
    ItMiiSampleHelper.callUpdate1UseCase();
  }

  /**
   * Test to verify WLS update2 use case.
   * Deploys a second domain 'domain2' with a different domain UID,
   * different secrets, and different datasource config map,
   * but that is otherwise the same as the update1 domain.
   * Verifies all WebLogic Server pods are ready, have the expected
   * restartVersion, and have the expected image.
   * For each domain, verifies the sample application is running
   * (response includes "domain1" or "domain2" depending on domain).
   */
  @Test
  @Order(4)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update2 use case")
  public void testWlsUpdate2UseCase() {
    ItMiiSampleHelper.callUpdate2UseCase();
  }

  /**
   * Test to verify update3 use case.
   * Deploys an updated WebLogic application to the running
   * domain from update1 using an updated Docker image,
   * and updates the domain resource restartVersion.
   * Verifies all WebLogic Server pods roll to ready, roll to the expected 
   * restartVersion, and have the expected image.
   * Verifies the sample application is running
   * and is at the new version (response includes "v2").
   */
  @Test
  @Order(5)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update3 use case")
  public void testWlsUpdate3UseCase() {
    ItMiiSampleHelper.callUpdate3UseCase();
  }

  /**
   * Test to verify WLS update4 use case.
   * Update Work Manager Min and Max Threads Constraints via a configmap and updates the
   * domain resource introspectVersion.
   * Verifies the sample application is running
   * and detects the updated configured count for the Min and Max Threads Constraints.
   */
  @Test
  @Order(6)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update4 use case")
  public void testWlsUpdate4UseCase() {
    ItMiiSampleHelper.callUpdate4UseCase();
  }

  /**
   * Uninstall traefik.
   */
  @AfterAll
  public void tearDown() {
    // uninstall traefik
    ItMiiSampleHelper.tearDownAll();
  }
}