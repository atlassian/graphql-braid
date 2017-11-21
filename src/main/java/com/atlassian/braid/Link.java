package com.atlassian.braid;

import static java.util.Objects.requireNonNull;

/**
 * Links a field on a type to another data source
 */
@SuppressWarnings("WeakerAccess")
public final class Link {
    private final String sourceType;
    private final String sourceField;
    private SchemaNamespace targetNamespace;
    private String targetType;
    private String argumentName = "id";
    private String targetField;

    private Link(String sourceType, String sourceField) {
        this.sourceType = requireNonNull(sourceType);
        this.sourceField = requireNonNull(sourceField);
        this.targetField = sourceField;
    }

    public static Link from(String sourceType, String sourceField) {
        return new Link(sourceType, sourceField);
    }

    public Link to(SchemaNamespace targetNamespace, String targetType) {
        this.targetNamespace = requireNonNull(targetNamespace);
        this.targetType = requireNonNull(targetType);
        return this;
    }

    public Link targetArgument(String argumentName) {
        this.argumentName = requireNonNull(argumentName);
        return this;
    }

    public Link targetField(String targetField) {
        this.targetField = requireNonNull(targetField);
        return this;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceField() {
        return sourceField;
    }

    public SchemaNamespace getTargetNamespace() {
        return targetNamespace;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getArgumentName() {
        return argumentName;
    }

    public String getTargetField() {
        return targetField;
    }
}
