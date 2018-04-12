package com.atlassian.braid

import com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceFactory
import org.junit.Ignore
import org.junit.Test

import static graphql.ExecutionInput.newExecutionInput
import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson
import static java.util.Collections.emptyMap

class YamlMultipleServicesTest {

    @Test
    @Ignore
    void test() {

        def bbCommitsYaml = '''
name: bb-commits
rootFields:
  commits: 
    uri: https://api.bitbucket.org/2.0/repositories/{username}/{slug}/commits
    responseMapping:
      nodes: 
        op: copyList
        source: values
        elements:
          message: copy
          author:
            op: copyMap
            source: "['author']['user']"
            elements:
              name: 
                op: copy
                source: display_name
              username: copy
links:
 - from:
      type: Author
      field: followers
      fromField: username
   to:
      namespace: bb-followers
      type: FollowersSet
      field: followers
      argument: username
schema: |
  schema {
    query: Query
  }
  type Query {
    commits(username: String,
            slug: String) : CommitSet
  } 
  type CommitSet {
    nodes : [Commit]
  }
  type Commit {
    message: String
    author: Author
  }
  type Author {
    name: String
    username: String
  }          
'''

        def bbFollowersYaml = '''
name: bb-followers
rootFields:
  followers: 
    uri: https://api.bitbucket.org/2.0/users/{username}/followers
    responseMapping:
      nodes: 
        op: copyList
        source: values
        elements:
          name: 
            op: copy
            source: display_name
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
'''


        def bbCommits = YamlRemoteSchemaSourceFactory.createRestSource(new StringReader(bbCommitsYaml))
        def bbFollowers = YamlRemoteSchemaSourceFactory.createRestSource(new StringReader(bbFollowersYaml))


        def braid = Braid.builder()
                .schemaSource(bbCommits)
                .schemaSource(bbFollowers)
                .build()

        def query = '''
{ 
  commits(username:"atlassian", slug:"asap-java") { 
    nodes { 
      message 
      author {
       name, 
       followers { 
         nodes { 
           name 
         } } } } } }
'''

        def result = braid.newGraphQL().execute(newExecutionInput().query(query).build()).join()
        assert result.errors == new ArrayList()
        println(prettyPrint(toJson(result.data)))
    }
}