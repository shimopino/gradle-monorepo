package com.example.lambdatest;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.lambda.model.State;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

@Testcontainers
public class SqsHandlerIntegrationTest {
  private static final Logger CONTAINER_LOG = LoggerFactory.getLogger("LocalStack");

  @Container
  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
          .withEnv("LAMBDA_EXECUTOR", "local")
          // .withEnv("DEBUG", "1")
          .withServices(LocalStackContainer.Service.LAMBDA, LocalStackContainer.Service.SQS)
          .withLogConsumer(new Slf4jLogConsumer(CONTAINER_LOG));

  @Test
  void testSqsHandlerInvocation() throws Exception {
    // SQSクライアントの準備（LocalStack）
    SqsClient sqs =
        SqsClient.builder()
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), localstack.getSecretKey())))
            .build();
    // ターゲットキューを作成
    String targetQueueUrl = sqs.createQueue(cq -> cq.queueName("target-queue")).queueUrl();
    System.out.println("[Test] Created target queue: " + targetQueueUrl);

    try (LambdaClient lambda =
        LambdaClient.builder()
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.LAMBDA))
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), localstack.getSecretKey())))
            .build()) {
      // Lambda 関数のデプロイ
      String jarPath = System.getProperty("lambda.jar.path");
      Path jar = Paths.get(jarPath);
      byte[] bytes = Files.readAllBytes(jar);
      lambda.createFunction(
          cf ->
              cf.functionName("SqsHandlerTest")
                  .runtime(Runtime.JAVA21)
                  .handler("com.example.lambdatest.SqsHandler::handleRequest")
                  .role("arn:aws:iam::000000000000:role/lambda-ex")
                  .code(c -> c.zipFile(SdkBytes.fromByteArray(bytes)))
                  // SqsHandler で参照する環境変数を設定
                  .environment(
                      env ->
                          env.variables(
                              Map.of(
                                  "TARGET_QUEUE_URL",
                                  targetQueueUrl,
                                  "AWS_LAMBDA_LOG_FORMAT",
                                  "TEXT"))));
      System.out.println("[Test] Lambda function 'SqsHandlerTest' deployment requested.");

      // 関数が Active 状態になるまで待機
      System.out.println("[Test] Waiting for function to become ACTIVE...");
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofMillis(500))
          .until(
              () -> {
                GetFunctionResponse cfg = lambda.getFunction(r -> r.functionName("SqsHandlerTest"));
                return cfg.configuration().state() == State.ACTIVE;
              });
      System.out.println("[Test] Lambda function 'SqsHandlerTest' is now ACTIVE.");

      // テスト用 SQSEvent ペイロードを生成
      String payload =
          "{ \"Records\": [ { \"body\": \"{\\\"name\\\":\\\"Alice\\\",\\\"age\\\":30}\" } ] }";
      System.out.println("[Test] Payload prepared: " + payload);

      // Lambda を直接 invoke してハンドラ処理を検証
      System.out.println("[Test] Invoking Lambda function 'SqsHandlerTest'...");
      InvokeResponse resp =
          lambda.invoke(
              ii -> ii.functionName("SqsHandlerTest").payload(SdkBytes.fromUtf8String(payload)));
      System.out.println(
          "[Test] InvokeResponse status=" + resp.statusCode() + ", error=" + resp.functionError());

      assertEquals(200, resp.statusCode());

      String result = resp.payload().asUtf8String();
      System.out.println("[Test] Result: " + result);

      assertNull(resp.functionError());
    }
    // フォワードされたメッセージを検証
    System.out.println("[Test] Checking forwarded message in target queue...");
    List<Message> messages =
        sqs.receiveMessage(r -> r.queueUrl(targetQueueUrl).maxNumberOfMessages(1)).messages();
    assertEquals(1, messages.size());
    assertEquals("{\"name\":\"Alice\",\"age\":30}", messages.get(0).body());
    System.out.println("[Test] Forwarded message received: " + messages.get(0).body());
  }
}
