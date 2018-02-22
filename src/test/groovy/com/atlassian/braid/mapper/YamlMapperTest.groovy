package com.atlassian.braid.mapper

import org.junit.*

import static org.assertj.core.api.Assertions.assertThat

class YamlMapperTest {

    @Test
    void simple() {
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

        assertMapping(yaml, input, output)
    }

    @Test
    void simpleAsString() {
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

        assertMapping(yaml, input, output)
    }

    @Test
    void list() {
        def yaml = '''
foo: 
  op: "copyList"
  elements:
    baz: 
     op: "copy"
     source: "bar"
'''
        def input = [
                "foo": [
                    ["bar": "blah2"],
                    ["bar": "jim"]

                ]
        ]

        def output = [
                "foo": [["baz": "blah2"], ["baz": "jim"]]
        ]

        assertMapping(yaml, input, output)
    }

    @Test
    void copyMap() {
        def yaml = '''
foo: 
  op: "copyMap"
  elements:
    baz: 
     op: "copy"
     source: "bar"
'''
        def input = [
                "foo": ["bar": "blah2"]
        ]

        def output = [
                "foo": ["baz": "blah2"]
        ]

        assertMapping(yaml, input, output)
    }

    @Test
    void nestedList() {
        def yaml = '''
foo: 
  op: "copyList"
  elements:
    bar: 
     op: "copyList"
     elements:
        baz: "copy" 
    jim: 
     op: "copyList"
     elements:
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

        assertMapping(yaml, input, output)
    }

    @Test
    void singleList() {
        def yaml = '''
foo: 
  op: "singletonList"
  elements:
    baz: 
     op: "copy"
     source: "bar"
'''
        def input = ["bar": "blah2"]

        def output = [
                "foo": [["baz": "blah2"]]
        ]

        assertMapping(yaml, input, output)
    }

    @Test
    void map() {
        def yaml = '''
foo: 
  op: "map"
  elements:
    baz: 
     op: "copy"
     source: "bar"
'''
        def input = ["bar": "blah2"]

        def output = [
                "foo": ["baz": "blah2"]
        ]

        assertMapping(yaml, input, output)
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

        assertMapping(yaml, input, output)
    }

    private static void assertMapping(String yaml, Map<String, String> input, Map<String, String> output) {
        def result = new YamlMapper(new StringReader(yaml)).map(input)
        assertThat(result).isEqualTo(output)
    }
}