package demo.consumer;

import demo.event.UpdateItem;
import demo.exception.RetryableMessagingException;
import demo.mapper.JsonMapper;
import demo.service.ItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.FixedDelayStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UpdateItemConsumer {

    private final ItemService itemService;
    public final String retryIntervalMilliseconds;
    public final String retryBackoffMultiplier;
    public final String maxRetryDurationMilliseconds;
    public final String maxRetryAttempts;
    public final String autoCreateRetryTopics;

    public UpdateItemConsumer(@Autowired ItemService itemService,
                              @Value("${demo.retry.retryIntervalMilliseconds}") Long retryIntervalMilliseconds,
                              @Value("${demo.retry.retryBackoffMultiplier}") Long retryBackoffMultiplier,
                              @Value("${demo.retry.maxRetryDurationMilliseconds}") Long maxRetryDurationMilliseconds,
                              @Value("${demo.retry.maxRetryAttempts}") Long maxRetryAttempts,
                              @Value("${demo.retry.autoCreateRetryTopics}") Boolean autoCreateRetryTopics) {
        this.itemService = itemService;
        this.retryIntervalMilliseconds = String.valueOf(retryIntervalMilliseconds);
        this.retryBackoffMultiplier = String.valueOf(retryBackoffMultiplier);
        this.maxRetryDurationMilliseconds = String.valueOf(maxRetryDurationMilliseconds);
        this.maxRetryAttempts = String.valueOf(maxRetryAttempts);
        this.autoCreateRetryTopics = String.valueOf(autoCreateRetryTopics);
    }

    @RetryableTopic(
            attempts = "#{updateItemConsumer.maxRetryAttempts}",
            autoCreateTopics = "#{updateItemConsumer.autoCreateRetryTopics}",
            backoff = @Backoff(delayExpression = "#{updateItemConsumer.retryIntervalMilliseconds}", multiplierExpression = "#{updateItemConsumer.retryBackoffMultiplier}"),
            fixedDelayTopicStrategy = FixedDelayStrategy.MULTIPLE_TOPICS,
            include = {RetryableMessagingException.class},
            timeout = "#{updateItemConsumer.maxRetryDurationMilliseconds}",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = "update-item", containerFactory = "kafkaListenerContainerFactory")
    public void listen(@Payload final String payload) {
        log.info("Update Item Consumer: Received message with payload: " + payload);
        try {
            UpdateItem event = JsonMapper.readFromJson(payload, UpdateItem.class);
            itemService.updateItem(event);
        } catch (RetryableMessagingException e) {
            // Ensure the message is retried.
            throw e;
        } catch (Exception e) {
            log.error("Update item - error processing message: " + e.getMessage());
        }
    }

    @DltHandler
    public void dlt(String data, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Event from topic "+topic+" is dead lettered - event:" + data);
    }
}
