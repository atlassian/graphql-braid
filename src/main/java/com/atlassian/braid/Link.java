package com.atlassian.braid;

import java.util.Objects;
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

    /**
     * Whether a null source field value should prompt a remote link call
     */
    private final boolean nullable;

    private Link(LinkSource source, LinkTarget target, LinkArgument argument, boolean nullable) {
        this.source = requireNonNull(source);
        this.target = requireNonNull(target);
        this.argument = requireNonNull(argument);
        this.nullable = nullable;
    }

    public static LinkBuilder from(SchemaNamespace namespace, String type, String field) {
        return from(namespace, type, field, field);
    }
    public static LinkBuilder from(SchemaNamespace namespace, String type, String field, String fromField) {
        return new LinkBuilder(new LinkSource(namespace, type, field, fromField));
    }

    /**
     * @return the type of the source from field
     */
    public String getSourceType() {
        return source.type;
    }

    /**
     * @return the field to store the linked type
     */
    public String getSourceField() {
        return source.field;
    }

    /**
     * @return the field to find the id of the linked type
     */
    public String getSourceFromField() {
        return source.fromField;
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

    public boolean isNullable() {
        return nullable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Link link = (Link) o;
        return Objects.equals(source, link.source) &&
                Objects.equals(target, link.target) &&
                Objects.equals(argument, link.argument);
    }

    @Override
    public int hashCode() {

        return Objects.hash(source, target, argument);
    }

    @Override
    public String toString() {
        return "Link{" +
                "source=" + source +
                ", target=" + target +
                ", argument=" + argument +
                '}';
    }

    public static final class LinkBuilder {
        private LinkSource source;
        private LinkTarget target;
        private LinkArgument argument = new LinkArgument("id");
        private boolean nullable = false;

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
            return new Link(source, target, argument, nullable);
        }

        public LinkBuilder setNullable(boolean nullable) {
            this.nullable = nullable;
            return this;
        }
    }

    private static class LinkSource {
        private final SchemaNamespace namespace;
        private final String type;
        private final String field;
        private final String fromField;

        private LinkSource(SchemaNamespace namespace, String type, String field, String fromField) {
            this.namespace = requireNonNull(namespace);
            this.type = requireNonNull(type);
            this.field = requireNonNull(field);
            this.fromField = requireNonNull(fromField);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LinkSource that = (LinkSource) o;
            return Objects.equals(namespace, that.namespace) &&
                    Objects.equals(type, that.type) &&
                    Objects.equals(field, that.field) &&
                    Objects.equals(fromField, that.fromField);
        }

        @Override
        public int hashCode() {

            return Objects.hash(namespace, type, field, fromField);
        }

        @Override
        public String toString() {
            return "LinkSource{" +
                    "namespace=" + namespace +
                    ", type='" + type + '\'' +
                    ", field='" + field + '\'' +
                    ", fromField='" + fromField + '\'' +
                    '}';
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LinkTarget that = (LinkTarget) o;
            return Objects.equals(namespace, that.namespace) &&
                    Objects.equals(type, that.type) &&
                    Objects.equals(queryField, that.queryField);
        }

        @Override
        public int hashCode() {

            return Objects.hash(namespace, type, queryField);
        }

        @Override
        public String toString() {
            return "LinkTarget{" +
                    "namespace=" + namespace +
                    ", type='" + type + '\'' +
                    ", queryField='" + queryField + '\'' +
                    '}';
        }
    }

    private static class LinkArgument {
        private final String name;

        private LinkArgument(String name) {
            this.name = requireNonNull(name);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LinkArgument that = (LinkArgument) o;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {

            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return "LinkArgument{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }
}