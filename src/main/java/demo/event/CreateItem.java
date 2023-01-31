package demo.event;

import java.util.UUID;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateItem {

    @NotNull
    private UUID id;

    @NotNull
    private String name;
}
