// Copyright (c) 2017, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.List;
import java.util.Optional;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ResourceAttributes;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReviewSpec;
import io.kubernetes.client.openapi.models.V1SubjectAccessReview;
import io.kubernetes.client.openapi.models.V1SubjectAccessReviewSpec;
import io.kubernetes.client.openapi.models.V1SubjectAccessReviewStatus;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/** Delegate authorization decisions to Kubernetes ABAC and/or RBAC. */
public class AuthorizationProxy {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Check if the specified principal is allowed to perform the specified operation on the specified
   * resource in the specified scope. Call this version of the method when you know that the
   * principal is not a member of any groups.
   *
   * @param principal The user, group or service account.
   * @param operation The operation to be authorized.
   * @param resource The kind of resource on which the operation is to be authorized.
   * @param resourceName The name of the resource instance on which the operation is to be
   *     authorized.
   * @param scope The scope of the operation (cluster or namespace).
   * @param namespaceName name of the namespace if scope is namespace else null.
   * @return true if the operation is allowed, or false if not.
   */
  public boolean check(
      String principal,
      Operation operation,
      Resource resource,
      String resourceName,
      Scope scope,
      String namespaceName) {
    return check(principal, null, operation, resource, resourceName, scope, namespaceName);
  }

  /**
   * Check if the specified principal is allowed to perform the specified operation on the specified
   * resource in the specified scope.
   *
   * @param principal The user, group or service account.
   * @param groups The groups that principal is a member of.
   * @param operation The operation to be authorized.
   * @param resource The kind of resource on which the operation is to be authorized.
   * @param resourceName The name of the resource instance on which the operation is to be
   *     authorized.
   * @param scope The scope of the operation (cluster or namespace).
   * @param namespaceName name of the namespace if scope is namespace else null.
   * @return true if the operation is allowed, or false if not.
   */
  public boolean check(
      String principal,
      final List<String> groups,
      Operation operation,
      Resource resource,
      String resourceName,
      Scope scope,
      String namespaceName) {
    LOGGER.entering();
    V1SubjectAccessReview subjectAccessReview =
        prepareSubjectAccessReview(
            principal, groups, operation, resource, resourceName, scope, namespaceName);
    try {
      subjectAccessReview = RequestBuilder.SAR.create(subjectAccessReview);
    } catch (ApiException e) {
      LOGGER.severe(MessageKeys.APIEXCEPTION_FROM_SUBJECT_ACCESS_REVIEW, e);
      LOGGER.exiting(Boolean.FALSE);
      return Boolean.FALSE;
    }
    V1SubjectAccessReviewStatus subjectAccessReviewStatus = subjectAccessReview.getStatus();
    Boolean result = Optional.ofNullable(subjectAccessReviewStatus)
        .map(V1SubjectAccessReviewStatus::getAllowed).orElse(false);
    LOGGER.exiting(result);
    return result;
  }

  /**
   * Prepares an instance of SubjectAccessReview and returns same.
   *
   * @param principal The user, group or service account.
   * @param groups The groups that principal is a member of.
   * @param operation The operation to be authorized.
   * @param resource The kind of resource on which the operation is to be authorized.
   * @param resourceName The name of the resource instance on which the operation is to be
   *     authorized.
   * @param scope The scope of the operation (cluster or namespace).
   * @param namespaceName name of the namespace if scope is namespace else null.
   * @return an instance of SubjectAccessReview.
   */
  private V1SubjectAccessReview prepareSubjectAccessReview(
      String principal,
      final List<String> groups,
      Operation operation,
      Resource resource,
      String resourceName,
      Scope scope,
      String namespaceName) {
    LOGGER.entering();
    V1SubjectAccessReviewSpec subjectAccessReviewSpec = new V1SubjectAccessReviewSpec();

    subjectAccessReviewSpec.setUser(principal);
    subjectAccessReviewSpec.setGroups(groups);
    subjectAccessReviewSpec.setResourceAttributes(
        prepareResourceAttributes(operation, resource, resourceName, scope, namespaceName));

    V1SubjectAccessReview subjectAccessReview = new V1SubjectAccessReview();
    subjectAccessReview.setApiVersion("authorization.k8s.io/v1");
    subjectAccessReview.setKind("SubjectAccessReview");
    subjectAccessReview.setMetadata(new V1ObjectMeta());
    subjectAccessReview.setSpec(subjectAccessReviewSpec);
    LOGGER.exiting(subjectAccessReview);
    return subjectAccessReview;
  }

