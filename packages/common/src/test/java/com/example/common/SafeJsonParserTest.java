package com.example.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SafeJsonParserTest {

  // テスト用レコードクラス
  public record TestRecord(String name, int age) {}

  @Test
  @DisplayName("有効なJSON文字列をパースしてオブジェクトが返る")
  void parse_validJson_shouldReturnObject() {
    String json = """
                {"name":"Taro","age":30}
                """;
    TestRecord tr = assertDoesNotThrow(() -> SafeJsonParser.parse(json, TestRecord.class));
    assertEquals("Taro", tr.name());
    assertEquals(30, tr.age());
  }

  @Test
  @DisplayName("nullまたは空文字をパースするとJsonParseExceptionが投げられる")
  void parse_nullOrBlankJson_shouldThrowJsonParseException() {
    // null の場合
    JsonParseException e1 =
        assertThrows(JsonParseException.class, () -> SafeJsonParser.parse(null, TestRecord.class));
    List<ErrorDetail> errors1 = e1.getErrors();
    assertEquals(1, errors1.size());
    assertEquals("Input JSON cannot be null or blank.", errors1.get(0).message());

    // 空文字の場合
    JsonParseException e2 =
        assertThrows(JsonParseException.class, () -> SafeJsonParser.parse("   ", TestRecord.class));
    List<ErrorDetail> errors2 = e2.getErrors();
    assertEquals(1, errors2.size());
    assertEquals("Input JSON cannot be null or blank.", errors2.get(0).message());
  }

  @Test
  @DisplayName("ターゲット型がnullの場合はJsonParseExceptionが投げられる")
  void parse_nullTargetType_shouldThrowJsonParseException() {
    JsonParseException e =
        assertThrows(JsonParseException.class, () -> SafeJsonParser.parse("{}", null));
    List<ErrorDetail> errors = e.getErrors();
    assertEquals(1, errors.size());
    assertEquals("Target type cannot be null.", errors.get(0).message());
  }

  @Test
  @DisplayName("型不一致のJSONをパースするとJsonParseExceptionが投げられ、フィールド名がageとなる")
  void parse_invalidFormat_shouldThrowJsonParseException() {
    String json = """
                {"name":"Taro","age":"notAnInt"}
                """;
    JsonParseException e =
        assertThrows(JsonParseException.class, () -> SafeJsonParser.parse(json, TestRecord.class));
    List<ErrorDetail> errors = e.getErrors();
    assertEquals(1, errors.size());
    ErrorDetail ed = errors.get(0);
    assertEquals("age", ed.field());
    assertTrue(ed.message().contains("Invalid format for field 'age'"));
  }

  @Test
  @DisplayName("不正なJSON構文をパースするとJsonParseExceptionが投げられる")
  void parse_syntaxError_shouldThrowJsonParseException() {
    String json = """
                {"name":"Taro",
                """;
    JsonParseException e =
        assertThrows(JsonParseException.class, () -> SafeJsonParser.parse(json, TestRecord.class));
    List<ErrorDetail> errors = e.getErrors();
    assertEquals(1, errors.size());
    ErrorDetail ed = errors.get(0);
    assertTrue(ed.message().startsWith("JSON parsing failed:"));
  }
}
