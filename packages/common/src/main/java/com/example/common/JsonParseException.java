package com.example.common;

import java.util.List;
import java.util.stream.Collectors;

/** JSONパース失敗時に投げられるチェック例外。 内部のJackson例外はcauseとして保持し、利用者にはErrorDetailだけを公開します。 */
public class JsonParseException extends Exception {
  private final List<ErrorDetail> errors;

  /**
   * エラー詳細リストを指定して例外を生成します。
   *
   * @param errors エラー詳細のリスト
   */
  public JsonParseException(List<ErrorDetail> errors) {
    super(errors.stream().map(ErrorDetail::message).collect(Collectors.joining("; ")));
    this.errors = errors;
  }

  /**
   * メッセージと原因となった例外を指定して例外を生成します。
   *
   * @param message エラーメッセージ
   * @param cause 原因例外
   */
  public JsonParseException(String message, Throwable cause) {
    super(message, cause);
    this.errors = List.of(new ErrorDetail(message));
  }

  /**
   * エラー詳細リストを取得します。
   *
   * @return エラー詳細のリスト
   */
  public List<ErrorDetail> getErrors() {
    return errors;
  }
}
