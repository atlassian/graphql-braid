package com.atlassian.braid.source.yaml

import com.atlassian.braid.SchemaNamespace
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

import static groovy.json.JsonOutput.toJson
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class RestRemoteSchemaSourceTest {

    @Test
    void restRemoteAsRestRootQuery() {
        def server = new MockWebServer()
        server.enqueue(new MockResponse().setBody(toJson(["foo": "bar"])))
        server.enqueue(new MockResponse().setBody(toJson(["foo": "baz"])))
        server.start()

        def url = server.url("/{id}")

        def schema = """
schema {
    query: Query
  }
  type Query {
    foo(id: String) : Foo
  } 
  type Foo {
      jim: String
  } 
"""

        def fooMapper = { sourceMap -> ["jim": sourceMap.get("foo")] }
        def fooRootField = new RestRemoteSchemaSource.RootField("foo", "${url}", fooMapper)

        def rootFields = ["foo": fooRootField]
        def links = []
        def remoteRetriever = new HttpRestRemoteRetriever()
        def restSchemaSource = new RestRemoteSchemaSource(SchemaNamespace.of("rr"),
                { -> new StringReader(schema) },
                remoteRetriever,
                rootFields,
                links,
                "foo"
        )

        def de = mock(DataFetchingEnvironment.class)
        def fd = mock(GraphQLFieldDefinition.class)
        when(fd.getName()).thenReturn("foo")
        when(de.getFieldDefinition()).thenReturn(fd)
        when(de.getArguments()).thenReturn(['id': 'blah'])

        def result = restSchemaSource.newBatchLoader(restSchemaSource, null).load([de])
        assert result.get().data == [["jim": "bar"]]
    }
}
