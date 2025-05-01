package com.example.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.List;
import java.util.stream.Collectors;

/** JSON 文字列を指定された型に安全にパースするユーティリティクラス。 */
public class SafeJsonParser {

  // ObjectMapper はスレッドセーフなので static final として再利用
  private static final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  /**
   * JSON を指定型に変換します。失敗時は JsonParseException を投げます。
   *
   * @param json パース対象の JSON 文字列
   * @param targetType パース先のクラス
   * @param <T> 返却型
   * @return パース成功時のインスタンス
   * @throws JsonParseException パース失敗時に発生
   */
  public static <T> T parse(String json, Class<T> targetType) throws JsonParseException {
    if (json == null || json.isBlank()) {
      throw new JsonParseException("Input JSON cannot be null or blank.", null);
    }
    if (targetType == null) {
      throw new JsonParseException("Target type cannot be null.", null);
    }

    try {
      return objectMapper.readValue(json, targetType);

    } catch (InvalidFormatException e) {
      String fieldPath =
          e.getPath().stream()
              .map(
                  ref ->
                      ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
              .collect(Collectors.joining("."));
      String message =
          String.format(
              "Invalid format for field '%s'. Expected type: %s, but received value: '%s'",
              fieldPath, e.getTargetType().getSimpleName(), e.getValue());
      throw new JsonParseException(List.of(new ErrorDetail(fieldPath, message)));

    } catch (MismatchedInputException e) {
      String fieldPath =
          e.getPath().stream()
              .map(
                  ref ->
                      ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
              .collect(Collectors.joining("."));
      String message =
          String.format(
              "Mismatched input for field '%s'. Problem: %s", fieldPath, e.getOriginalMessage());
      if (e.getLocation() != null) {
        message +=
            String.format(
                " (at line %d, column %d)",
                e.getLocation().getLineNr(), e.getLocation().getColumnNr());
      }
      throw new JsonParseException(List.of(new ErrorDetail(fieldPath, message)));

    } catch (JsonProcessingException e) {
      String message = "JSON parsing failed: " + e.getOriginalMessage();
      if (e.getLocation() != null) {
        message +=
            String.format(
                " (at line %d, column %d)",
                e.getLocation().getLineNr(), e.getLocation().getColumnNr());
      }
      throw new JsonParseException(message, e);

    } catch (Exception e) {
      throw new JsonParseException(
          "An unexpected error occurred during JSON parsing: " + e.getMessage(), e);
    }
  }
}
