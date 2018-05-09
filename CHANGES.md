Changelog for graphql-braid
===============================

(Unreleased)
-------------------

- Change default property DataFetcher to be aware of aliases when source is a Map

0.10.6 (2018-05-08)
-------------------

- Fix support for array arguments for top level fields

0.10.5 (2018-05-04)
-------------------

- Use null-safe `toMap` to collect results 

0.10.4 (2018-05-04)
-------------------

- Updated Javadoc around RestRemoteSchemaSource and related classes, added a test for RestRemoteSchemaSource
- Changed signature of RestRemoteRetriever to reflect the fact that the Context is wrapped in a BraidContext
- Handle requests with multiple named queries and a given operation name 

0.10.3 (2018-04-24)
-------------------

- Handle inline fragments within fragments 

0.10.2 (2018-04-23)
-------------------

- Handle interfaces in fragments 

0.10.1 (2018-04-17)
-------------------
 
- Handle fragments in document mapping

0.10.0 (2018-04-12)
-------------------

- Updates the Braid building and runtime configurations, see the README for more details  
- Support GraphQL fragments when using document mapping


0.9.1 (2018-04-09)
-------------------

- Update GraphQL Java to version 8.0 (from 7.0)
- Update Java Dataloader to version 2.0.2 (from 2.0.1)
- Add new LazyRecursiveDataLoaderDispatcherInstrumentation that optimises batch loading

0.9.0 (2018-03-29)
-------------------

- Add experimental document mapper 
- Remove non-production-worthy HTTP retriever implementations
- Move YAML source loading to its own package

0.8.2 (2018-03-21)
-------------------

- Fix support for not null list resolution

0.8.1 (2018-03-15)
-------------------

- Fix detection of source type if query name is different

0.8.0 (2018-03-15)
-------------------

- Added support for resolving lists of ids
- Added support for non-string ids
- Added support for linking top level fields

0.7.6 (2018-03-15)
-------------------

- Remove `BraidDataLoaderDispatcherInstrumentation` introduced in 0.7.5 

0.7.5 (2018-03-14)
-------------------

- Improved batching when querying linked sources

0.7.4 (2018-03-13)
-------------------

- Do not query linked schema when fields are all query variables  

0.7.3 (2018-03-09)
-------------------

-

0.7.3 (2018-03-09)
-------------------
- Added Link.LinkBuiler.replaceFromField() and Link.isReplaceFromField()

0.7.2 (2018-03-09)
-------------------

- Add mapper `list` and `map` function with predicates

0.7.1 (2018-03-05)
-------------------

- New 'nullable' property on a link to signify whether a link handles nulls or not.  Default is not, which
  is different than the old default of fetching the data from the link target for a null key value.

0.7.0 (2018-02-27)
-------------------

- Breaking change: renamed many schema sources, including RemoteSchemaSource
- Add REST schema source (RestRemoteSchemaSource) for exposing REST resources as GraphQL fields
- Add YAML configuration (YamlRemoteSchemaSourceFactory) for creating REST or GraphQL schema sources
- Add new data mapper library for converting from one map structure into another
- Add support for mutations, with:
   - input object containing variables references
   - weaving of mutation result with other schema

0.6.0 (2018-01-25)
-------------------

- Upgrade to graphql-java 7.0
- SchemaSource instances can now construct their own BatchLoaders.  Useful for local source
  instances that want to load the entities from id in non-graphql ways.
- Ability for a non-exposed fields in a schema source to be the target of a link
- Make the original GraphQL query context available to local schema source
  executions

0.5.0 (2018-01-18)
-------------------

- Add ability to link via a different field on the source type

0.4.9 (2018-01-08)
-------------------

- Fix generics with configuration

0.4.8 (2017-12-11)
-------------------

- Fix missing interfaces when import a schema from an introspection result

0.4.7 (2017-12-08)
-------------------

- Fix broken handling of interfaces in a query

0.4.6 (2017-12-04)
-------------------

- Fix broken __typename support

0.4.5 (2017-11-28)
-------------------

- Fix incorrect/missing query operation name

0.4.4 (2017-11-28)
-------------------

- Fix to not mutate query document fragments

0.4.3 (2017-11-28)
-------------------

-

0.4.2 (2017-11-28)
-------------------

-

0.4.1 (2017-11-28)
-------------------

- Fix release process

0.4.0 (2017-11-28)
-------------------

- Support multiple use of fragments
- Support multiple variables in different areas of a query
- New config builder
- Clean up functional tests

0.3.1 (2017-11-20)
-------------------

- Fix missing deployment artifact

0.3.0 (2017-11-20)
-------------------

- Initial release




