package com.atlassian.braid.source;

import com.atlassian.braid.BraidContext;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

abstract class AbstractSchemaSource<C extends BraidContext> implements SchemaSource<C> {
    private final SchemaNamespace namespace;
    private final TypeDefinitionRegistry schema;
    private final TypeDefinitionRegistry privateSchema;
    private final List<Link> links;

    AbstractSchemaSource(SchemaNamespace namespace,
                         TypeDefinitionRegistry schema,
                         TypeDefinitionRegistry privateSchema,
                         List<Link> links) {
        this.namespace = requireNonNull(namespace);
        this.schema = requireNonNull(schema);
        this.privateSchema = requireNonNull(privateSchema);
        this.links = requireNonNull(links);
    }

    @Override
    public final SchemaNamespace getNamespace() {
        return namespace;
    }

    @Override
    public final TypeDefinitionRegistry getSchema() {
        return schema;
    }

    @Override
    public final TypeDefinitionRegistry getPrivateSchema() {
        return privateSchema;
    }

    @Override
    public final List<Link> getLinks() {
        return new ArrayList<>(links);
    }
}
