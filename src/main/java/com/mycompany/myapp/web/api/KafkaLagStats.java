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
public class KafkaLagStats implements KafkaLagStatsApiDelegate {

    private static final int NUMBER_OF_SAMPLING_INSTANTS = 10;
    private final KafkaLagService lagService;
    private final Clock clock;
    private final LagServiceMapper mapper;

    public KafkaLagStats(KafkaLagService lagService, Clock clock, LagServiceMapper mapper) {
        this.lagService = lagService;
        this.clock = clock;
        this.mapper = mapper;
    }

    /**
     * GET /api/kafka-lag/lags : get the last lags in time for a single consumer
     *
     * @param group the consumer group of the consumer (required)
     * @param topic the topic subscribed by the consumer (required)
     * @param key the partition key used to get the partition of the consumer  (give partition or key but not both) (optional)
     * @param partition the partition of the consumer (provide partition or key but not both) (optional)
     * @return the response
     * @see KafkaLagStatsApi#getLags
     */
    @Override
    public ResponseEntity<List<MessageLag>> getLags(String group, String topic, String key, Integer partition) {
        List<Instant> samplingInstants = getSamplingInstantsFromNow(NUMBER_OF_SAMPLING_INSTANTS);
        return ResponseEntity.ok(mapper.messageLagstoApi(lagService.getConsumerLags(group, getPartitionFromParams(topic, partition, key), samplingInstants)));
    }

    /**
     * GET /api/kafka-lag/messages-remaining : get the number of messages that a consumer still has to consume to reach a message published at a given time
     *
     * @param group the consumer group of the consumer (required)
     * @param topic the topic subscribed by the consumer (required)
     * @param key the partition key used to get the partition of the consumer  (give partition or key but not both) (optional)
     * @param partition the partition  of the consumer (provide partition or key but not both) (optional)
     * @param publishTimestamp the timestamp at which the message was published (optional)
     * @return the response
     * @see KafkaLagStatsApi#getMessagesToPublishTimestamp
     */
    @Override
    public ResponseEntity<MessageLag> getMessagesToPublishTimestamp(String group, String topic, String key, Integer partition, String publishTimestamp) {
        try {
            return ResponseEntity.ok(mapper.toApi(lagService.getMessagesToPublishTimestamp(group, getPartitionFromParams(topic, partition, key), publishTimestamp)));
        } catch (InterruptedException | ExecutionException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error!", e);
        }
    }

    /**
     * GET /api/kafka-lag/partition : get the partition for the given topic and key
     *
     * @param key the key (required)
     * @param topic the topic (required)
     * @return the response
     * @see KafkaLagStatsApi#getPartition
     */
    @Override
    public ResponseEntity<Integer> getPartition(String key, String topic) {
        return ResponseEntity.ok(lagService.getPartition(topic, key));
    }

    /**
     * GET /api/kafka-lag/speed-stats : get the average consumption speed of a given consumer
     *
     * @param group the consumer group of the consumer (required)
     * @param topic the topic subscribed by the consumer (required)
     * @param key the partition key used to get the partition of the consumer  (give partition or key but not both) (optional)
     * @param partition the partition  of the consumer (provide partition or key but not both) (optional)
     * @return the response
     * @see KafkaLagStatsApi#getSpeedStats
     */
    @Override
    public ResponseEntity<SpeedStats> getSpeedStats(String group, String topic, String key, Integer partition) {
        List<Instant> samplingInstants = getSamplingInstantsFromNow(NUMBER_OF_SAMPLING_INSTANTS);
        return ResponseEntity.ok(mapper.toApi(lagService.getSpeedStats(group, getPartitionFromParams(topic, partition, key), samplingInstants)));
    }

    /**
     * GET /api/kafka-lag/speeds : get the last consumption speeds in time for a single consumer
     *
     * @param group the consumer group of the consumer (required)
     * @param topic the topic subscribed by the consumer (required)
     * @param key the partition key used to get the partition of the consumer  (give partition or key but not both) (optional)
     * @param partition the partition  of the consumer (provide partition or key but not both) (optional)
     * @return the response
     * @see KafkaLagStatsApi#getSpeeds
     */
    @Override
    public ResponseEntity<List<MessageSpeed>> getSpeeds(String group, String topic, String key, Integer partition) {
        List<Instant> samplingInstants = getSamplingInstantsFromNow(NUMBER_OF_SAMPLING_INSTANTS);
        return ResponseEntity.ok(mapper.messageSpeedstoApi(lagService.getConsumerSpeeds(group,  getPartitionFromParams(topic, partition, key), samplingInstants)));
    }

    /**
     * GET /api/kafka-lag/time-remaining-stats : get the average time that a consumer still needs to consume a message published
     *
     * @param group the consumer group of the consumer (required)
     * @param topic the topic subscribed by the consumer (required)
     * @param publishTimestamp the timestamp at which the message was published (optional)
     * @return the response
     * @see KafkaLagStatsApi#getTimeRemainingStats
     */
    @Override
    public ResponseEntity<TimeRemainingStats> getTimeRemainingStats(String group, String topic, String publishTimestamp) {
        List<Instant> samplingInstants = getSamplingInstantsFromNow(NUMBER_OF_SAMPLING_INSTANTS);
        TimeRemainingStats timeRemainingStats = mapper.toApi(lagService.getTimeRemainingStats(group, topic, publishTimestamp, samplingInstants));
        return ResponseEntity.ok(timeRemainingStats);
    }

    /**
     * GET /api/kafka-lag/time-remaining : get the time that a consumer still needs to consume a message published at a given time
     *
     * @param group the consumer group of the consumer (required)
     * @param topic the topic subscribed by the consumer (required)
     * @param key the partition key used to get the partition of the consumer  (give partition or key but not both) (optional)
     * @param partition the partition  of the consumer (provide partition or key but not both) (optional)
     * @param publishTimestamp the timestamp at which the message was published (optional)
     * @return the response
     * @see KafkaLagStatsApi#getTimeRemaining
     */
    @Override
    public ResponseEntity<TimeRemaining> getTimeRemaining(String group, String topic, String key, Integer partition, String publishTimestamp) {
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
