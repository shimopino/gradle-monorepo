package com.example.lambdatest;

import java.net.URI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.example.common.Person;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class SqsHandler implements RequestHandler<SQSEvent, Void> {

  private static final Logger LOG = LogManager.getLogger(SqsHandler.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ParameterNamesModule());
  private final SqsClient sqsClient;
  private final String targetQueueUrl;

  /** デフォルトコンストラクタ：環境変数とデフォルトクライアントを使用 */
  public SqsHandler() {
    this(
        SqsClient.builder()
            .endpointOverride(URI.create(System.getenv("AWS_ENDPOINT_OVERRIDE_SQS")))
            .region(Region.of(System.getenv("AWS_DEFAULT_REGION")))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        System.getenv("AWS_ACCESS_KEY_ID"),
                        System.getenv("AWS_SECRET_ACCESS_KEY")))
            )
            .build(),
        System.getenv("TARGET_QUEUE_URL")
    );
  }

  /** テスト用コンストラクタ：クライアントとキューURLを外部から注入可能 */
  public SqsHandler(SqsClient sqsClient, String targetQueueUrl) {
    this.sqsClient = sqsClient;
    this.targetQueueUrl = targetQueueUrl;
  }

  @Override
  public Void handleRequest(SQSEvent event, Context context) {
    event
        .getRecords()
        .forEach(
            rec -> {
              try {
                Person p = MAPPER.readValue(rec.getBody(), Person.class);
                LOG.info("Received person: {}", p);
                SendMessageRequest sendReq =
                    SendMessageRequest.builder()
                        .queueUrl(targetQueueUrl)
                        .messageBody(rec.getBody())
                        .build();
                sqsClient.sendMessage(sendReq);
              } catch (Exception e) {
                LOG.error("Failed to parse: {}", rec.getBody(), e);
              }
            });
    return null;
  }
}
