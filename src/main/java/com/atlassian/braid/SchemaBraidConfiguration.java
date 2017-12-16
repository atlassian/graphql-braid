package com.atlassian.braid;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for braid
 */
@SuppressWarnings("WeakerAccess")
public class SchemaBraidConfiguration<C extends BraidContext> {
    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final RuntimeWiring.Builder runtimeWiringBuilder;
    private final List<SchemaSource<C>> schemaSources;

    public static <C extends BraidContext> SchemaBraidConfigurationBuilder<C> builder() {
        return new SchemaBraidConfigurationBuilder<>();
    }

    private SchemaBraidConfiguration(TypeDefinitionRegistry typeDefinitionRegistry, RuntimeWiring.Builder runtimeWiringBuilder, List<SchemaSource<C>> schemaSources) {
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.runtimeWiringBuilder = runtimeWiringBuilder;
        this.schemaSources = schemaSources;
    }

    public TypeDefinitionRegistry getTypeDefinitionRegistry() {
        return typeDefinitionRegistry;
    }

    public RuntimeWiring.Builder getRuntimeWiringBuilder() {
        return runtimeWiringBuilder;
    }

    public List<SchemaSource<C>> getSchemaSources() {
        return schemaSources;
    }

    public static class SchemaBraidConfigurationBuilder<C extends BraidContext> {
        private TypeDefinitionRegistry typeDefinitionRegistry = new TypeDefinitionRegistry();
        private RuntimeWiring.Builder runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();
        private List<SchemaSource<C>> schemaSources = new ArrayList<>();

        private SchemaBraidConfigurationBuilder() {}

        public SchemaBraidConfigurationBuilder<C> typeDefinitionRegistry(TypeDefinitionRegistry typeDefinitionRegistry) {
            this.typeDefinitionRegistry = typeDefinitionRegistry;
            return this;
        }

        public SchemaBraidConfigurationBuilder<C> runtimeWiringBuilder(RuntimeWiring.Builder runtimeWiringBuilder) {
            this.runtimeWiringBuilder = runtimeWiringBuilder;
            return this;
        }

        public final SchemaBraidConfigurationBuilder<C> schemaSource(SchemaSource<C> schemaSource) {
            this.schemaSources.add(schemaSource);
            return this;
        }

        public final SchemaBraidConfigurationBuilder<C> schemaSources(List<SchemaSource<C>> schemaSources) {
            this.schemaSources.addAll(schemaSources);
            return this;
        }

        public SchemaBraidConfiguration<C> build() {
            return new SchemaBraidConfiguration<>(typeDefinitionRegistry, runtimeWiringBuilder, schemaSources);
        }
    }
}

