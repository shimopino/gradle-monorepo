package com.example.lambdatest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.lambda.model.State;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

@Testcontainers
public class SqsTriggerIntegrationTest {
  private static final Logger CONTAINER_LOG = LoggerFactory.getLogger("LocalStack");

  @Container
  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
          .withEnv("LAMBDA_EXECUTOR", "local")
          // .withEnv("DEBUG", "1")
          .withServices(LocalStackContainer.Service.LAMBDA, LocalStackContainer.Service.SQS)
          .withLogConsumer(new Slf4jLogConsumer(CONTAINER_LOG));

  @Test
  void testSqsEventSourceMapping() throws Exception {
    // SQSトリガーのテスト用クライアント準備
    SqsClient sqs = SqsClient.builder()
        .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
        .region(Region.of(localstack.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    localstack.getAccessKey(), localstack.getSecretKey())))
        .build();
    // ソースキューとターゲットキューを作成
    String sourceQueueUrl = sqs.createQueue(q -> q.queueName("source-queue")).queueUrl();
    String targetQueueUrl = sqs.createQueue(q -> q.queueName("target-queue")).queueUrl();
    // Lambdaクライアントで関数をデプロイ＆イベントソースマッピング登録
    try (LambdaClient lambda = LambdaClient.builder()
        .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.LAMBDA))
        .region(Region.of(localstack.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    localstack.getAccessKey(), localstack.getSecretKey())))
        .build()) {
      // 関数デプロイ
      String jarPath = System.getProperty("lambda.jar.path");
      System.out.println("[Test] jarPath: " + jarPath);
      Path jar = Paths.get(jarPath);
      byte[] bytes = Files.readAllBytes(jar);
      lambda.createFunction(cf -> cf
          .functionName("SqsHandlerTest")
          .runtime(Runtime.JAVA21)
          .handler("com.example.lambdatest.SqsHandler::handleRequest")
          .role("arn:aws:iam::000000000000:role/lambda-ex")
          .code(c -> c.zipFile(SdkBytes.fromByteArray(bytes)))
          .environment(env -> env.variables(
              Map.of(
                  "TARGET_QUEUE_URL", targetQueueUrl,
                  "AWS_LAMBDA_LOG_FORMAT", "TEXT",
                  "AWS_ENDPOINT_OVERRIDE_SQS", localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString(),
                  "AWS_DEFAULT_REGION", localstack.getRegion(),
                  "AWS_ACCESS_KEY_ID", "test",
                  "AWS_SECRET_ACCESS_KEY", "test"
              )
          ))
      );
      // イベントソースマッピング登録
      String sourceQueueArn = String.format("arn:aws:sqs:%s:000000000000:source-queue", localstack.getRegion());
      lambda.createEventSourceMapping(em -> em
          .functionName("SqsHandlerTest")
          .eventSourceArn(sourceQueueArn)
          .batchSize(1));
      // ACTIVEになるまで待機
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofMillis(500))
          .until(
              () -> lambda.getFunction(r -> r.functionName("SqsHandlerTest")).configuration().state() == State.ACTIVE);
    }
    // メッセージ送信でトリガーを発動
    sqs.sendMessage(sm -> sm.queueUrl(sourceQueueUrl).messageBody("{\"name\":\"Alice\",\"age\":30}"));
    // フォワード結果を待機・検証
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> {
          List<Message> messages = sqs.receiveMessage(r -> r
              .queueUrl(targetQueueUrl)
              .maxNumberOfMessages(1)
              .visibilityTimeout(1)
          ).messages();
          assertEquals(1, messages.size());
          assertEquals("{\"name\":\"Alice\",\"age\":30}", messages.get(0).body());
        });
  }
}
