import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class ArrivalPrometheus {


    private static final Logger log = LogManager.getLogger(ArrivalPrometheus.class);
    public static String CONSUMER_GROUP;
    public static AdminClient admin = null;
    static Long sleep;
    static double doublesleep;
    static String topic;


    static String BOOTSTRAP_SERVERS;
    static Map<TopicPartition, OffsetAndMetadata> committedOffsets;


    static Map<String, ConsumerGroupDescription> consumerGroupDescriptionMap;
    //////////////////////////////////////////////////////////////////////////////
    static TopicDescription td;
    static DescribeTopicsResult tdr;
    static ArrayList<Partition> partitions = new ArrayList<>();





     static void readEnvAndCrateAdminClient() throws ExecutionException, InterruptedException {
        sleep = 1L;
        doublesleep = 1000L;
        topic = "testtopic1";

        CONSUMER_GROUP = "testgroup1";
        BOOTSTRAP_SERVERS ="my-cluster-kafka-bootstrap:9092";
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        admin = AdminClient.create(props);
        tdr = admin.describeTopics(Collections.singletonList(topic));
        td = tdr.values().get(topic).get();


        for (TopicPartitionInfo p : td.partitions()) {
            partitions.add(new Partition(p.partition(), 0, 0));
        }
        log.info("topic has the following partitions {}", td.partitions().size());
    }




     static void getCommittedLatestOffsetsAndLag() throws ExecutionException, InterruptedException {
        committedOffsets = admin.listConsumerGroupOffsets(CONSUMER_GROUP)
                .partitionsToOffsetAndMetadata().get();

        Map<TopicPartition, OffsetSpec> requestLatestOffsets = new HashMap<>();
        Map<TopicPartition, OffsetSpec> requestTimestampOffsets1 = new HashMap<>();
        Map<TopicPartition, OffsetSpec> requestTimestampOffsets2 = new HashMap<>();

        for (TopicPartitionInfo p : td.partitions()) {
            requestLatestOffsets.put(new TopicPartition(topic, p.partition()), OffsetSpec.latest());
            requestTimestampOffsets2.put(new TopicPartition(topic, p.partition()),
                    OffsetSpec.forTimestamp(Instant.now().minusMillis(1500).toEpochMilli()));
            requestTimestampOffsets1.put(new TopicPartition(topic, p.partition()),
                    OffsetSpec.forTimestamp(Instant.now().minusMillis(sleep + 1500).toEpochMilli()));
        }

        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                admin.listOffsets(requestLatestOffsets).all().get();
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> timestampOffsets1 =
                admin.listOffsets(requestTimestampOffsets1).all().get();
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> timestampOffsets2 =
                admin.listOffsets(requestTimestampOffsets2).all().get();


        long totalArrivalRate = 0;
        double currentPartitionArrivalRate;
        Map<Integer, Double> previousPartitionArrivalRate = new HashMap<>();
        for (TopicPartitionInfo p : td.partitions()) {
            previousPartitionArrivalRate.put(p.partition(), 0.0);
        }
        for (TopicPartitionInfo p : td.partitions()) {
            TopicPartition t = new TopicPartition(topic, p.partition());
            long latestOffset = latestOffsets.get(t).offset();
            long timeoffset1 = timestampOffsets1.get(t).offset();
            long timeoffset2 = timestampOffsets2.get(t).offset();
            long committedoffset = committedOffsets.get(t).offset();
            partitions.get(p.partition()).setLag(latestOffset - committedoffset);
            //TODO if abs(currentPartitionArrivalRate -  previousPartitionArrivalRate) > 15
            //TODO currentPartitionArrivalRate= previousPartitionArrivalRate;
            if(timeoffset2==timeoffset1)
                break;

            if (timeoffset2 == -1) {
                timeoffset2 = latestOffset;
            }
            if (timeoffset1 == -1) {
                // NOT very critical condition
                currentPartitionArrivalRate = previousPartitionArrivalRate.get(p.partition());
                partitions.get(p.partition()).setArrivalRate(currentPartitionArrivalRate);
                log.info("Arrival rate into partition {} is {}", t.partition(), partitions.get(p.partition()).getArrivalRate());
                log.info("lag of  partition {} is {}", t.partition(),
                        partitions.get(p.partition()).getLag());
                log.info(partitions.get(p.partition()));
            } else {
                currentPartitionArrivalRate = (double) (timeoffset2 - timeoffset1) / doublesleep;
                //if(currentPartitionArrivalRate==0) continue;
                //TODO only update currentPartitionArrivalRate if (currentPartitionArrivalRate - previousPartitionArrivalRate) < 10 or threshold
                // currentPartitionArrivalRate = previousPartitionArrivalRate.get(p.partition());
                //TODO break
                partitions.get(p.partition()).setArrivalRate(currentPartitionArrivalRate);
                log.info(" Arrival rate into partition {} is {}", t.partition(),
                        partitions.get(p.partition()).getArrivalRate());

                log.info(" lag of  partition {} is {}", t.partition(),
                        partitions.get(p.partition()).getLag());
                log.info(partitions.get(p.partition()));
            }
            //TODO add a condition for when both offsets timeoffset2 and timeoffset1 do not exist, i.e., are -1,
            previousPartitionArrivalRate.put(p.partition(), currentPartitionArrivalRate);
            totalArrivalRate += currentPartitionArrivalRate;
        }
        //report total arrival only if not zero only the loop has not exited.
        log.info("totalArrivalRate {}", totalArrivalRate);



    }

}
