// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.List;

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

/**
 * Tests to verify MII sample with JRF domain.
 */
@DisplayName("Test model in image sample with JRF domain")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
public class ItMiiSampleFmwMain {

  private static String domainType = "JRF";
  private static String imageType = "MAIN";

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *        JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void init(@Namespaces(4) List<String> namespaces) {
    ItMiiSampleHelper.setDomainType(domainType);
    ItMiiSampleHelper.setImageType(imageType);
    ItMiiSampleHelper.initAll(namespaces);
  }

  /**
   * Test to verify MII sample JRF initial use case.
   * Deploys a database and initializes it for RCU, 
   * uses an FMW infra base image instead of WLS 
   * base image, and uses a WDT model that's 
   * specialized for JRF, but is otherwise similar to
   * the WLS initial use case.
   * @see ItMiiSampleWlsMain#testWlsInitialUseCase for more...
   */
  @Test
  @Order(1)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF initial use case")
  public void testFmwInitialUseCase() {
    ItMiiSampleHelper.callInitialUseCase();
  }


  /**
   * Test to verify JRF update1 use case.
   * @see ItMiiSampleWlsMain#testWlsUpdate1UseCase for more...
   */
  @Test
  @Order(2)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF update1 use case")
  public void testFmwUpdate1UseCase() {
    ItMiiSampleHelper.callUpdate1UseCase();
  }

  /**
   * Test to verify JRF update2 use case.
   * @see ItMiiSampleWlsMain#testWlsUpdate2UseCase for more...
   */
  @Test
  @Order(3)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF update2 use case")
  public void testFmwUpdate2UseCase() {
    ItMiiSampleHelper.callUpdate2UseCase();
  }

  /**
   * Test to verify JRF update3 use case.
   * @see ItMiiSampleWlsMain#testWlsUpdate3UseCase for more...
   */
  @Test
  @Order(4)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF update3 use case")
  public void testFmwUpdate3UseCase() {
    ItMiiSampleHelper.callUpdate3UseCase();
  }

  /**
   * Test to verify JRF update4 use case.
   * Update Work Manager Min and Max Threads Constraints via a configmap and updates the
   * domain resource introspectVersion.
   * Verifies the sample application is running
   * and detects the updated configured count for the Min and Max Threads Constraints.
   */
  @Test
  @Order(5)
  @DisabledIfEnvironmentVariable(named = "SKIP_JRF_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample JRF update4 use case")
  public void testFmwUpdate4UseCase() {
    ItMiiSampleHelper.callUpdate4UseCase();
  }

  /**
   * Delete DB deployment and Uninstall traefik.
   */
  @AfterAll
  public void tearDownAll() {
    // db cleanup or deletion and uninstall traefik
    ItMiiSampleHelper.tearDownAll();
  }
}