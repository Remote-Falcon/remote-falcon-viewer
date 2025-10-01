package com.remotefalcon.exception;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Provider
public class CustomGraphQLExceptionResolver extends RuntimeException implements GraphQLError {
  public CustomGraphQLExceptionResolver(String errorMessage) {
    super(errorMessage);
  }

  @Override
  public Map<String, Object> getExtensions() {
    Map<String, Object> customAttributes = new LinkedHashMap<>();

    customAttributes.put("message", this.getMessage());

    return customAttributes;
  }

  @Override
  public List<SourceLocation> getLocations() {
    return null;
  }

  @Override
  public ErrorType getErrorType() {
    return null;
  }
}