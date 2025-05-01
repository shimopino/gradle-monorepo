package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.example.common.Person;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SqsHandler implements RequestHandler<SQSEvent, Void> {

  private static final Logger LOG = LogManager.getLogger(SqsHandler.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ParameterNamesModule());

  @Override
  public Void handleRequest(SQSEvent event, Context context) {
    event
        .getRecords()
        .forEach(
            rec -> {
              try {
                Person p = MAPPER.readValue(rec.getBody(), Person.class);
                LOG.info("Received person: {}", p);
              } catch (Exception e) {
                LOG.error("Failed to parse: {}", rec.getBody(), e);
              }
            });
    return null;
  }
}
