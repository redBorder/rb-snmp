package net.redborder.snmp.managers;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import net.redborder.snmp.util.Configuration;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class KafkaManager {
    private static final Logger log = LoggerFactory.getLogger(KafkaManager.class);

    // The kafka producer
    private Producer<String, String> producer;

    // A JSON parser from Jackson
    private ObjectMapper objectMapper = new ObjectMapper();


    /**
     * Creates a new KafkaSink.
     * This method initializes and starts a new Kafka producer that will be
     * used to produce messages to kafka topics.
     */

    public KafkaManager() {
        Configuration configuration =  Configuration.getConfiguration();
        // The producer config attributes
        Properties props = new Properties();
        props.put("metadata.broker.list", configuration.getFromGeneral(Configuration.Dimensions.KAFKABROKERS));
        props.put("serializer.class", "kafka.serializer.StringEncoder");
        props.put("request.required.acks", "1");
        props.put("message.send.max.retries", "60");
        props.put("retry.backoff.ms", "1000");
        props.put("producer.type", "async");
        props.put("queue.buffering.max.messages", "10000");
        props.put("queue.buffering.max.ms", "500");
        props.put("partitioner.class", "net.redborder.snmp.util.SimplePartitioner");

        // Initialize the producer
        ProducerConfig config = new ProducerConfig(props);
        producer = new Producer<>(config);
    }

    /**
     * Stops the kafka producer and releases its resources.
     */

    public void shutdown() {
        producer.close();
    }

    /**
     * This method sends a given message, with a given key to a given kafka topic.
     *
     * @param topic The topic where the message will be sent
     * @param key The key of the message
     * @param message The message to send
     */

    public void send(String topic, String key, Map<String, Object> message) {
        try {
            String messageStr = objectMapper.writeValueAsString(message);
            KeyedMessage<String, String> keyedMessage = new KeyedMessage<>(topic, key, messageStr);
            producer.send(keyedMessage);
        } catch (IOException e) {
            log.error("Error converting map to json: {}", message);
        }
    }
}
