# Kafka Spring Boot Retry Topics Demo

Spring Boot application demonstrating Kafka non-blocking retry with Spring retry topics.

If an event is received by an application that is not ready to process it, it can be sent to a retry topic ensuring the original topic is not blocked.  If this retry fails, it will be sent to a further retry topic, and so on until the event either completes processing or is discarded.  This ensures that each retry topic remains unblocked.  The cost of this is the loss of guaranteed ordering, as while the event is being retried an event received after it may be processed successfully.

Spring automatically manages the retry topics based on a naming convention, e.g. `update-item-retry-0` and dead letter topic `update-item-dlt`.  Spring creates a consumer instance for each retry topic, so the consumed retrying events are not mixed in the batches of the original events, but of course the processing logic is that of the implemented consumer.  

Spring's `@DltHandler` annotation is used to mark the method within the same class as the `@KafkaListener` that is responsible for consuming the message from the dead letter topic.

While the application demonstrates using multiple retry topics, one for each retry, it is possible to have a single retry topic that deals with all retries.  Simply change the `@Retryable` annotation parameter `fixedDelayTopicStrategy` from `FixedDelayStrategy.MULTIPLE_TOPICS` to `FixedDelayStrategy.SINGLE_TOPIC`. 

This application demonstrates this retry pattern using a `create-item` event that creates an item in the database, and is updated with an `update-item` event.  If the `update-item` event is received before the `create-item` event it may be required to delay and retry this update after a period of time to allow for the corresponding `create-item` event to arrive and be processed.  When related events are originating in bulk from external systems it may well be the case that such events arrive out of order by the time they hit a downstream service.  This pattern therefore caters for such a scenario as the `update-item` event can be safely retried until the item is eventually created by the `create-item` event, at which point the update can be applied.  

This repo accompanies the article [Kafka Consumer Non-Blocking Retry: Spring Retry Topics](https://www.lydtechconsulting.com/blog-kafka-spring-retry-topics.html).

## Configuration

The retry topics are configured using Spring's `@Retryable` annotation.  
```
@RetryableTopic(
        attempts = "#{'${demo.retry.maxRetryAttempts}'}",
        autoCreateTopics = "#{'${demo.retry.autoCreateRetryTopics}'}",
        backoff = @Backoff(delayExpression = "#{'${demo.retry.retryIntervalMilliseconds}'}", multiplierExpression = "#{'${demo.retry.retryBackoffMultiplier}'}"),
        fixedDelayTopicStrategy = FixedDelayStrategy.MULTIPLE_TOPICS,
        include = {RetryableMessagingException.class},
        timeout = "#{'${demo.retry.maxRetryDurationMilliseconds}'}",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
```

The following configuration parameters defined in the `src/main/resources/application.yml` can then be applied:

|Property|Usage|Default|
|---|---|---|
|demo.retry.retryIntervalMilliseconds|The interval in seconds between retries| 10,000 milliseconds (10 seconds)|
|demo.retry.maxRetryDurationMilliseconds|The maximum duration an event should be retried before being discarded|60,000 milliseconds (1 minute)|
|demo.retry.retryBackoffMultiplier|The retry backoff multiplier|2|
|demo.retry.maxRetryAttempts|The maximum number of retry attempts to perform before dead lettering the event|4|

Whichever of the two limits `maxRetryDurationMilliseconds` and `maxRetryAttempts` is reached first determines when no more retries will take place, and the event is sent to the dead letter topic.

## Build

Build with Java 17.

```
mvn clean install
```

## Integration Tests

The integration tests run as part of the maven `test` target (during the `install`).

Configuration for the test is taken from the `src/test/resources/application-test.yml`, overriding the default values listed above.

The tests demonstrate sending events to an embedded in-memory Kafka that are consumed by the application.  `create-item` events result in an item being persisted in the database.  `update-item` events update the corresponding item if it is present in the database.  The tests demonstrate that if the item is not found it is retried via the retry topics.

## Run Spring Boot Application

### Run docker containers

From root dir run the following to start dockerised Kafka, Zookeeper, and Conduktor Platform:
```
docker-compose up -d
```

### Start demo spring boot application
```
java -jar target/kafka-spring-retry-topics-1.0.0.jar
```

### Produce create and update item command events:

Jump onto Kafka docker container:
```
docker exec -ti kafka bash
```

Produce a message to the `create-item` topic:
```
kafka-console-producer \
--topic create-item \
--broker-list kafka:29092 
```
Now enter the message to create the item (with a UUID and name String):
```
{"id": "b346d83e-f2db-4427-947d-3e239111d6db", "name": "my-new-item"}
```

Retrieve the item status via the REST API, confirming it is `NEW`:
```
curl -X GET http://localhost:9001/v1/demo/items/b346d83e-f2db-4427-947d-3e239111d6db/status
```

Produce a message to the `update-item` topic:
```
kafka-console-producer \
--topic update-item \
--broker-list kafka:29092 
```

Enter the message to update the item. The status can be one of `ACTIVE` or `CANCELLED`
```
{"id": "b346d83e-f2db-4427-947d-3e239111d6db", "status": "ACTIVE"}
```

Retrieve the updated item status via the REST API, confirming it is now `ACTIVE`:
```
curl -X GET http://localhost:9001/v1/demo/items/b346d83e-f2db-4427-947d-3e239111d6db/status
```

### Exercise the retry with out of order events:

Submit an `update-item` first (with a different UUID, and status of `ACTIVE` or `CANCELLED`).  Observe that no item status is returned from the `curl` statement.  If a `create-item` event with this same itemId is submitted before the `maxRetryDurationSeconds` threshold is exceeded (as defined in `application.yml`), then the item will be created, and the retrying `update-item` event will transition the status to `ACTIVE` or `CANCELLED`.  If the threshold is exceeded then the status of the created item will remain at `NEW`.

### View the retry topics and events in Conduktor:

Log in to Conduktor at `http://localhost:8080` with credentials: `admin@conduktor.io` / `admin`

### Docker clean up

Manual clean up:
```
docker rm -f $(docker ps -aq)
```
Further docker clean up if necessary:
```
docker system prune
docker volume prune
```
