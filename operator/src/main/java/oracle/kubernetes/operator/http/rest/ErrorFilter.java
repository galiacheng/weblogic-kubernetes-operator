// Copyright (c) 2017, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import javax.annotation.Priority;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import oracle.kubernetes.operator.http.rest.model.ErrorModel;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/**
 * ErrorFilter reformats string entities from non-success responses into arrays of message entities.
 */
@Provider
@Priority(FilterPriorities.ERROR_FILTER_PRIORITY)
public class ErrorFilter implements ContainerResponseFilter {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public ErrorFilter() {
    // nothing to do
  }

  @Override
  public void filter(ContainerRequestContext req, ContainerResponseContext res) {
    LOGGER.entering();
    int status = res.getStatus();
    LOGGER.finer("status=" + status);
    if ((status >= 200) && (status <= 299)) {
      // don't wrap success messages
      return;
    }
    Object entity = res.getEntity();
    if (entity == null) {
      // don't wrap null entities
      LOGGER.finer("null entity");
    } else if (entity instanceof String detail) {
      // Wrap the error in an 'Error' object that converts the error to a
      // json object matching the Oracle REST style guide:
      LOGGER.finer("String entity=" + detail);
      ErrorModel error = new ErrorModel(status, detail);
      res.setEntity(error, res.getEntityAnnotations(), MediaType.APPLICATION_JSON_TYPE);
    } else {
      LOGGER.finer("Non-string entity", entity);
    }
    LOGGER.exiting();
  }
}
