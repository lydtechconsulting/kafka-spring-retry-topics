package demo.consumer;

import demo.event.CreateItem;
import demo.mapper.JsonMapper;
import demo.service.ItemService;
import demo.util.TestEventData;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class CreateItemConsumerTest {

    private ItemService serviceMock;
    private CreateItemConsumer consumer;

    @BeforeEach
    public void setUp() {
        serviceMock = mock(ItemService.class);
        consumer = new CreateItemConsumer(serviceMock);
    }

    /**
     * Ensure that the JSON message is successfully passed on to the service, having been correctly unmarshalled into its PoJO form.
     */
    @Test
    public void testListen_Success() {
        CreateItem testEvent = TestEventData.buildCreateItemEvent(randomUUID(), RandomStringUtils.randomAlphabetic(8));
        String payload = JsonMapper.writeToJson(testEvent);

        consumer.listen(payload);

        verify(serviceMock, times(1)).createItem(testEvent);
    }

    /**
     * If an exception is thrown, an error is logged but the processing completes successfully.
     *
     * This ensures the consumer offsets are updated so that the message is not redelivered.
     */
    @Test
    public void testListen_ServiceThrowsException() {
        CreateItem testEvent = TestEventData.buildCreateItemEvent(randomUUID(), RandomStringUtils.randomAlphabetic(8));
        String payload = JsonMapper.writeToJson(testEvent);

        doThrow(new RuntimeException("Service failure")).when(serviceMock).createItem(testEvent);

        consumer.listen(payload);

        verify(serviceMock, times(1)).createItem(testEvent);
    }
}
