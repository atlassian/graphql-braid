package com.atlassian.braid;

import com.atlassian.braid.source.LocalQueryExecutingSchemaSource;
import com.atlassian.braid.source.MapGraphQLError;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.parser.Parser;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.DataLoaderRegistry;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.atlassian.braid.Collections.castMap;
import static com.atlassian.braid.Collections.getListValue;
import static com.atlassian.braid.Collections.getMapValue;
import static com.atlassian.braid.Util.read;
import static com.atlassian.braid.graphql.language.GraphQLNodes.printNode;
import static graphql.GraphQL.newGraphQL;
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

/**
 * Executes a test by using the test name to find a yml file containing all the information to execute and test a
 * graphql scenario
 */
public class YamlBraidExecutionRule implements MethodRule {

    @SuppressWarnings("WeakerAccess")
    public ExecutionResult executionResult = null;

    public Braid braid = null;

    @Override
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    TestConfiguration config = loadFromYaml(getYamlPath(method));

                    braid = new SchemaBraid<>()
                            .braid(SchemaBraidConfiguration.builder()
                                    .schemaSources(loadSchemaSources(config))
                                    .runtimeWiringBuilder(newRuntimeWiring()
                                            .type("Fooable", wiring -> wiring.typeResolver(__ -> null)))
                                    .build());

                    final DataLoaderRegistry dataLoaderRegistry = braid.newDataLoaderRegistry();

                    final GraphQL graphql = newGraphQL(braid.getSchema())
                            .instrumentation(new DataLoaderDispatcherInstrumentation(dataLoaderRegistry))
                            .build();

                    final TestQuery request = config.getRequest();

                    final BraidContext context =
                            new DefaultBraidContext(dataLoaderRegistry, request.getVariables(), request.getQuery());

                    ExecutionInput.Builder executionInputBuilder = ExecutionInput.newExecutionInput()
                            .query(request.getQuery())
                            .variables(request.getVariables())
                            .context(context);

                    request.getOperation().ifPresent(executionInputBuilder::operationName);

                    executionResult = graphql.execute(executionInputBuilder);

                    Map<String, Object> data = executionResult.getData();
                    Map<String, Object> response = config.getResponse();

                    assertEquals(response.get("errors"), toSpecification(executionResult.getErrors()));
                    assertEquals(response.get("data"), data);

                    base.evaluate();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }


    private List<SchemaSource<BraidContext>> loadSchemaSources(TestConfiguration config) {
        return config.getSchemaSources()
                .stream()
                .map(schemaSource -> new LocalQueryExecutingSchemaSource<>(
                        schemaSource.getNamespace(),
                        schemaSource.getTypeDefinitionRegistry(),
                        getLinks(schemaSource),
                        mapInputToResult(schemaSource))
                )
                .collect(toList());
    }

    private List<Link> getLinks(TestSchemaSource schemaSource) {
        return schemaSource.getLinks()
                .map(links -> links.stream().map(l -> this.getLink(schemaSource, l)).collect(toList()))
                .orElse(emptyList());
    }

    private Link getLink(TestSchemaSource schemaSource, Map<String, Map<String, String>> l) {
        Link.LinkBuilder link = Link.from(
                schemaSource.getNamespace(),
                l.get("from").get("type"),
                l.get("from").get("field"),
                l.get("from").getOrDefault("fromField",
                        l.get("from").get("field")))
                .to(
                        SchemaNamespace.of(l.get("to").get("namespace")),
                        l.get("to").get("type"),
                        l.get("to").get("field")
                );
        ofNullable(l.get("to").get("argument")).ifPresent(link::argument);
        return link.build();
    }

    private Function<ExecutionInput, Object> mapInputToResult(TestSchemaSource schemaSource) {
        return input -> {
            final TestQuery expected = schemaSource.getExpected();

            assertEquals(printQuery(expected.getQuery()), printQuery(input.getQuery()));
            assertEquals(expected.getVariables(), input.getVariables());
            expected.getOperation().ifPresent(operation -> assertEquals(operation, input.getOperationName()));

            TestResponse response = schemaSource.getResponse();
            return new DataFetcherResult<>(response.getData(), response.getGraphQLErrors());
        };
    }

    private String printQuery(String query) {
        return printNode(new Parser().parseDocument(query));
    }

    private List<Map<String, Object>> toSpecification(List<GraphQLError> errors) {
        return errors.stream().map(GraphQLError::toSpecification).collect(toList());
    }

    private static TestConfiguration loadFromYaml(String path) throws IOException {
        return new TestConfiguration(loadYamlAsMap(path));
    }

    private static Map<String, Object> loadYamlAsMap(String path) throws IOException {
        return castMap(new Yaml().loadAs(read(path), Map.class));
    }

    private static String getYamlPath(FrameworkMethod method) {
        return method.getName() + ".yml";
    }

    private static class TestConfiguration {

        private final Map<String, Object> configMap;

        private TestConfiguration(Map<String, Object> configMap) {
            this.configMap = requireNonNull(configMap);
        }

        TestQuery getRequest() {
            return new TestQuery(getMapValue(configMap, "request"));
        }

        List<TestSchemaSource> getSchemaSources() {
            return Collections.<String, Map<String, Object>>getListValue(configMap, "schemaSources")
                    .stream()
                    .map(TestSchemaSource::new)
                    .collect(toList());
        }

        Map<String, Object> getResponse() {
            return getMapValue(configMap, "response");
        }
    }

    private static class TestQuery {
        private final Map<String, Object> requestMap;

        private TestQuery(Map<String, Object> requestMap) {
            this.requestMap = requireNonNull(requestMap);
        }

        String getQuery() {
            return (String) requestMap.get("query");
        }

        Map<String, Object> getVariables() {
            return getMapValue(requestMap, "variables");
        }

        Optional<String> getOperation() {
            return Optional.ofNullable(requestMap.get("operation")).map(String.class::cast);
        }
    }

    private static class TestSchemaSource {
        private final Map<String, Object> schemaSourceMap;

        private TestSchemaSource(Map<String, Object> schemaSourceMap) {
            this.schemaSourceMap = requireNonNull(schemaSourceMap);
        }

        SchemaNamespace getNamespace() {
            return SchemaNamespace.of(getName());
        }

        TypeDefinitionRegistry getTypeDefinitionRegistry() {
            return new SchemaParser().parse(getSchema());
        }

        String getName() {
            return (String) schemaSourceMap.get("name");
        }

        String getSchema() {
            return (String) schemaSourceMap.get("schema");
        }

        Optional<List<Map<String, Map<String, String>>>> getLinks() {
            return Optional.ofNullable(getListValue(schemaSourceMap, "links"));
        }

        TestQuery getExpected() {
            return new TestQuery(getMapValue(schemaSourceMap, "expected"));
        }

        TestResponse getResponse() {
            return new TestResponse(getMapValue(schemaSourceMap, "response"));
        }
    }

    private static class TestResponse {
        private final Map<String, Object> responseMap;

        private TestResponse(Map<String, Object> responseMap) {
            this.responseMap = requireNonNull(responseMap);
        }

        Map<String, Object> getData() {
            return getMapValue(responseMap, "data");
        }

        List<Map<String, Object>> getErrors() {
            return getListValue(responseMap, "errors");
        }

        List<GraphQLError> getGraphQLErrors() {
            return getErrors().stream().map(MapGraphQLError::new).collect(toList());
        }
    }
}
