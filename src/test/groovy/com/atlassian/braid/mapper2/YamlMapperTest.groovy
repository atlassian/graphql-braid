package com.atlassian.braid.mapper2

import org.junit.Test

import static com.atlassian.braid.mapper2.NewMapper.fromYaml
import static org.assertj.core.api.AssertionsForClassTypes.assertThat

class YamlMapperTest {

    @Test
    void copy() {
        def yaml = '''
foo: 
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
foo: "copy"
bar: "copy"
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
foo: 
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
foo: 
  op: "copyList"
  target: "fooz"
  mapper:
    bar: 
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
foo: 
  op: "copyList"
  mapper:
    bar: 
     op: "copyList"
     mapper:
        baz: "copy" 
    jim: 
     op: "copyList"
     mapper:
        sara: "copy"     
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
foo: 
  op: "singletonList"
  mapper:
    bar: 
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
foo: 
  op: "map"
  mapper:
    bar: 
     op: "copy"
     target: "baz"
'''
        def input = ["bar": "blah2"]

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