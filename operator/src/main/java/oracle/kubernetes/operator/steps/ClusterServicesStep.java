// Copyright (c) 2017, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.PodDisruptionBudgetHelper;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ClusterServicesStep extends Step {

  public ClusterServicesStep(Step next) {
    super(next);
  }

  @Override
  public @Nonnull Result apply(Packet packet) {
    Collection<Fiber.StepAndPacket> startDetails = new ArrayList<>();

    // Add cluster services
    WlsDomainConfig config = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    if (config != null) {
      for (Map.Entry<String, WlsClusterConfig> entry : config.getClusterConfigs().entrySet()) {
        Packet p = packet.copy();
        WlsClusterConfig clusterConfig = entry.getValue();
        p.put(ProcessingConstants.CLUSTER_NAME, clusterConfig.getClusterName());

        startDetails.add(new Fiber.StepAndPacket(PodDisruptionBudgetHelper
                .createPodDisruptionBudgetForClusterStep(ServiceHelper.createForClusterStep(null)), p));
      }
    }

    if (startDetails.isEmpty()) {
      return doNext(packet);
    }
    return doForkJoin(getNext(), packet, startDetails);
  }
}
