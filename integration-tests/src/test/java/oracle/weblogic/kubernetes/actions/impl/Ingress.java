// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1HTTPIngressPath;
import io.kubernetes.client.openapi.models.V1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1IngressBackend;
import io.kubernetes.client.openapi.models.V1IngressList;
import io.kubernetes.client.openapi.models.V1IngressRule;
import io.kubernetes.client.openapi.models.V1IngressServiceBackend;
import io.kubernetes.client.openapi.models.V1IngressSpec;
import io.kubernetes.client.openapi.models.V1IngressTLS;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ServiceBackendPort;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import static oracle.weblogic.kubernetes.actions.ActionConstants.INGRESS_API_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.INGRESS_KIND;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listNamespacedIngresses;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 * Utility class for ingress resource .
 */
public class Ingress {

  /**
   * Create an ingress for the WebLogic domain with domainUid in the specified domain namespace.
   * The ingress hosts are set to
   * [domainUid.domainNamespace.adminserver.test, domainUid.domainNamespace.clusterName.test'].
   *
   * @param ingressName name of the ingress to be created
   * @param domainNamespace the WebLogic domain namespace in which the ingress will be created
   * @param domainUid the WebLogic domainUid which is backend to the ingress
   * @param clusterNameMsPortMap the map with key as cluster name and value as managed server port of the cluster
   * @param annotations annotations to create ingress resource
   * @param ingressClassName Ingress class name
   * @param setIngressHost if false does not set ingress host
   * @param tlsSecret name of the TLS secret if any
   * @param enableAdminServerRouting enable the ingress rule to admin server
   * @param adminServerPort the port number of admin server pod of the domain
   * @return list of ingress hosts or null if got ApiException when calling Kubernetes client API to create ingress
   */
  public static List<String> createIngress(String ingressName,
                                           String domainNamespace,
                                           String domainUid,
                                           Map<String, Integer> clusterNameMsPortMap,
                                           Map<String, String> annotations,
                                           String ingressClassName,
                                           boolean setIngressHost,
                                           String tlsSecret,
                                           boolean enableAdminServerRouting,
                                           int adminServerPort) {

    List<String> ingressHostList = new ArrayList<>();
    ArrayList<V1IngressRule> ingressRules = new ArrayList<>();

    // set the ingress rule for admin server
    if (enableAdminServerRouting) {
      V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
          .path(null)
          .pathType("ImplementationSpecific")
          .backend(new V1IngressBackend()
              .service(new V1IngressServiceBackend()
                  .name(domainUid + "-admin-server")
                  .port(new V1ServiceBackendPort().number(adminServerPort)))
          );
      ArrayList<V1HTTPIngressPath> httpIngressPaths = new ArrayList<>();
      httpIngressPaths.add(httpIngressPath);

      // set the ingress rule
      String ingressHost = domainUid + "." + domainNamespace + ".adminserver.test";
      if (!setIngressHost) {
        ingressHost = "";
        ingressHostList.add("*");
      } else {
        ingressHostList.add(ingressHost);
      }
      V1IngressRule ingressRule = new V1IngressRule()
          .host(ingressHost)
          .http(new V1HTTPIngressRuleValue()
              .paths(httpIngressPaths));

      ingressRules.add(ingressRule);
    }

    // set the ingress rule for clusters
    clusterNameMsPortMap.forEach((clusterName, managedServerPort) -> {
      // set the http ingress paths
      V1HTTPIngressPath httpIngressPath1 = new V1HTTPIngressPath()
              .path(null)
              .pathType("ImplementationSpecific")
              .backend(new V1IngressBackend()
                  .service(new V1IngressServiceBackend()
                      .name(domainUid + "-cluster-" + clusterName.toLowerCase().replace("_", "-"))
                      .port(new V1ServiceBackendPort()
                          .number(managedServerPort)))
              );
      ArrayList<V1HTTPIngressPath> httpIngressPaths1 = new ArrayList<>();
      httpIngressPaths1.add(httpIngressPath1);

      // set the ingress rule
      String ingressHost1 = domainUid + "." + domainNamespace + "." + clusterName + ".test";
      if (!setIngressHost) {
        ingressHost1 = "";
        ingressHostList.add("*");
      } else {
        ingressHostList.add(ingressHost1);
      }

      V1IngressRule ingressRule1 = new V1IngressRule()
              .host(ingressHost1)
              .http(new V1HTTPIngressRuleValue()
                      .paths(httpIngressPaths1));

      ingressRules.add(ingressRule1);
    });

    List<V1IngressTLS> tlsList = new ArrayList<>();
    if (tlsSecret != null) {
      clusterNameMsPortMap.forEach((clusterName, port) -> {
        tlsList.add(new V1IngressTLS()
            .hosts(Arrays.asList(
                domainUid + "." + domainNamespace + "." + clusterName + ".test"))
            .secretName(tlsSecret));
      });
    }

    // set the ingress
    V1Ingress ingress = new V1Ingress()
        .apiVersion(INGRESS_API_VERSION)
        .kind(INGRESS_KIND)
        .metadata(new V1ObjectMeta()
            .name(ingressName)
            .namespace(domainNamespace)
            .annotations(annotations))
        .spec(new V1IngressSpec()
            .rules(ingressRules));
    if (ingressClassName != null) {
      ingress.setSpec(ingress.getSpec().ingressClassName(ingressClassName));
    }
    if (tlsSecret != null) {
      V1IngressSpec spec = ingress.getSpec().tls(tlsList);
      ingress.setSpec(spec);
    }

    // create the ingress
    try {
      Kubernetes.createIngress(domainNamespace, ingress);
    } catch (ApiException apex) {
      getLogger().severe("got ApiException while calling createIngress: {0}", apex.getResponseBody());
      return null;
    }

    return ingressHostList;
  }

