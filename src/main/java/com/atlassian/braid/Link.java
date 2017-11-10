package com.atlassian.braid;

/**
 * Links a field on a type to another data source
 */
@SuppressWarnings("WeakerAccess")
public class Link {
    private final String sourceType;
    private final String sourceField;
    private String targetNamespace;
    private String targetType;
    private String argumentName = "id";
    private String targetField;

    private Link(String sourceType, String sourceField) {
        this.sourceType = sourceType;
        this.sourceField = sourceField;
        this.targetField = sourceField;
    }

    public static Link from(String sourceType, String sourceField) {
        return new Link(sourceType, sourceField);
    }

    public Link to(String targetNamespace, String targetType) {
        this.targetNamespace = targetNamespace;
        this.targetType = targetType;
        return this;
    }

    public Link targetArgument(String argumentName) {
        this.argumentName = argumentName;
        return this;
    }

    public Link targetField(String targetField) {
        this.targetField = targetField;
        return this;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceField() {
        return sourceField;
    }

    public String getTargetNamespace() {
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
