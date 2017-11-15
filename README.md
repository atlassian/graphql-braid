# Welcome to graphql-braid #

Combines graphql backends into one schema

## Using

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