  /**
   * Prepares an instance of ResourceAttributes and returns same.
   *
   * @param operation The operation to be authorized.
   * @param resource The kind of resource on which the operation is to be authorized.
   * @param resourceName The name of the resource instance on which the operation is to be
   *     authorized.
   * @param scope The scope of the operation (cluster or namespace).
   * @param namespaceName name of the namespace if scope is namespace else null.
   * @return an instance of ResourceAttributes
   */
  private V1ResourceAttributes prepareResourceAttributes(
      Operation operation,
      Resource resource,
      String resourceName,
      Scope scope,
      String namespaceName) {
    LOGGER.entering();
    V1ResourceAttributes resourceAttributes = new V1ResourceAttributes();
    if (null != operation) {
      resourceAttributes.setVerb(operation.toString());
    }
    if (null != resource) {
      resourceAttributes.setResource(resource.resourceName);
      resourceAttributes.setSubresource(resource.subResource);
      resourceAttributes.setGroup(resource.apiGroup);
    }

    if (null != resourceName) {
      resourceAttributes.setName(resourceName);
    }

    if (Scope.NAMESPACE == scope) {
      resourceAttributes.setNamespace(namespaceName);
    }
    LOGGER.exiting(resourceAttributes);
    return resourceAttributes;
  }

  V1SelfSubjectRulesReview review(String namespace) {
    V1SelfSubjectRulesReview subjectRulesReview = new V1SelfSubjectRulesReview();
    V1SelfSubjectRulesReviewSpec spec = new V1SelfSubjectRulesReviewSpec();
    spec.setNamespace(namespace);
    subjectRulesReview.setSpec(spec);
    subjectRulesReview.setMetadata(new V1ObjectMeta()); // work around NPE in GenericKubernetesApi
    try {
      return RequestBuilder.SSRR.create(subjectRulesReview);
    } catch (ApiException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      return null;
    }
  }

  public enum Operation {
    GET("get"),
    LIST("list"),
    CREATE("create"),
    UPDATE("update"),
    PATCH("patch"),
    WATCH("watch"),
    DELETE("delete"),
    DELETECOLLECTION("deletecollection");

    private final String value;

    Operation(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return String.valueOf(this.value);
    }
  }

  public enum Resource {
    CONFIGMAPS("configmaps", ""),
    PODS("pods", ""),
    LOGS("pods", "log", ""),
    EXEC("pods", "exec", ""),
    EVENTS("events", ""),
    SERVICES("services", ""),
    NAMESPACES("namespaces", ""),
    JOBS("jobs", "batch"),
    CRDS("customresourcedefinitions", "apiextensions.k8s.io"),
    DOMAINS("domains", "weblogic.oracle"),
    DOMAINSTATUSES("domains", "status", "weblogic.oracle"),
    SELFSUBJECTRULESREVIEWS("selfsubjectrulesreviews", "authorization.k8s.io"),
    TOKENREVIEWS("tokenreviews", "authentication.k8s.io"),
    SECRETS("secrets", "");

    private final String resourceName;
    private final String subResource;
    private final String apiGroup;

    Resource(String resourceName, String apiGroup) {
      this(resourceName, "", apiGroup);
    }

    Resource(String resourceName, String subResource, String apiGroup) {
      this.resourceName = resourceName;
      this.subResource = subResource;
      this.apiGroup = apiGroup;
    }

    public String getResourceName() {
      return resourceName;
    }

    public String getSubResource() {
      return subResource;
    }

    public String getApiGroup() {
      return apiGroup;
    }

    @Override
    public String toString() {
      return String.valueOf(this.resourceName);
    }
  }

  public enum Scope {
    NAMESPACE("namespace"),
    CLUSTER("cluster");

    private final String value;

    Scope(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return String.valueOf(this.value);
    }
  }
}
