package com.atlassian.braid.source;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Wraps an error and removes the first path entry
 */
public class RelativeGraphQLError implements GraphQLError {
    private final GraphQLError delegate;

    RelativeGraphQLError(GraphQLError delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public String getMessage() {
        return delegate.getMessage();
    }

    @Override
    public List<SourceLocation> getLocations() {
        return delegate.getLocations();
    }

    @Override
    public ErrorType getErrorType() {
        return delegate.getErrorType();
    }

    @Override
    public List<Object> getPath() {
        if (delegate.getPath() == null || delegate.getPath().isEmpty()) {
            return delegate.getPath();
        } else {
            return delegate.getPath().subList(1, delegate.getPath().size());
        }
    }

    @Override
    public Map<String, Object> toSpecification() {
        return delegate.toSpecification();
    }

    @Override
    public Map<String, Object> getExtensions() {
        return delegate.getExtensions();
    }
}
