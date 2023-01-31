package demo.integration;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import demo.DemoConfiguration;
import demo.event.CreateItem;
import demo.event.UpdateItem;
import demo.mapper.JsonMapper;
import demo.repository.ItemRepository;
import demo.service.ItemStatus;
import demo.util.TestEventData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = { DemoConfiguration.class } )
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@EmbeddedKafka(controlledShutdown = true, topics = { "create-item", "update-item", "update-item-retry" })
public class KafkaIntegrationTest {

    final static String CREATE_ITEM_TOPIC = "create-item";
    final static String UPDATE_ITEM_TOPIC = "update-item";

    @Autowired
    private TestKafkaClient kafkaClient;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    public void setUp() {
        itemRepository.deleteAll();

        // Wait until the partitions are assigned.
        registry.getListenerContainers().stream().forEach(container ->
                ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic()));
    }

    /**
     * Test the standard scenario where items are created first before update events are received to update them.
     *
     * Each item is created with NEW status, and the update event transitions it to ACTIVE status.
     */
    @Test
    public void testCreateAndUpdateItems() {
        int totalMessages = 10;
        Set<UUID> itemIds = new HashSet<>();

        // Create new items.
        for (int i=0; i<totalMessages; i++) {
            UUID itemId = randomUUID();
            CreateItem createEvent = TestEventData.buildCreateItemEvent(itemId, RandomStringUtils.randomAlphabetic(8));
            kafkaClient.sendMessage(CREATE_ITEM_TOPIC, JsonMapper.writeToJson(createEvent));
            itemIds.add(itemId);
        }

        // Check all items added to database.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(() -> itemRepository.findAll().size() == totalMessages);
        // Check all items have NEW status.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(() -> itemRepository.findAll().stream().allMatch((item) -> item.getStatus().equals(ItemStatus.NEW)));

        // Update all items.
        itemIds.forEach((itemId) -> {
            UpdateItem updateEvent = TestEventData.buildUpdateItemEvent(itemId, ItemStatus.ACTIVE);
            kafkaClient.sendMessage(UPDATE_ITEM_TOPIC, JsonMapper.writeToJson(updateEvent));
        });
        // Check all items have transitioned to ACTIVE status.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(() -> itemRepository.findAll().stream().allMatch((item) -> item.getStatus().equals(ItemStatus.ACTIVE)));
    }

    /**
     * Test where the update item events are received first, and do not find matching items in the database.
     *
     * The update item events are sent to the retry topic.
     *
     * Meanwhile the corresponding create item events are received, and the items are persisted in the database.
     *
     * The update item events should successfully retry and then update the items now found in the database.
     */
    @Test
    public void testUpdateBeforeCreate() throws Exception {
        int totalMessages = 10;
        Set<UUID> itemIds = new HashSet<>();

        // Update the items.
        for (int i=0; i<totalMessages; i++) {
            UUID itemId = randomUUID();
            UpdateItem updateEvent = TestEventData.buildUpdateItemEvent(itemId, ItemStatus.ACTIVE);
            kafkaClient.sendMessage(UPDATE_ITEM_TOPIC, JsonMapper.writeToJson(updateEvent));
            itemIds.add(itemId);
        }

        // Pause a few seconds before sending in the create item events to ensure the update events are sent to the retry topic.
        TimeUnit.SECONDS.sleep(5);

        // Assert that no items have been created by the update item event.
        assertThat(itemRepository.findAll().size(), equalTo(0));

        // Create the new items.
        itemIds.forEach((itemId) -> {
            CreateItem createEvent = TestEventData.buildCreateItemEvent(itemId, RandomStringUtils.randomAlphabetic(8));
            kafkaClient.sendMessage(CREATE_ITEM_TOPIC, JsonMapper.writeToJson(createEvent));
        });

        // Check all messages added to database.
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(() -> itemRepository.findAll().size() == totalMessages);
        // Check all messages have ACTIVE status.
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(() -> itemRepository.findAll().stream().allMatch((item) -> item.getStatus().equals(ItemStatus.ACTIVE)));
    }

    /**
     * Test where an update item event is received before the corresponding create item.  The update item event is retried
     * but is discarded as the max retry duration is exceeded before the create item event is received.
     */
    @Test
    public void testUpdateEventIsDiscarded() throws Exception {
        UUID itemId = randomUUID();

        // Send in the update item event before the item is created.
        UpdateItem updateEvent = TestEventData.buildUpdateItemEvent(itemId, ItemStatus.ACTIVE);
        kafkaClient.sendMessage(UPDATE_ITEM_TOPIC, JsonMapper.writeToJson(updateEvent));

        // Pause for longer than the maxRetryDurationSeconds (defined in application-test.yml) before sending the create
        // item event to prove the update event is discarded.
        TimeUnit.SECONDS.sleep(12);

        // Create the new item.
        CreateItem createEvent = TestEventData.buildCreateItemEvent(itemId, RandomStringUtils.randomAlphabetic(8));
        kafkaClient.sendMessage(CREATE_ITEM_TOPIC, JsonMapper.writeToJson(createEvent));

        // Check item is added to database.
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(() -> itemRepository.findAll().size() == 1);

        // Pause to ensure the update item event is not applied before checking status.
        TimeUnit.SECONDS.sleep(3);

        // Call the endpoint to check the item has NEW status (hence has not been updated).
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(() -> {
                ResponseEntity<String> response = restTemplate.getForEntity("/v1/demo/items/"+itemId+"/status", String.class);
                return response.getStatusCode() == HttpStatus.OK && response.getBody().equals("NEW");
            });
    }
}
