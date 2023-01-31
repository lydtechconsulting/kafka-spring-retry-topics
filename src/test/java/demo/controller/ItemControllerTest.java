package demo.controller;

import java.util.Optional;
import java.util.UUID;

import demo.domain.Item;
import demo.repository.ItemRepository;
import demo.util.TestEntityData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ItemControllerTest {

    private ItemRepository itemRepositoryMock;
    private ItemController controller;

    @BeforeEach
    public void setUp() {
        itemRepositoryMock = mock(ItemRepository.class);
        controller = new ItemController(itemRepositoryMock);
    }

    /**
     * Ensure that the REST call results in a database lookup.
     */
    @Test
    public void testGetItem_Success() {
        UUID itemId = randomUUID();
        Item item = TestEntityData.buildItem(itemId, "my-item");
        when(itemRepositoryMock.findById(itemId)).thenReturn(Optional.of(item));

        ResponseEntity response = controller.getItemStatus(itemId);
        assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
        assertThat(response.getBody(), equalTo(item.getStatus().toString()));
    }

    @Test
    public void testGetItem_NotFound() {
        UUID itemId = randomUUID();
        when(itemRepositoryMock.findById(itemId)).thenReturn(Optional.empty());

        ResponseEntity response = controller.getItemStatus(itemId);
        assertThat(response.getStatusCode(), equalTo(HttpStatus.NOT_FOUND));
    }

    @Test
    public void testListen_RepositoryThrowsException() {
        UUID itemId = randomUUID();
        when(itemRepositoryMock.findById(itemId)).thenThrow(new RuntimeException("failed"));

        ResponseEntity response = controller.getItemStatus(itemId);
        assertThat(response.getStatusCode(), equalTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
