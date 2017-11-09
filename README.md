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


## Developing

Run a build and test:

```bash
./gradlew build test
```

To release, tag and re-run the latest master build:

```bash
git tag x.y.z
git push origin x.y.z
```


### Discussing

Meet us in the "team-b" on HipChat.

