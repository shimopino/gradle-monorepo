package com.example.lambdatest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class HelloWorldTest {

  @Test
  void helloWorldTest() {
    // シンプルなアサーションでテスト環境を動作確認
    assertEquals("Hello World", "Hello World");
  }
}
