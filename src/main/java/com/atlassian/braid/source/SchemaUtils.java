package com.atlassian.braid.source;

import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.Reader;
import java.util.function.Supplier;

import static com.atlassian.braid.TypeUtils.filterQueryType;

public final class SchemaUtils {

    private SchemaUtils() {
    }

    public static TypeDefinitionRegistry loadPublicSchema(Supplier<Reader> schemaProvider, String... topLevelFields) {
        return filterQueryType(loadSchema(schemaProvider), topLevelFields);
    }

    public static TypeDefinitionRegistry loadSchema(Supplier<Reader> schema) {
        SchemaParser parser = new SchemaParser();
        return parser.parse(schema.get());
    }
}
