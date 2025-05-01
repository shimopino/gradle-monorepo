package com.example.common;

/**
 * エラー詳細を表すレコード。
 *
 * @param field フィールド名
 * @param message エラーメッセージ
 */
public record ErrorDetail(String field, String message) {
  /** メッセージのみを指定してエラー詳細を作成します。 */
  public ErrorDetail(String message) {
    this(null, message);
  }
}
