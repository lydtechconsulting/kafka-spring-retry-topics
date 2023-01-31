package demo.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestKafkaClient {

    @Autowired
    private final KafkaTemplate kafkaTemplate;

    public SendResult sendMessage(String topic, String data) {
        try {
            final ProducerRecord<String, String> record = new ProducerRecord<>(topic, data);
            final SendResult result = (SendResult) kafkaTemplate.send(record).get();
            final RecordMetadata metadata = result.getRecordMetadata();

            log.info(String.format("Sent record(key=%s value=%s) meta(topic=%s, partition=%d, offset=%d)",
                    record.key(), record.value(), metadata.topic(), metadata.partition(), metadata.offset()));

            return result;
        } catch (Exception e) {
            String message = "Error sending message to topic " + topic;
            log.error(message);
            throw new RuntimeException(message, e);
        }
    }
}
