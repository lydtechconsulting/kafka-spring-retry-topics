package demo.util;

import java.util.UUID;

import demo.event.CreateItem;
import demo.event.UpdateItem;
import demo.service.ItemStatus;

public class TestEventData {

    public static CreateItem buildCreateItemEvent(UUID id, String name) {
        return CreateItem.builder()
                .id(id)
                .name(name)
                .build();
    }

    public static UpdateItem buildUpdateItemEvent(UUID id, ItemStatus status) {
        return UpdateItem.builder()
                .id(id)
                .status(status)
                .build();
    }
}
