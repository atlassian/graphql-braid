package com.atlassian.braid;

import com.atlassian.braid.document.DocumentMappers;
import com.atlassian.braid.document.DocumentMapperFactory;
import com.atlassian.braid.java.util.BraidMaps;
import com.atlassian.braid.java.util.BraidObjects;
import com.atlassian.braid.source.LocalQueryExecutingSchemaSource;
import com.atlassian.braid.source.MapGraphQLError;
import com.google.common.base.Supplier;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.dataloader.LazyRecursiveDataLoaderDispatcherInstrumentation;
import graphql.parser.Parser;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.DataLoaderRegistry;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;

import static com.atlassian.braid.Util.read;
import static com.atlassian.braid.graphql.language.GraphQLNodes.printNode;
import static com.atlassian.braid.java.util.BraidObjects.cast;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceFactory.getReplaceFromField;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Suppliers.memoize;
import static graphql.GraphQL.newGraphQL;
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
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
                            .instrumentation(new LazyRecursiveDataLoaderDispatcherInstrumentation(dataLoaderRegistry))
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

                    Map<String, Object> response = config.getResponse();

                    assertEquals(response.get("errors"), toSpecification(executionResult.getErrors()));
                    assertEquals(response.get("data"), executionResult.<Map<String, Object>>getData());

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
                        schemaSource.getMapper(),
                        mapInputToResult(schemaSource))
                )
                .collect(toList());
    }

    private List<Link> getLinks(TestSchemaSource schemaSource) {
        return schemaSource.getLinks()
                .map(links -> links.stream().map(link -> getLink(schemaSource, link)).collect(toList()))
                .orElse(emptyList());
    }

    private static Link getLink(TestSchemaSource schemaSource, Map<String, Map<String, Object>> l) {
        final Map<String, Object> from = l.get("from");
        final Map<String, Object> to = l.get("to");

        Link.LinkBuilder link = Link.from(
                schemaSource.getNamespace(),
                getString(from, "type"),
                getString(from, "field"),
                BraidMaps.get(from, "fromField").map(BraidObjects::<String>cast).orElseGet(() -> getString(from, "field")))
                .to(SchemaNamespace.of(getString(to, "namespace")),
                        getString(to, "type"),
                        getStringOrNull(to, "field"),
                        getStringOrNull(to, "variableField"));
        if (getReplaceFromField(l)) {
            link.replaceFromField();
        }

        BraidMaps.get(to, "argument").map(BraidObjects::<String>cast).ifPresent(link::argument);
        BraidMaps.get(to, "nullable").map(BraidObjects::<Boolean>cast).ifPresent(link::setNullable);

        return link.build();
    }

    private Function<ExecutionInput, Object> mapInputToResult(TestSchemaSource schemaSource) {
        return input -> {
            try {
                final TestQuery expected = schemaSource.getExpected().poll();
                if (expected == null) {
                    throw new IllegalArgumentException(schemaSource + " shouldn't have been called");
                }

                assertThat(QueryAssertion.from(input)).isEqualTo(QueryAssertion.from(expected));

                return schemaSource.getResponse().poll().getResult();
            } catch (Throwable e) {
                // necessary to make sure assertion error show in the JUnit output, otherwise they're kinda swallowed
                // by the futures and GaphQL java (since they're Errors and not Exception
                throw new RuntimeException(e);
            }
        };
    }

    private static String printQuery(String query) {
        try {
            return printNode(new Parser().parseDocument(query));
        } catch (Exception e) {
            throw new IllegalStateException("Exception while printing query:\n" + query + "\n", e);
        }
    }

    private List<Map<String, Object>> toSpecification(List<GraphQLError> errors) {
        return errors.stream().map(GraphQLError::toSpecification).collect(toList());
    }

    private static TestConfiguration loadFromYaml(String path) throws IOException {
        return new TestConfiguration(loadYamlAsMap(path));
    }

    private static Map<String, Object> loadYamlAsMap(String path) throws IOException {
        return BraidObjects.cast(new Yaml().loadAs(read(path), Map.class));
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
            return BraidMaps.get(configMap, "request")
                    .map(BraidObjects::<Map<String, Object>>cast)
                    .map(TestQuery::new)
                    .orElse(null);
        }

        List<TestSchemaSource> getSchemaSources() {
            return BraidMaps.get(configMap, "schemaSources")
                    .map(BraidObjects::<List<Map<String, Object>>>cast)
                    .map(sources -> sources.stream().map(TestSchemaSource::new).collect(toList()))
                    .orElse(emptyList());
        }

        Map<String, Object> getResponse() {
            return BraidMaps.get(configMap, "response")
                    .map(BraidObjects::<Map<String, Object>>cast)
                    .orElse(null);
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
            return BraidMaps.get(requestMap, "variables").map(BraidObjects::<Map<String, Object>>cast).orElse(null);
        }

        Optional<String> getOperation() {
            return Optional.ofNullable(requestMap.get("operation")).map(String.class::cast);
        }
    }

    private static class TestSchemaSource {
        private final Map<String, Object> schemaSourceMap;
        private final Supplier<Queue<TestQuery>> expected;
        private final Supplier<Queue<TestResponse>> response;


        private TestSchemaSource(Map<String, Object> schemaSourceMap) {
            this.schemaSourceMap = requireNonNull(schemaSourceMap);
            this.expected = memoize(() -> parseAsQueue(schemaSourceMap, "expected", TestQuery::new));
            this.response = memoize(() -> parseAsQueue(schemaSourceMap, "response", TestResponse::new));
        }

        SchemaNamespace getNamespace() {
            return SchemaNamespace.of(getName());
        }

        TypeDefinitionRegistry getTypeDefinitionRegistry() {
            return new SchemaParser().parse(getSchema());
        }

        String getName() {
            return getString(this.schemaSourceMap, "name");
        }

        String getSchema() {
            return getString(schemaSourceMap, "schema");
        }

        Optional<List<Map<String, Map<String, Object>>>> getLinks() {
            return BraidMaps.get(schemaSourceMap, "links").map(BraidObjects::cast);
        }

        Queue<TestQuery> getExpected() {
            return expected.get();
        }

        Queue<TestResponse> getResponse() {
            return response.get();
        }

        DocumentMapperFactory getMapper() {
            return BraidMaps.get(schemaSourceMap, "mapper")
                    .map(BraidObjects::<List<Map<String, Object>>>cast)
                    .map(DocumentMappers::fromYamlList)
                    .orElse(DocumentMappers.identity());
        }
    }

    private static <T> List<T> asList(Object o) {
        return o instanceof List ? cast(o) : singletonList(cast(o));
    }

    private static <T> Queue<T> parseAsQueue(Map<String, Object> map, String key,
                                             Function<Map<String, Object>, T> transform) {
        return new LinkedList<>(BraidMaps.get(map, key)
                .map(YamlBraidExecutionRule::<Map<String, Object>>asList)
                .map(l -> l.stream().map(transform).collect(toList()))
                .orElse(emptyList()));
    }

    private static String getString(Map<String, Object> map, String key) {
        return BraidMaps.get(map, key).map(BraidObjects::<String>cast).orElseThrow(IllegalStateException::new);
    }

    private static String getStringOrNull(Map<String, Object> map, String key) {
        return BraidMaps.get(map, key).map(BraidObjects::<String>cast).orElse(null);
    }

    private static class TestResponse {
        private final Map<String, Object> responseMap;

        private TestResponse(Map<String, Object> responseMap) {
            this.responseMap = requireNonNull(responseMap);
        }

        Map<String, Object> getData() {
            return BraidMaps.get(responseMap, "data")
                    .map(BraidObjects::<Map<String, Object>>cast)
                    .orElse(null);
        }

        List<Map<String, Object>> getErrors() {
            return BraidMaps.get(responseMap, "errors")
                    .map(BraidObjects::<List<Map<String, Object>>>cast)
                    .orElse(emptyList());
        }

        private List<GraphQLError> getGraphQLErrors() {
            return getErrors().stream().map(MapGraphQLError::new).collect(toList());
        }

        DataFetcherResult<Map<String, Object>> getResult() {
            return new DataFetcherResult<>(this.getData(), this.getGraphQLErrors());
        }
    }

    private static class QueryAssertion {
        private final String query;
        private final Map<String, Object> variables;
        private final String operationName;

        private QueryAssertion(String query, Map<String, Object> variables, String operationName) {
            this.query = query;
            this.variables = variables;
            this.operationName = operationName;
        }

        static QueryAssertion from(ExecutionInput input) {
            return new QueryAssertion(printQuery(input.getQuery()), input.getVariables(), input.getOperationName());
        }

        static QueryAssertion from(TestQuery testQuery) {
            return new QueryAssertion(printQuery(testQuery.getQuery()), testQuery.getVariables(), testQuery.getOperation().orElse(null));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            QueryAssertion that = (QueryAssertion) o;
            return Objects.equals(query, that.query) &&
                    Objects.equals(variables, that.variables) &&
                    // operation name is check only if both are non-null
                    (operationName == null || that.operationName == null
                            || Objects.equals(operationName, that.operationName));
        }

        @Override
        public int hashCode() {
            return Objects.hash(query, variables, operationName);
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("query", query)
                    .add("variables", variables)
                    .add("operationName", operationName)
                    .toString();
        }
    }
}
