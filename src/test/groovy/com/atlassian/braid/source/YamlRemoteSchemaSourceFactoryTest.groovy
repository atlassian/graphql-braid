package com.atlassian.braid.source

import com.atlassian.braid.Link
import com.atlassian.braid.SchemaNamespace
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

import static groovy.json.JsonOutput.toJson
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class YamlRemoteSchemaSourceFactoryTest {

    @Test
    void simple() {
        def yaml = '''
name: "test"
schema: |
  schema {
      query: Query
  }
  type Query {
      foo(id: String) : Foo
  }
  type Foo {
      id: String
      name: String
      bar: String
  } 
'''
        def remoteRetriever = mock(GraphQLRemoteRetriever.class)
        def cs = YamlRemoteSchemaSourceFactory.createGraphQLSource(new StringReader(yaml), remoteRetriever)

        assert cs.getNamespace() == SchemaNamespace.of("test")
        assert cs.getSchema().getType("Foo").isPresent()
        assert cs.getLinks().isEmpty()
    }

    @Test
    void links() {
        def yaml = '''
name: "test"
links:
  - from:
      type: Foo
      field: bar
      fromField: id
    to:
      namespace: bar
      type: Bar
      field: topbar
      argument: id
schema: |
  schema {
      query: Query
  }
  type Query {
      foo: String
  }
'''
        def remoteRetriever = mock(GraphQLRemoteRetriever.class)
        def cs = YamlRemoteSchemaSourceFactory.createGraphQLSource(new StringReader(yaml), remoteRetriever)

        assert cs.getNamespace() == SchemaNamespace.of("test")
        assert cs.getLinks() == [Link.from(SchemaNamespace.of("test"), "Foo", "bar", "id")
            .to(SchemaNamespace.of("bar"), "Bar", "topbar").argument("id").build()]
    }

    @Test
    void topLevelFields() {
        def yaml = '''
name: "test"
topLevelFields:
 - "foo"
schema: |
  schema {
      query: Query
  }
  type Query {
      foo(id: String) : Foo
      bar : Bar
  }
  type Foo {
      id: String
  } 
  type Bar {
      id: String
  } 
'''
        def remoteRetriever = mock(GraphQLRemoteRetriever.class)
        def cs = YamlRemoteSchemaSourceFactory.createGraphQLSource(new StringReader(yaml), remoteRetriever)

        assert cs.getNamespace() == SchemaNamespace.of("test")

        def publicQueryFields = cs.getSchema().getType("Query").get().getFieldDefinitions()
        assert publicQueryFields.size() == 1
        assert publicQueryFields.get(0).getName() == "foo"

        def privateQueryFields = cs.getPrivateSchema().getType("Query").get().getFieldDefinitions()
        assert privateQueryFields.size() == 2
    }

    @Test
    void responseMapperAsRestRootQuery() {
        def server = new MockWebServer()
        server.enqueue(new MockResponse().setBody(toJson(["foo": "bar"])))
        server.enqueue(new MockResponse().setBody(toJson(["foo": "baz"])))
        server.start()

        def url = server.url("/{id}")

        def yaml = """
name: "test"
type: rest
rootFields:
  foo:
    uri: ${url}
    responseMapping:
      jim:
        op: copy
        source: foo
schema: |
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
        def cs = YamlRemoteSchemaSourceFactory.createRestSource(new StringReader(yaml), new HttpRestRemoteRetriever())

        def de = mock(DataFetchingEnvironment.class)
        def fd = mock(GraphQLFieldDefinition.class)
        when(fd.getName()).thenReturn("foo")
        when(de.getFieldDefinition()).thenReturn(fd)
        when(de.getArguments()).thenReturn(['id': 'blah'])

        def result = cs.newBatchLoader(cs, null).load([de])
        assert result.get().data == [["jim": "bar"]]
    }
}
