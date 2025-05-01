package com.example.lambdatest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@ExtendWith(MockitoExtension.class)
public class SqsHandlerTest {

  @Mock private SqsClient mockSqsClient;
  private static final String TARGET_URL = "https://dummy-queue";

  private SqsHandler handler;

  @BeforeEach
  void setUp() {
    handler = new SqsHandler(mockSqsClient, TARGET_URL);
  }

  @Test
  @DisplayName("Valid JSON を含む SQSEvent で sendMessage が呼ばれること")
  void testHandleRequest_validJson_sendCalled() {
    String json =
        """
            {
                "name": "Alice",
                "age": 30
            }
            """;

    SQSEvent event = new SQSEvent();
    SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
    message.setBody(json);
    event.setRecords(Collections.singletonList(message));

    // Act
    handler.handleRequest(event, null);

    // Assert: sendMessage が正しいリクエストで呼ばれる
    ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
    verify(mockSqsClient).sendMessage(captor.capture());
    SendMessageRequest req = captor.getValue();
    assertEquals(TARGET_URL, req.queueUrl());
    assertEquals(json, req.messageBody());
  }

  @Test
  @DisplayName("Invalid JSON を含む SQSEvent で sendMessage が呼ばれないこと")
  void testHandleRequest_invalidJson_noSend() {
    SQSEvent event = new SQSEvent();
    SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
    message.setBody("invalid-json");
    event.setRecords(Collections.singletonList(message));

    // Act
    handler.handleRequest(event, null);

    // Assert: sendMessage が呼ばれない
    verify(mockSqsClient, never()).sendMessage(any(SendMessageRequest.class));
  }
}
