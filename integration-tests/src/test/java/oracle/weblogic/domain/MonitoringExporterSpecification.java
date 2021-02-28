// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import io.swagger.annotations.ApiModelProperty;
import oracle.weblogic.domain.MonitoringExporterConfiguration;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.yaml.snakeyaml.Yaml;


public class MonitoringExporterSpecification {

  public static final String EXPORTER_PORT_NAME = "exporter";
  public static final String DEFAULT_EXPORTER_IMAGE = "oracle/weblogic-monitoring-exporter:2.0";
  //@PreserveUnknown
  @ApiModelProperty("The configuration for the WebLogic Monitoring Exporter sidecar. If specified, the operator will "
      + "deploy a sidecar alongside each server instance. See https://github.com/oracle/weblogic-monitoring-exporter")
  private Map<String,Object> configuration;

  /**
   * The Monitoring Exporter sidecar image.
   */
  @ApiModelProperty(
      "The WebLogic Monitoring Exporter sidecar image name. Defaults to "
          + "oracle/weblogic-monitoring-exporter:2.0")
  private String image;

  @ApiModelProperty(
      "The image pull policy for the WebLogic Monitoring Exporter sidecar image. "
          + "Legal values are Always, Never, and IfNotPresent. "
          + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  private String imagePullPolicy;

  /**
   * Computes the REST port for the specified server. This port will be used by the
   * metrics exporter to query runtime data.
   * @param serverConfig the configuration for a server
   */
  /*
  public static int getRestPort(WlsServerConfig serverConfig) {
    int restPort = 8080;
    final Set<Integer> webLogicPorts = getWebLogicPorts(serverConfig);
    while (webLogicPorts.contains(restPort)) {
      restPort++;
    }
    return restPort;
  }

  @Nonnull
  private static Set<Integer> getWebLogicPorts(WlsServerConfig serverConfig) {
    final Set<Integer> ports = new HashSet<>();
    Optional.ofNullable(serverConfig.getListenPort()).ifPresent(ports::add);
    Optional.ofNullable(serverConfig.getSslListenPort()).ifPresent(ports::add);
    Optional.ofNullable(serverConfig.getAdminPort()).ifPresent(ports::add);
    return ports;
  }

   */

  public MonitoringExporterConfiguration getConfiguration() {
    return Optional.ofNullable(configuration).map(this::toJson).map(this::toConfiguration).orElse(null);
  }

  private String toJson(Object object) {
    return new Gson().toJson(object);
  }

  private MonitoringExporterConfiguration toConfiguration(String string) {
    return new Gson().fromJson(string, MonitoringExporterConfiguration.class);
  }

  void createConfiguration(String yaml) {
    configuration = Optional.ofNullable(yaml).map(this::parse).orElse(null);
  }

  private Map<String, Object> parse(String yaml) {
    return new Yaml().load(yaml);
  }

  String getImage() {
    return Optional.ofNullable(image).orElse(DEFAULT_EXPORTER_IMAGE);
  }

  void setImage(@Nullable String image) {
    this.image = image;
  }

  public String getImagePullPolicy() {
    return imagePullPolicy;
  }

  void setImagePullPolicy(@Nullable String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }


  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("configuration", configuration)
        .append("image", image)
        .append("imagePullPolicy", imagePullPolicy)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    return (this == o)
        || ((o instanceof MonitoringExporterSpecification) && equals((MonitoringExporterSpecification) o));
  }

  private boolean equals(MonitoringExporterSpecification that) {
    return new EqualsBuilder()
        .append(configuration, that.configuration)
        .append(image, that.image)
        .append(imagePullPolicy, that.imagePullPolicy)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(configuration)
        .append(image)
        .append(imagePullPolicy)
        .toHashCode();
  }
}
