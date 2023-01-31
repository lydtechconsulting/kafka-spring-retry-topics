package demo.consumer;

import java.util.concurrent.atomic.AtomicInteger;

import demo.event.CreateItem;
import demo.mapper.JsonMapper;
import demo.service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class CreateItemConsumer {

    final AtomicInteger counter = new AtomicInteger();
    final ItemService itemService;

    @KafkaListener(topics = "create-item", containerFactory = "kafkaListenerContainerFactory")
    public void listen(@Payload final String payload) {
        counter.getAndIncrement();
        log.info("Create Item Consumer: Received message [" +counter.get()+ "] - payload: " + payload);
        try {
            CreateItem event = JsonMapper.readFromJson(payload, CreateItem.class);
            itemService.createItem(event);
        } catch (Exception e) {
            log.error("Create item - error processing message: " + e.getMessage());
        }
    }
}