  /**
   * Create an ingress in specified namespace.
   * @param ingressName ingress name
   * @param namespace namespace in which the ingress will be created
   * @param annotations annotations of the ingress
   * @param ingressClassName Ingress class name
   * @param ingressRules a list of ingress rules
   * @param tlsList list of ingress tls
   * @throws ApiException if Kubernetes API call fails
   */
  public static void createIngress(String ingressName,
                                   String namespace,
                                   Map<String, String> annotations,
                                   String ingressClassName,
                                   List<V1IngressRule> ingressRules,
                                   List<V1IngressTLS> tlsList) throws ApiException {

    // set the ingress
    V1Ingress ingress = new V1Ingress()
        .apiVersion(INGRESS_API_VERSION)
        .kind(INGRESS_KIND)
        .metadata(new V1ObjectMeta()
            .name(ingressName)
            .namespace(namespace)
            .annotations(annotations))
        .spec(new V1IngressSpec()
            .rules(ingressRules));

    if (ingressClassName != null) {
      ingress.setSpec(ingress.getSpec().ingressClassName(ingressClassName));
    }

    if (tlsList != null) {
      V1IngressSpec spec = ingress.getSpec().tls(tlsList);
      ingress.setSpec(spec);
    }

    // create the ingress
    try {
      Kubernetes.createIngress(namespace, ingress);
    } catch (ApiException apex) {
      getLogger().severe("got ApiException while calling createIngress: {0}", apex.getResponseBody());
      throw apex;
    }
  }

  /**
   * List all of the ingresses in the specified namespace.
   *
   * @param namespace the namespace to which the ingresses belong
   * @return a list of ingress names in the namespace
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> listIngresses(String namespace) throws ApiException {

    List<String> ingressNames = new ArrayList<>();
    V1IngressList ingressList = Kubernetes.listNamespacedIngresses(namespace);
    List<V1Ingress> listOfIngress = ingressList.getItems();

    listOfIngress.forEach(ingress -> {
      if (ingress.getMetadata() != null) {
        ingressNames.add(ingress.getMetadata().getName());
      }
    });

    return ingressNames;
  }
  
  /**
   * Get ingress object in the specified namespace.
   *
   * @param namespace the namespace in which the ingress exists
   * @param ingressName name of the ingress object
   * @return an Optional ingress name in the namespace
   * @throws ApiException if Kubernetes client API call fails
   */
  public static Optional<V1Ingress> getIngress(String namespace, String ingressName) throws ApiException {
    return listNamespacedIngresses(namespace).getItems().stream().filter(
        ingress -> ingress.getMetadata().getName().equals(ingressName)).findAny();
  }
  
  /**
   * Update Ingress in the given namespace.
   *
   * @param namespace namespace name
   * @param ingress V1Ingress body
   * @throws ApiException when update fails
   */
  public static void updateIngress(String namespace, V1Ingress ingress) throws ApiException {
    Kubernetes.updateNamespacedIngresses(namespace, ingress);
  } 

  /**
   * Delete an ingress in the specified namespace.
   *
   * @param name  ingress name to be deleted
   * @param namespace namespace in which the specified ingress exists
   * @return true if deleting ingress succeed, false otherwise
   * @throws ApiException if Kubernetes API client call fails
   */
  public static boolean deleteIngress(String name, String namespace) throws ApiException {
    return Kubernetes.deleteIngress(name, namespace);
  }
}
