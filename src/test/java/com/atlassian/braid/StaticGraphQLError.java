package com.atlassian.braid;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.List;

/**
 */
public class StaticGraphQLError implements GraphQLError {

    private final String message;
    private final List<SourceLocation> sourceLocations;
    private final ErrorType errorType;
    private final List<Object> path;

    public StaticGraphQLError(String message) {
        this(message, null, ErrorType.DataFetchingException, null);
    }

    public StaticGraphQLError(String message, List<Object> path) {
        this(message, null, ErrorType.DataFetchingException, path);
    }

    public StaticGraphQLError(String message, List<SourceLocation> sourceLocations, ErrorType errorType, List<Object> path) {
        this.message = message;
        this.sourceLocations = sourceLocations;
        this.errorType = errorType;
        this.path = path;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return sourceLocations;
    }

    @Override
    public ErrorType getErrorType() {
        return errorType;
    }

    @Override
    public List<Object> getPath() {
        return path;
    }
}
