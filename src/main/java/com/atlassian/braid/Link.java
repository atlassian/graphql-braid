package com.atlassian.braid;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Links a field on a type to another data source
 */
@SuppressWarnings("WeakerAccess")
public final class Link {
    /**
     * Uniquely identifies a field in a {@link SchemaSource} that will be queried as the target type
     */
    private final LinkSource source;


    /**
     * Uniquely identifies a query field in a {@link SchemaSource} that will be used to <em>replace</em> the source
     * field
     */
    private final LinkTarget target;

    /**
     * Holds information about the argument name used to query the target field
     */
    private final LinkArgument argument;

    private Link(LinkSource source, LinkTarget target, LinkArgument argument) {
        this.source = requireNonNull(source);
        this.target = requireNonNull(target);
        this.argument = requireNonNull(argument);
    }

    public static LinkBuilder from(SchemaNamespace namespace, String type, String field) {
        return new LinkBuilder(new LinkSource(namespace, type, field));
    }

    public String getSourceType() {
        return source.type;
    }

    public String getSourceField() {
        return source.field;
    }

    public SchemaNamespace getTargetNamespace() {
        return target.namespace;
    }

    public String getTargetType() {
        return target.type;
    }

    public String getTargetField() {
        return Optional.ofNullable(target.queryField).orElse(source.field);
    }

    public String getArgumentName() {
        return argument.name;
    }

    public static final class LinkBuilder {
        private LinkSource source;
        private LinkTarget target;
        private LinkArgument argument = new LinkArgument("id");

        LinkBuilder(LinkSource source) {
            this.source = requireNonNull(source);
        }

        public LinkBuilder to(SchemaNamespace namespace, String type) {
            return to(namespace, type, null);
        }

        public LinkBuilder to(SchemaNamespace namespace, String type, String queryField) {
            this.target = new LinkTarget(namespace, type, queryField);
            return this;
        }

        public LinkBuilder argument(String sourceName) {
            this.argument = new LinkArgument(sourceName);
            return this;
        }

        public Link build() {
            return new Link(source, target, argument);
        }
    }

    private static class LinkSource {
        private final SchemaNamespace namespace;
        private final String type;
        private final String field;

        private LinkSource(SchemaNamespace namespace, String type, String field) {
            this.namespace = requireNonNull(namespace);
            this.type = requireNonNull(type);
            this.field = requireNonNull(field);
        }
    }

    private static class LinkTarget {
        private final SchemaNamespace namespace;
        private final String type;
        private final String queryField;

        private LinkTarget(SchemaNamespace namespace, String type, String queryField) {
            this.namespace = requireNonNull(namespace);
            this.type = requireNonNull(type);
            this.queryField = queryField; // can be null, in which case the value is the same as LinkSource#field
        }
    }

    private static class LinkArgument {
        private final String name;

        private LinkArgument(String name) {
            this.name = requireNonNull(name);
        }
    }
}