import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class Lag2  {

    private static final Logger log = LoggerFactory.getLogger(Lag2.class);


    private static Properties consumerGroupProps;
    private static  Properties metadataConsumerProps;
    private static KafkaConsumer<byte[], byte[]> metadataConsumer;


static {
    // Construct Properties from config map
    consumerGroupProps = new Properties();

    // group.id must be defined
   consumerGroupProps.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "testgroup1");
    consumerGroupProps.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "my-cluster-kafka-bootstrap:9092");


    consumerGroupProps.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            "org.apache.kafka.common.serialization.StringDeserializer");

    consumerGroupProps.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
    "org.apache.kafka.common.serialization.StringDeserializer");


    // Create a new consumer that can be used to get lag metadata for the consumer group


    log.info("creating the metadataconsumer inside the configure");


    if (metadataConsumer == null) {
        metadataConsumer = new KafkaConsumer<>(consumerGroupProps);
    }


}

     static void readTopicPartitionLags() {
        // metadataConsumer.enforceRebalance();
                final List<TopicPartition> topicPartitions =  new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                          topicPartitions.add(new TopicPartition("testtopic1", i));
                }

                // Get end offset in each partition
                final Map<TopicPartition, Long> topicEndOffsets = metadataConsumer.endOffsets(topicPartitions);
                //get last committed offset
                Map<TopicPartition, OffsetAndMetadata> partitionMetadata =
                        metadataConsumer.committed(new HashSet<>(topicPartitions));
                // Determine lag for each partition
                double totalLag=0;
                for (TopicPartition partition : topicPartitions) {
                    final long lag =
                            topicEndOffsets.get(partition)- partitionMetadata.get(partition).offset();
                    totalLag += lag;

                  log.info(" lag of partition {} is {}", partition.partition(), lag);
                }
                log.info("total lag is {}", totalLag );
            }
    }


















