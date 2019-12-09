package com.mycompany.myapp.service.lag;

import com.mycompany.myapp.config.KafkaProperties;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.Utils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@Profile("!test")
public class KafkaLagService {

    private final KafkaProperties kafkaProperties;

    private final ConsumerOffsetsReader consumerOffsetsReader;

    private final AdminClient client;

    public KafkaLagService(KafkaProperties kafkaProperties, ConsumerOffsetsReader consumerOffsetsReader, AdminClient client) {
        this.kafkaProperties = kafkaProperties;
        this.consumerOffsetsReader = consumerOffsetsReader;
        this.client = client;
    }

    static DoubleStats stddev(List<Double> doubles) {
        double sum = 0.0;
        for (Double i : doubles) {
            sum+=i;
        }
        double mean = sum/doubles.size();

        double num=0.0;
        for (Double i : doubles) {
            num+=Math.pow(i - mean, 2);
        }
        double stddev = Math.sqrt(num/doubles.size());
        return new DoubleStats(mean,stddev);
    }

    public int getPartition(String topic, String key) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProperties.getConsumerProps())) {
            int numPartitions = consumer.partitionsFor(topic).size();
            return Utils.toPositive(Utils.murmur2(key.getBytes(StandardCharsets.UTF_8))) % numPartitions;
        }
    }

    List<OffsetAndInstant> getProducerOffsets(TopicPartition tp, List<Instant> samplingInstants) {
        Map<TopicPartition, Long> timestampsToSearch = new HashMap<>();
        try(KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProperties.getConsumerProps())) {
            Long endOffset = null;
            List<OffsetAndInstant> producerOffsets = new ArrayList<>();

            for (Instant instant : samplingInstants) {
                timestampsToSearch.put(tp, instant.toEpochMilli());
                OffsetAndTimestamp offsetAndTimestamp = consumer.offsetsForTimes(timestampsToSearch).get(tp);
                if (offsetAndTimestamp == null) {
                    if (endOffset == null) {
                        endOffset = consumer.endOffsets(Collections.singletonList(tp)).get(tp);
                    }
                    producerOffsets.add(new OffsetAndInstant(endOffset, instant));
                } else {
                    producerOffsets.add(new OffsetAndInstant(offsetAndTimestamp.offset(), instant));
                }
            }
            return producerOffsets;
        }
    }

    Optional<Long> getConsumerOffsetsFromReadings(String group, TopicPartition tp, Instant instant) {
        OffsetPoint pointBefore = null;
        OffsetPoint pointAfter = null;
        BlockingQueue<OffsetPoint> offsetPoints = consumerOffsetsReader.getGroupOffsetPoints().get(group);
        if (offsetPoints == null) {
            return Optional.empty();
        }
        for (OffsetPoint offsetPoint : offsetPoints) {
            if (offsetPoint.getTimestamp().isBefore(instant)) {
                pointBefore = offsetPoint;
            } else {
                pointAfter = offsetPoint;
                break;
            }
        }
        if (pointAfter == null || pointBefore == null || pointAfter.getOffsets().get(tp) == null || pointBefore.getOffsets().get(tp) == null) {
            return Optional.empty();
        }

        return Optional.of( pointBefore.getOffsets().get(tp) +
            (pointAfter.getOffsets().get(tp) - pointBefore.getOffsets().get(tp))
                * (instant.toEpochMilli() - pointBefore.getTimestamp().toEpochMilli())
                / (pointAfter.getTimestamp().toEpochMilli() - pointBefore.getTimestamp().toEpochMilli())
        );
    }

    MessageLag getConsumerLag(String group, TopicPartition tp, Long producerOffset, Instant timestamp) {
        return getConsumerOffsetsFromReadings(group, tp, timestamp)
            .map(consumerOffset -> new MessageLag(
                consumerOffset,
                producerOffset,
                Math.max(producerOffset - consumerOffset, 0),
                timestamp)
            )
            .orElse(new MessageLag(
                null,
                producerOffset,
                null,
                timestamp));
    }

    public List<MessageLag> getConsumerLags(String group, TopicPartition tp, List<Instant> samplingInstants) {
        return getProducerOffsets(tp, samplingInstants).stream()
            .map(offset -> getConsumerLag(group, tp, offset.getOffset(), offset.getInstant()))
            .collect(Collectors.toList());
    }

    public List<MessageSpeed> getConsumerSpeeds(String group, TopicPartition tp, List<Instant> samplingInstants) {
        List<MessageSpeed> speeds = new ArrayList<>();
        MessageLag previousLag = null;
        List<MessageLag> consumerLags = getConsumerLags(group, tp, samplingInstants);
        consumerLags.sort(Comparator.comparingDouble(lag -> lag.getTimestamp().toEpochMilli()));

        for (MessageLag consumerLag : consumerLags) {
            Long consumerOffset = consumerLag.getConsumerOffset();
            Double speed = null;
            if (consumerOffset != null &&
                previousLag != null &&
                previousLag.getConsumerOffset() != null &&
                consumerLag.getLagMessages() > 0 &&
                previousLag.getLagMessages() > 0) {
                speed = (double)(consumerOffset - previousLag.getConsumerOffset()) * 1000
                    / (consumerLag.getTimestamp().toEpochMilli() - previousLag.getTimestamp().toEpochMilli());

            }
            speeds.add(new MessageSpeed(speed, consumerLag.getTimestamp(), consumerLag));
            previousLag = consumerLag;
        }
        Collections.reverse(speeds);
        return speeds;
    }

    public SpeedStats getSpeedStats(String group, TopicPartition topicPartition, List<Instant> samplingInstants) {
        List<MessageSpeed> messageSpeeds = getConsumerSpeeds(group, topicPartition, samplingInstants);
        List<Double> speeds = messageSpeeds
            .stream()
            .map(MessageSpeed::getSpeed)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        return new SpeedStats(stddev(speeds), messageSpeeds);
    }

    Optional<Long> getCurrentConsumerOffset(String group, TopicPartition topicPartition) throws InterruptedException, ExecutionException {
        return Optional.ofNullable(
            client.listConsumerGroupOffsets(group)
                .partitionsToOffsetAndMetadata()
                .get()
                .get(topicPartition))
            .map(OffsetAndMetadata::offset);
    }

    public MessageLag getMessagesToPublishTimestamp(String group, TopicPartition topicPartition, String publishTimestamp)
        throws InterruptedException, ExecutionException {
        return getCurrentConsumerOffset(group, topicPartition)
            .map(offset -> {
                Instant publishInstant = publishTimestamp == null ? Instant.now() : Instant.parse(publishTimestamp);
                Long producerOffset = getProducerOffsets(topicPartition, Collections.singletonList(publishInstant)).get(0).getOffset();
                return new MessageLag(offset, producerOffset, producerOffset - offset, publishInstant);
            })
            .orElseThrow(() -> new KafkaLagService.OffsetNotFoundException("Couldn't find consumer offset for partition "  + topicPartition.toString()));
    }

    public TimeRemaining getTimeRemaining(String group, TopicPartition partition, String publishTimestamp, List<Instant> samplingInstants) throws ExecutionException, InterruptedException {
        SpeedStats speedStats = getSpeedStats(group, partition, samplingInstants);
        MessageLag messageLag = getMessagesToPublishTimestamp(group, partition, publishTimestamp);
        double timeRemaining = messageLag.getLagMessages().doubleValue() / speedStats.getMeanSpeed().getMean();
        return new TimeRemaining(partition.partition(), timeRemaining > 0 ? timeRemaining : 0, messageLag, speedStats);
    }

    public TimeRemainingStats getTimeRemainingStats(String group, String topic, String publishTimestamp, List<Instant> samplingInstants) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProperties.getConsumerProps())) {
            List<Double> timesRemaining = new ArrayList<>();
            Map<Integer, TimeRemaining> timesRemainingInfo = consumer.partitionsFor(topic).stream()
                .map(PartitionInfo::partition)
                .map(partition -> {
                    try {
                        return getTimeRemaining(group, new TopicPartition(topic, partition), publishTimestamp, samplingInstants);
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toMap(
                    TimeRemaining::getPartition,
                    t -> {
                        timesRemaining.add(t.getTimeRemaining());
                        return t;
                    })
                );
            DoubleStats stddev = stddev(timesRemaining);
            return new TimeRemainingStats(stddev, timesRemainingInfo);
        }
    }

    public static class OffsetAndInstant {
        private final Long offset;
        private final Instant instant;

        public OffsetAndInstant(Long offset, Instant instant) {
            this.offset = offset;
            this.instant = instant;
        }

        public Long getOffset() {
            return offset;
        }

        public Instant getInstant() {
            return instant;
        }

        @Override
        public String toString() {
            return "OffsetAndInstant{" +
                "offset=" + offset +
                ", instant=" + instant +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OffsetAndInstant that = (OffsetAndInstant) o;
            return Objects.equals(offset, that.offset) &&
                Objects.equals(instant, that.instant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(offset, instant);
        }
    }

    public static class OffsetNotFoundException extends RuntimeException {
        public OffsetNotFoundException(String message) {
            super(message);
        }
    }
}
