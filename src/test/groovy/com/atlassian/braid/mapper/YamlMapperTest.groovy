package com.atlassian.braid.mapper

import org.junit.*

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

        def result = new YamlMapper(new StringReader(yaml)).map(input)

        assertThat(output).isEqualTo(result);
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

        def result = new YamlMapper(new StringReader(yaml)).map(input)

        assert output == result
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

        def result = new YamlMapper(new StringReader(yaml)).map(input)

        assert output == result
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

        def result = new YamlMapper(new StringReader(yaml)).map(input)

        assert output == result
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

        def result = new YamlMapper(new StringReader(yaml)).map(input)

        assert output == result
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

        def result = new YamlMapper(new StringReader(yaml)).map(input)

        assert output == result
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

        def result = new YamlMapper(new StringReader(yaml)).map(input)

        assert output == result
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

        def result = new YamlMapper(new StringReader(yaml)).map(input)

        assert output == result
    }
}