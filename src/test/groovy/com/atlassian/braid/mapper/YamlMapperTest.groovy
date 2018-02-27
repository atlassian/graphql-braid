package com.atlassian.braid.mapper

import org.junit.Test

import static com.atlassian.braid.mapper.Mappers.fromYaml
import static org.assertj.core.api.AssertionsForClassTypes.assertThat

class YamlMapperTest {

    @Test
    void copy() {
        def yaml = '''
- key: "foo" 
  op: "copy"
'''
        def input = [
                "foo": "blah",
                "bar": "blah2"
        ]

        def output = [
                "foo": "blah",
        ]

        testYamlMapper(yaml, input, output)
    }

    @Test
    void copySimple() {
        def yaml = '''
- foo: "copy"
- bar: "copy"
'''
        def input = [
                "foo": "blah",
                "bar": "blah2"
        ]

        def output = [
                "foo": "blah",
                "bar": "blah2"
        ]

        testYamlMapper(yaml, input, output)
    }

    @Test
    void put() {
        def yaml = '''
- key: "foo" 
  op: "put"
  value: "bar"
'''
        def input = ["blah": "sd"]

        def output = [
                "foo": "bar"
        ]

        testYamlMapper(yaml, input, output)
    }

    @Test
    void copyList() {
        def yaml = '''
- key: "foo" 
  op: "copyList"
  target: "fooz"
  mapper:
    - key: "bar" 
      op: "copy"
      target: "baz"
'''
        def input = [
                "foo": [
                        ["bar": "blah2"],
                        ["bar": "jim"]
                ]
        ]

        def output = [
                "fooz": [["baz": "blah2"], ["baz": "jim"]]
        ]

        testYamlMapper(yaml, input, output)
    }

    @Test
    void nestedList() {
        def yaml = '''
- key: "foo" 
  op: "copyList"
  mapper:
    - key: "bar" 
      op: "copyList"
      mapper:
        - baz: "copy" 
    - key: "jim" 
      op: "copyList"
      mapper:
        - sara: "copy"     
'''
        def input = [
                "foo": [
                        ["bar": [
                                ["baz": "blah2"],
                        ],
                         "jim": [
                                 ["sara": "b"]
                         ]]
                ]
        ]

        def output = [
                "foo": [["bar": [["baz": "blah2"]], "jim": [["sara": "b"]]]]
        ]

        testYamlMapper(yaml, input, output)
    }

    @Test
    void singleList() {
        def yaml = '''
- key: "foo" 
  op: "list"
  mapper:
    - key: "bar" 
      op: "copy"
      target: "baz"
'''
        def input = ["bar": "blah2"]

        def output = [
                "foo": [["baz": "blah2"]]
        ]

        testYamlMapper(yaml, input, output)
    }

    @Test
    void map() {
        def yaml = '''
- key: "foo" 
  op: "map"
  mapper:
    - key: "bar" 
      op: "copy"
      target: "baz"
'''
        def input = ["bar": "blah2"]

        def output = [
                "foo": ["baz": "blah2"]
        ]

        testYamlMapper(yaml, input, output)
    }

    @Test
    void copyMap() {
        def yaml = '''
- key: "foo" 
  op: "copyMap"
  mapper:
    - key: "bar" 
      op: "copy"
      target: "baz"
'''
        def input = [
                "foo": ["bar": "blah2"]
        ]

        def output = [
                "foo": ["baz": "blah2"]
        ]

        testYamlMapper(yaml, input, output)
    }

    private static void testYamlMapper(String yaml, Map<String, Object> input, Map<String, Object> output) {
        def mapper = fromYaml { new StringReader(yaml) }
        assertThat(mapper.apply(input)).isEqualTo(output);
    }
}