package demo.service;

import java.util.Optional;

import demo.domain.Item;
import demo.event.CreateItem;
import demo.event.UpdateItem;
import demo.exception.RetryableMessagingException;
import demo.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ItemService {

    @Autowired
    private final ItemRepository itemRepository;

    public void createItem(final CreateItem event) {
        Item item = Item.builder()
                .id(event.getId())
                .name(event.getName())
                .status(ItemStatus.NEW)
                .build();
        itemRepository.save(item);
        log.info("Item persisted to database with Id: {}", event.getId());
    }

    public void updateItem(final UpdateItem event) {
        final Optional<Item> item = itemRepository.findById(event.getId());
        if(item.isPresent()) {
            item.get().setStatus(event.getStatus());
            itemRepository.save(item.get());
            log.info("Item updated in database with Id: {}", event.getId());
        } else {
            // Retry...
            log.info("Item not found with Id: {} - retrying update event", event.getId());
            throw new RetryableMessagingException("Retry event.");
        }
    }
}
