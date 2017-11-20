package com.atlassian.braid.source;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.atlassian.braid.source.OptionalHelper.castNullable;
import static com.atlassian.braid.source.OptionalHelper.castNullableList;

/**
 * Turns a map representing the data of a GraphQL error into a real GraphQLError
 */
@SuppressWarnings("WeakerAccess")
public class MapGraphQLError implements GraphQLError {


    private final String message;
    private final List<SourceLocation> locations;
    private final ErrorType errorType;
    private final List<Object> path;

    public MapGraphQLError(Map<String, Object> error) {
        this.message = castNullable(error.get("message"), String.class).orElse("Unknown error");
        this.locations = castNullableList(error.get("locations"), Map.class)
                .map(errors ->
                        errors.stream()
                                .filter(err -> err.containsKey("line") && err.containsKey("column"))
                                .map(err -> new SourceLocation((Integer) err.get("line"), (Integer) err.get("column")))
                                .collect(Collectors.toList()))
                .orElse(null);
        this.errorType = ErrorType.DataFetchingException;
        this.path = castNullableList(error.get("path"), Object.class).orElse(null);
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return locations;
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
