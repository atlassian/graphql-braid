package com.atlassian.braid;

import com.atlassian.braid.source.LocalSchemaSource;
import com.atlassian.braid.source.MapGraphQLError;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.parser.Parser;
import graphql.schema.idl.SchemaParser;
import org.dataloader.DataLoaderRegistry;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.atlassian.braid.Util.read;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static org.junit.Assert.assertEquals;

/**
 * Executes a test by using the test name to find a yml file containing all the information to execute and test a
 * graphql scenario
 */
@SuppressWarnings("unchecked")
public class YamlBraidExecutionRule implements MethodRule {

    @SuppressWarnings("WeakerAccess")
    public ExecutionResult executionResult = null;

    @Override
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    Map<String, Object> config = (Map<String, Object>) new Yaml().load(
                            read(method.getName() + ".yml"));

                    Braid braid = new SchemaBraid().braid(SchemaBraidConfiguration.<DefaultBraidContext>builder()
                            .schemaSources(loadSchemaSources(config))
                            .build());
                    DataLoaderRegistry dataLoaderRegistry = braid.newDataLoaderRegistry();
                    GraphQL graphql = GraphQL.newGraphQL(braid.getSchema())
                            .instrumentation(new DataLoaderDispatcherInstrumentation(dataLoaderRegistry))
                            .build();

                    Map<String, Object> req = (Map<String, Object>) config.get("request");
                    executionResult = graphql.execute(ExecutionInput.newExecutionInput()
                            .query((String) req.get("query"))
                            .variables((Map<String, Object>) req.get("variables"))
                            .context(new DefaultBraidContext(dataLoaderRegistry, (Map<String, Object>) req.get("variables"), (String) req.get("query"))));

                    Map<String, Object> data = executionResult.getData();
                    Map<String, Object> response = (Map<String, Object>) config.get("response");
                    assertEquals(response.get("errors"), executionResult.getErrors().stream().map(e -> e.toSpecification()).collect(Collectors.toList()));
                    assertEquals(response.get("data"), data);
                    base.evaluate();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    private List<SchemaSource<DefaultBraidContext>> loadSchemaSources(Map<String, Object> config) {
        GraphQLQueryPrinter printer = new GraphQLQueryPrinter();
        return ((List<Map<String, Object>>) config.get("schemaSources")).stream()
                .map(m -> new LocalSchemaSource<DefaultBraidContext>(
                        SchemaNamespace.of((String) m.get("name")),
                        new SchemaParser().parse((String) m.get("schema")),
                        ofNullable((List<Map<String, Map<String, String>>>) m.get("links"))
                                .map(links -> links.stream().map(
                                        l -> {
                                            Link.LinkBuilder link = Link.from(
                                                    SchemaNamespace.of("name)"),
                                                    l.get("from").get("type"),
                                                    l.get("from").get("field"))
                                                    .to(
                                                            SchemaNamespace.of(l.get("to").get("namespace")),
                                                            l.get("to").get("type"),
                                                            l.get("to").get("field")
                                                    );
                                            ofNullable(l.get("to").get("argument")).ifPresent(link::argument);
                                            return link.build();
                                        })
                                        .collect(Collectors.toList()))
                                .orElse(emptyList()),
                        mapInputToResult(printer, m))
                )
                .collect(Collectors.toList());
    }

    private Function<ExecutionInput, Object> mapInputToResult(GraphQLQueryPrinter printer, Map<String, Object> m) {
        return input -> {
            Map<String, Object> expected = (Map<String, Object>) m.get("expected");
            assertEquals(printer.print(new Parser().parseDocument((String) expected.get("query"))),
                    printer.print(new Parser().parseDocument(input.getQuery())));
            assertEquals(expected.get("variables"), input.getVariables());
            Map<String, Object> response = (Map<String, Object>) m.get("response");
            return new DataFetcherResult(response.get("data"),
                    ((List<Map<String, Object>>) response.get("errors")).stream()
                            .map(MapGraphQLError::new)
                            .collect(Collectors.toList()));
        };
    }
}
