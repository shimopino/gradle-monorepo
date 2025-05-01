# Lambda Test パッケージ：Testcontainers + LocalStack 結合テスト実装計画

このドキュメントでは、`packages/lambda-test` モジュールで AWS Lambda（SQS 経由トリガー）を本番に近い形でローカル統合テストするために、Testcontainers と LocalStack を組み合わせた実装手順をまとめます。

---

## 1. 目的

- SQS イベントソースマッピングを持つ `SqsHandler` をローカルで起動し、実際にメッセージを送信して Lambda がハンドルする一連の流れを E2E テストとして検証する。
- 外部 AWS リソース（SQS キュー、IAM ロール、Lambda 関数実行環境）を LocalStack でエミュレーションし、Testcontainers でライフサイクル管理する。

## 2. 前提条件

- Java 17+ / Corretto 21
- Docker（Testcontainers 実行環境）
- Gradle 8.x
- LocalStack Docker イメージ（公式: `localstack/localstack:latest`）

## 3. 必要な依存関係

`packages/lambda-test/build.gradle.kts` に以下を追加します。

```kotlin
dependencies {
    // テストフレームワーク
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    // Testcontainers Core + JUnit5
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:core:1.19.3")
    // LocalStack モジュール
    testImplementation("org.testcontainers:localstack:1.19.3")
    // AWS SDK v2 (Lambda / SQS)
    implementation("software.amazon.awssdk:sqs:2.20.49")
    implementation("software.amazon.awssdk:lambda:2.20.49")
    // Awaitility（非同期検証用）
    testImplementation("org.awaitility:awaitility:4.2.0")
}
```

## 4. Build 設定

- `build.gradle.kts` の `application` プラグイン設定は不要（Lambda はハンドラ経由で起動）
- テスト実行時に LocalStack 用イメージをプルできるよう Docker の設定を確認

## 5. テスト実装の概要

1. **LocalStackContainer の起動**  
   Testcontainers の `@Container` で LocalStack コンテナを起動し、SQS/LAMBDA サービスを有効化します（`LocalStackContainer(Service.SQS, Service.LAMBDA)`）。

2. **動的プロパティ設定** (`@DynamicPropertySource`)  
   Spring プロパティ（または直書き設定）として、LocalStack のエンドポイント、リージョン、認証情報をテスト実行コンテナから取得し、AWS SDK クライアントに注入します。

3. **リソース作成** (`@BeforeAll`)

   - `awslocal` CLI をコンテナ内で実行し、SQS キューを作成
   - Lambda 関数用 IAM ロールは既存ダミー ARN を利用
   - `LambdaClient.createFunction(...)` でテスト用 JAR をアップロードし、SQS イベントソースマッピングを登録

4. **テスト実行**

   - `SqsClient.sendMessage(...)` でメッセージをキューに投げる
   - Awaitility でポーリングし、ハンドラ実行後の副作用（ログ出力、DLQ 挙動、メトリクスなど）を検証

5. **クリーンアップ**  
   Testcontainers がコンテナ停止・破棄を自動で処理。

## 6. テストコード構成例

- `src/test/java/com/example/lambda/SqsHandlerIntegrationTest.java`

  ```java
  @Testcontainers
  public class SqsHandlerIntegrationTest {

      @Container
      static LocalStackContainer localstack = new LocalStackContainer("localstack/localstack:latest")
          .withServices(SQS, LAMBDA);

      @BeforeAll
      static void setup() throws Exception {
          // キュー作成
          localstack.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", "test-queue");
          // ラムダ作成 & イベントソースマッピング
          Path jar = Paths.get("../lambda/build/libs/lambda.jar");
          byte[] bytes = Files.readAllBytes(jar);
          AWSLambda lambda = AWSLambdaClient.builder()
              .endpointOverride(localstack.getEndpointOverride(LAMBDA))
              .region(localstack.getRegion())
              .credentialsProvider(localstack.getDefaultCredentialsProvider())
              .build();
          lambda.createFunction(r -> r
              .functionName("SqsHandlerTest")
              .runtime(Runtime.JAVA11)
              .role("arn:aws:iam::000000000000:role/lambda-ex")
              .handler("com.example.lambda.SqsHandler::handleRequest")
              .code(c -> c.zipFile(SdkBytes.fromByteArray(bytes)))
          );
          lambda.createEventSourceMapping(r -> r
              .functionName("SqsHandlerTest")
              .batchSize(1)
              .eventSourceArn(localstack.getEndpointOverride(SQS).toString() + ":test-queue")
          );
      }

      @Test
      void testSqsTrigger() {
          SqsClient sqs = SqsClient.builder()
              .endpointOverride(localstack.getEndpointOverride(SQS))
              .region(localstack.getRegion())
              .credentialsProvider(localstack.getDefaultCredentialsProvider())
              .build();

          sqs.sendMessage(b -> b.queueUrl(localstack.getEndpointOverride(SQS) + "/000000000000/test-queue")
              .messageBody("{\"name\":\"TestUser\"}"));

          await().atMost(Duration.ofSeconds(10))
              .untilAsserted(() -> {
                  // ログや DLQ の検証、あるいはメトリクスチェックなど
              });
      }
  }
  ```

## 7. 実行方法

```bash
./gradlew :lambda-test:test
```

## 8. テスト実行ログサンプル

以下は `./gradlew :lambda-test:test` 実行時にコンソールに出力されるログの一例です。

```text
> ./gradlew :lambda-test:test

> Task :lambda-test:test
[Test] Lambda function 'SqsHandlerTest' deployment requested.
[Test] Waiting for function to become ACTIVE...
[Test] Lambda function 'SqsHandlerTest' is now ACTIVE.
[Test] Payload prepared: { "Records": [ { "body": "{\"name\":\"Alice\",\"age\":30}" } ] }
[Test] Invoking Lambda function 'SqsHandlerTest'...
[Test] InvokeResponse status=200, error=null
[Test] Result: null

# LocalStack コンテナ内の標準出力に流れるハンドラログ
12:34:56.789 [localstack-local] INFO  com.example.lambdatest.SqsHandler - Received person: Person[name=Alice, age=30]

BUILD SUCCESSFUL
```

---

### 参考リンク

- Testcontainers + LocalStack ガイド  
  https://testcontainers.com/guides/testing-aws-service-integrations-using-localstack/
- LocalStack & Testcontainers 統合テスト  
  https://hashnode.localstack.cloud/cloud-integration-testing-made-easy-localstack-testcontainers
