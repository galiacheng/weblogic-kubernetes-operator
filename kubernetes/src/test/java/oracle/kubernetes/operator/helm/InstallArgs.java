// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

/**
 * The arguments needed to install a helm chart.
 */
@SuppressWarnings({"SameParameterValue"})
public class InstallArgs {
  private final String chartName;
  private final String releaseName;
  private final String namespace;
  private final Map<String, Object> valueOverrides;

  InstallArgs(
      String chartName, String releaseName, String namespace, Map<String, Object> valueOverrides) {
    this.chartName = chartName;
    this.releaseName = releaseName;
    this.namespace = namespace;
    this.valueOverrides = valueOverrides;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof InstallArgs)) {
      return false;
    }
    InstallArgs rhs = ((InstallArgs) other);
    if (!StringUtils.equals(this.chartName, rhs.chartName)) {
      return false;
    }
    if (!StringUtils.equals(this.releaseName, rhs.releaseName)) {
      return false;
    }
    if (!StringUtils.equals(this.namespace, rhs.namespace)) {
      return false;
    }
    return Objects.equals(this.valueOverrides, rhs.valueOverrides);
  }

  @Override
  public int hashCode() {
    return Objects.hash(chartName, releaseName, namespace, valueOverrides);
  }

  String getChartName() {
    return this.chartName;
  }

  String getReleaseName() {
    return this.releaseName;
  }

  String getNamespace() {
    return this.namespace;
  }

  Map<String, Object> getValueOverrides() {
    return this.valueOverrides;
  }
}
