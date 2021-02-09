// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import oracle.kubernetes.json.AdditionalProperties;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.operator.ImagePullPolicy;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import org.yaml.snakeyaml.Yaml;

import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_EXPORTER_IMAGE;

public class MonitoringExporterSpecification {

  @Description("The configuration for the WebLogic Monitoring Exporter sidecar. If specified, the operator will "
        + "deploy a sidecar alongside each server instance. See https://github.com/oracle/weblogic-monitoring-exporter")
  @AdditionalProperties
  private Map<String,Object> configuration;

  /**
   * The Monitoring Exporter sidecar image.
   *
   */
  @Description(
        "The WebLogic Monitoring Exporter sidecar image name. Defaults to "
           + DEFAULT_EXPORTER_IMAGE)
  private String image;
  
  @Description(
      "The image pull policy for the WebLogic Monitoring Exporter sidecar image. "
          + "Legal values are Always, Never, and IfNotPresent. "
          + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  @EnumClass(ImagePullPolicy.class)
  private String imagePullPolicy;

  MonitoringExporterConfiguration getConfiguration() {
    return Optional.ofNullable(configuration).map(this::toJson).map(this::toConfiguration).orElse(null);
  }

  private String toJson(Object object) {
    return new Gson().toJson(object);
  }

  private MonitoringExporterConfiguration toConfiguration(String string) {
    return new Gson().fromJson(string, MonitoringExporterConfiguration.class);
  }

  void createConfiguration(String yaml) {
    configuration = new Yaml().load(yaml);
  }

  String getImage() {
    return Optional.ofNullable(image).orElse(DEFAULT_EXPORTER_IMAGE);
  }

  void setImage(@Nullable String image) {
    this.image = image;
  }

  String getImagePullPolicy() {
    return Optional.ofNullable(imagePullPolicy).orElse(KubernetesUtils.getInferredImagePullPolicy(getImage()));
  }

  void setImagePullPolicy(@Nullable String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }
}
