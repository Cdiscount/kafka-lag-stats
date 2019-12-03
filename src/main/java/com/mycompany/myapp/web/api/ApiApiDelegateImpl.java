package com.mycompany.myapp.web.api;

import com.mycompany.myapp.service.lag.KafkaLagService;
import com.mycompany.myapp.web.api.model.*;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Profile("!test")
public class ApiApiDelegateImpl implements ApiApiDelegate {

    private static final int NUMBER_OF_SAMPLING_INSTANTS = 10;
    private final KafkaLagService lagService;
    private final Clock clock;
    private final LagServiceMapper mapper;

    public ApiApiDelegateImpl(KafkaLagService lagService, Clock clock, LagServiceMapper mapper) {
        this.lagService = lagService;
        this.clock = clock;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<MessageLag>> getLagsUsingGET(String group, String topic, String key, Integer partition) {
        List<Instant> samplingInstants = getSamplingInstantsFromNow(NUMBER_OF_SAMPLING_INSTANTS);
        return ResponseEntity.ok(mapper.messageLagstoApi(lagService.getConsumerLags(group, getPartitionFromParams(topic, partition, key), samplingInstants)));
    }

    @Override
    public ResponseEntity<MessageLag> getMessagesToPublishTimestampUsingGET(String group, String topic, String key, Integer partition, String publishTimestamp) {
        try {
            return ResponseEntity.ok(mapper.toApi(lagService.getMessagesToPublishTimestamp(group, getPartitionFromParams(topic, partition, key), publishTimestamp)));
        } catch (InterruptedException | ExecutionException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error!", e);
        }
    }

    @Override
    public ResponseEntity<Integer> getPartitionUsingGET(String key, String topic) {
        return ResponseEntity.ok(lagService.getPartition(topic, key));
    }

    @Override
    public ResponseEntity<SpeedStats> getSpeedStatsUsingGET(String group, String topic, String key, Integer partition) {
        List<Instant> samplingInstants = getSamplingInstantsFromNow(NUMBER_OF_SAMPLING_INSTANTS);
        return ResponseEntity.ok(mapper.toApi(lagService.getSpeedStats(group, getPartitionFromParams(topic, partition, key), samplingInstants)));
    }

    @Override
    public ResponseEntity<List<MessageSpeed>> getSpeedsUsingGET(String group, String topic, String key, Integer partition) {
        List<Instant> samplingInstants = getSamplingInstantsFromNow(NUMBER_OF_SAMPLING_INSTANTS);
        return ResponseEntity.ok(mapper.messageSpeedstoApi(lagService.getConsumerSpeeds(group,  getPartitionFromParams(topic, partition, key), samplingInstants)));
    }

    @Override
    public ResponseEntity<TimeRemainingStats> getTimeRemainingStatsUsingGET(String group, String topic, String publishTimestamp) {
        List<Instant> samplingInstants = getSamplingInstantsFromNow(NUMBER_OF_SAMPLING_INSTANTS);
        TimeRemainingStats timeRemainingStats = mapper.toApi(lagService.getTimeRemainingStats(group, topic, publishTimestamp, samplingInstants));
        return ResponseEntity.ok(timeRemainingStats);
    }

    @Override
    public ResponseEntity<TimeRemaining> getTimeRemainingUsingGET(String group, String topic, String key, Integer partition, String publishTimestamp) {
        List<Instant> samplingInstants = getSamplingInstantsFromNow(NUMBER_OF_SAMPLING_INSTANTS);
        try {
            return ResponseEntity.ok(mapper.toApi(lagService.getTimeRemaining(group, getPartitionFromParams(topic, partition, key), publishTimestamp, samplingInstants)));
        } catch (ExecutionException | InterruptedException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error!", e);
        }

    }

    private TopicPartition getPartitionFromParams(String topic, Integer partition, String key) {
        if (partition == null && key == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either partition or key must be specified");
        }
        if (partition != null && key != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either partition or key must be specified, not both");
        }
        return new TopicPartition(topic, key != null ? lagService.getPartition(topic, key) : partition);
    }

    private List<Instant> getSamplingInstantsFromNow(int numberOfInstants) {
        // Start 2 seconds ago to have high probability that the consumer offset has been read.
        Instant now = clock.instant().minusSeconds(2);
        return IntStream.range(0, numberOfInstants)
            .mapToObj(i -> now.minusSeconds(i * 60L))
            .collect(Collectors.toList());
    }
}
