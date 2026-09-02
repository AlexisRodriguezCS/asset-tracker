package com.assettracker.peopleservice.config;

import com.assettracker.peopleservice.web.CorrelationIdFilter;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Copies the current request's correlation id onto outbound calls so a trace spans services. */
class CorrelationIdPropagator implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    String id = MDC.get(CorrelationIdFilter.MDC_KEY);
    if (id != null && !request.getHeaders().containsKey(CorrelationIdFilter.HEADER)) {
      request.getHeaders().add(CorrelationIdFilter.HEADER, id);
    }
    return execution.execute(request, body);
  }
}
