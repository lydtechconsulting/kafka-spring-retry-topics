package demo.service;

import java.util.Optional;
import java.util.UUID;

import demo.domain.Item;
import demo.event.CreateItem;
import demo.event.UpdateItem;
import demo.exception.RetryableMessagingException;
import demo.repository.ItemRepository;
import demo.util.TestEntityData;
import demo.util.TestEventData;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ItemServiceTest {

    private ItemService service;
    private ItemRepository itemRepositoryMock;

    @BeforeEach
    public void setUp() {
        itemRepositoryMock = mock(ItemRepository.class);
        service = new ItemService(itemRepositoryMock);
    }

    @Test
    public void testCreateItem() {
        final String name = RandomStringUtils.randomAlphabetic(8);
        CreateItem testEvent = TestEventData.buildCreateItemEvent(randomUUID(), name);

        service.createItem(testEvent);

        verify(itemRepositoryMock, times(1)).save(argThat(s -> s.getName().equals(name)));
    }

    @Test
    public void testUpdateItem_ItemUpdated() {
        UUID itemId = randomUUID();
        Item item = TestEntityData.buildItem(itemId, "my-item");
        when(itemRepositoryMock.findById(itemId)).thenReturn(Optional.of(item));

        UpdateItem testEvent = TestEventData.buildUpdateItemEvent(itemId, ItemStatus.ACTIVE);

        service.updateItem(testEvent);

        verify(itemRepositoryMock, times(1)).save(argThat(s -> s.getStatus().equals(ItemStatus.ACTIVE)));
    }

    @Test
    public void testUpdateItem_ItemRetried() {
        UUID itemId = randomUUID();
        when(itemRepositoryMock.findById(itemId)).thenReturn(Optional.empty());

        UpdateItem testEvent = TestEventData.buildUpdateItemEvent(itemId, ItemStatus.ACTIVE);

        Exception exception = assertThrows(RetryableMessagingException.class, () -> {
           service.updateItem(testEvent);
        });

        assertThat(exception.getMessage(), equalTo("Retry event."));
        verify(itemRepositoryMock, times(0)).save(any());
    }
}
