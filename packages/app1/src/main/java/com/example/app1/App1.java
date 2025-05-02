package com.example.app1;

import com.example.common.Person;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

public class App1 {
  public static void main(String[] args) throws Exception {
    String json = """
                { "name": "Alice", "age": 30 }
                """;

    ObjectMapper mapper = new ObjectMapper().registerModule(new ParameterNamesModule());

    Person p = mapper.readValue(json, Person.class);
    System.out.println("Hello, " + p.name() + "! You are " + p.age() + " years old.");
  }
}
