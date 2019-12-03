package com.mycompany.myapp.web.api;


import com.mycompany.myapp.service.lag.*;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Mapper(componentModel = "spring")
public interface LagServiceMapper {
    com.mycompany.myapp.web.api.model.DoubleStats toApi(DoubleStats doubleStats);
    com.mycompany.myapp.web.api.model.MessageLag toApi(MessageLag messageLag);
    com.mycompany.myapp.web.api.model.MessageSpeed toApi(MessageSpeed messageSpeed);
    com.mycompany.myapp.web.api.model.SpeedStats toApi(SpeedStats speedStats);
    com.mycompany.myapp.web.api.model.TimeRemaining toApi(TimeRemaining timeRemaining);
    com.mycompany.myapp.web.api.model.TimeRemainingStats toApi(TimeRemainingStats timeRemainingStats);

    List<com.mycompany.myapp.web.api.model.MessageLag> messageLagstoApi(List<MessageLag> consumerLags);
    List<com.mycompany.myapp.web.api.model.MessageSpeed> messageSpeedstoApi(List<MessageSpeed> consumerSpeeds);

    default OffsetDateTime map(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
