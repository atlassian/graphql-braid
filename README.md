# Welcome to graphql-braid #

Combines graphql backends into one schema.

## Using

### Use Braid to create your combined schema

The usage of Braid happens in two steps: creation of a braided schema from multiple data sources, and using that combined schema to 
build a GraphQL instance per-request.

By default, Braid will aggregate the top level fields of all data sources into the final schema.  You can also configure
which fields are added, if you don't want all of them in the final schema.

In the simpliest usage of Braid, you can create a schema that combines the top level queries of multiple data sources. 
For example, this configuration will combine two data sources:

```
#!java
Braid braid = new SchemaBraid().braid(SchemaBraidConfiguration.builder()
        .schemaSource(new GraphQLRemoteSchemaSource(
                SchemaNamespace.of("foo"),
                new HttpGraphQLRemoteRetriever(new URL("http://foo.com/graphql")),
                emptyList()))
        .schemaSource(new GraphQLRemoteSchemaSource(
                SchemaNamespace.of("bar"),
                new HttpGraphQLRemoteRetriever(new URL("http://bar.com/graphql")),
                emptyList()))
        .build());
        
// then per request...
GraphQL graphql = new GraphQL.Builder(braid.getSchema())
        .instrumentation(new DataLoaderDispatcherInstrumentation(braid.newDataLoaderRegistry()))
        .build();
```

### Using links

The other way Braid combines schemas is through links.  A link will connect a field of one data source to be resolved against another
data source.  This allows one data source to simply know about the unique identifier of an object, and another to know about its details,
yet to the consumer, they see an integrated schema.  

For example, let's assume there are two data sources: foo and bar.  'foo' has a type Foo that contains a 'bar' field that is a simple
string identifier.  The 'bar' data source contains all the information about the 'Bar' type.  The user will just see a single schema, 
and be able to query both foo and bar data in one go:

```
query {
  foo {
    title
    bar {
      name
    }
  }
}  
```

Under the covers, Braid actually makes this query to foo:

```
query {
  foo {
    title
    bar
  }
}
```

and this query to bar:

```
query {
  bar(id:$barId) {
    name
  }
}
```

The usage of Braid to make this happen is:

```
#!java
Braid braid = new SchemaBraid().braid(SchemaBraidConfiguration.builder()
        .schemaSource(new GraphQLRemoteSchemaSource(
                SchemaNamespace.of("foo"),
                new HttpGraphQLRemoteRetriever(new URL("http://foo.com/graphql")),
                singletonList(
                        Link.from(SchemaNamespace.of("foo"), "Foo", "bar")
                            .to(SchemaNamespace.of("bar"), "Bar"))))
        .schemaSource(new GraphQLRemoteSchemaSource(
                SchemaNamespace.of("bar"),
                new HttpGraphQLRemoteRetriever(new URL("http://bar.com/graphql")),
                emptyList()))
        .build());
```

Braid currently only supports top-level query aggregation and simple links, but future work to support more link types is expected.

### YAML configuration ###

A REST or GraphQL schema source can also be configured via a YAML file.  To create the YAML-based schema source, first
create a YAML file containing your configuration.  This file configures a REST source out of a Bitbucket followers 
endpoint:

```
#!yaml
name: bb-followers
rootFields:
  followers: 
    uri: https://api.bitbucket.org/2.0/users/{username}/followers
    responseMapping:
      - key: nodes
        op: copyList
        mapper:
          - key: display_name 
            op: copy
            target: name
schema: |
  schema {
    query: Query
  }
  type Query {
    followers(username: String) : FollowersSet
  } 
  type FollowersSet {
    nodes : [Follower]
  }
  type Follower {
    name: String
  }
```

This configuration file contains two primary sections: the schema and root field mappings.  The schema specifies the 
GraphQL schema this source will expose. The "rootFields" section contains a list of root fields and instructions for 
mapping the response of the URI to the desired GraphQL structure.

The YAML configuration file supports the definition of links as well as more complex sets of mapping instructions.
For examples, see YamlMapperTest.

Finally, create this schema source in Java via:

```
#!java
SchemaSource source = YamlRemoteSchemaSourceFactory
        .createRestSource(yamlReader, new HttpRestRemoteRetriever());
```

### GraphQL mapping ###

The YAML configuration also allows to configure GraphQL mapping based on types, for schema source based on
`com.atlassian.braid.source.QueryExecutorSchemaSource`:

```
#!yaml
- name: foo
    schema: |
      schema {
        query: Query
      }
      type Query {
        followers(username: String) : FollowersSet
      } 
      type FollowersSet {
        nodes : [Follower]
      }
      type Follower {
        name: String
        type: String
      }
    mapper:
      # mapping the type `Follower`
      - type: Follower 
        operations:
          # this means the `name` will be queried as `displayName` to the backend
          - key: name
            op: copy
            target: displayName 
          
          # allows easy mocking of field that might not be implemented by the backend (yet)
          - key: type
            op: put
            value: user 
          
          # keep all other fields
          - key: '*'
            op: copy 
```

### Add to your project ###

#### Gradle ####

```
#!groovy
dependencies {
  compile "com.atlassian.braid:graphql-braid:x.y.z"
  ...
}

```
#### Maven ####

```
#!xml
<dependency>
    <groupId>com.atlassian.braid</groupId>
    <artifactId>graphql-braid</artifactId>
    <version>x.y.z</version>
</dependency>
```

See [the changelog](CHANGES.md) for the changes in each release.

## Developing

Run a build and test:

```bash
maven install
```

To release, run the 'release' pipeline on the master branch in Bitbucket pipelines.

### Discussing

Meet us in the "CC Team B" on Stride.

