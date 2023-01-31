package demo.event;

import java.util.UUID;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import demo.service.ItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateItem {

    @NotNull
    private UUID id;

    @Valid
    private ItemStatus status;
}
