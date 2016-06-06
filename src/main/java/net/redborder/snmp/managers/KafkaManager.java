package net.redborder.snmp.managers;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import net.redborder.snmp.util.Configuration;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

public class KafkaManager extends Thread {
    private static final Logger log = LoggerFactory.getLogger(KafkaManager.class);

    // The kafka producer
    private Producer<String, String> producer;

    // A JSON parser from Jackson
    private ObjectMapper objectMapper = new ObjectMapper();

    private LinkedBlockingQueue<Map<String, Object>> queue;


    /**
     * Creates a new KafkaSink.
     * This method initializes and starts a new Kafka producer that will be
     * used to produce messages to kafka topics.
     */

    public KafkaManager(LinkedBlockingQueue<Map<String, Object>> queue) {
        this.queue = queue;
        Configuration configuration = Configuration.getConfiguration();
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

    @Override
    public void run() {
        log.info("KafkaManager is started!");
        while (!isInterrupted()) {
            Map<String, Object> event;
            try {
                event = queue.take();
                if (event != null) {
                    String directions[] = new String[]{"ingress", "egress"};
                    Map<String, Object> state = new HashMap<>();
                    Long time_now = System.currentTimeMillis() / 1000;
                    time_now = time_now - (time_now%60);
                    Map<String, Object> enrichment = (Map<String, Object>) event.get("enrichment");

                    if (event.get("devClientCount") != null) {
                        state.put("wireless_station", event.get("devInterfaceMac"));
                        if(event.get("devName") != null)
                            state.put("wireless_station_name", event.get("devName"));
                        state.put("client_count", event.get("devClientCount"));
                        state.put("type", "snmp_apMonitor");
                        state.put("timestamp", time_now);
                        state.put("status", event.get("devStatus"));
                        state.put("sensor_ip", event.get("sensorIp"));
                        state.putAll(enrichment);

                        // Data for Load Utilization (Channel 2.4)
                        if (event.get("dev24LoadChannelUtilization") != null) {
                            Map<String, Object> state24 = new HashMap<>();
                            state24.put("type", "channel_2.4");
                            state24.put("wireless_station", event.get("devInterfaceMac"));
                            state24.put("timestamp", time_now);
                            state24.put("value", Integer.valueOf((String) event.get("dev24LoadChannelUtilization")));
                            state24.putAll(enrichment);
                            send("rb_state", (String) event.get("devInterfaceMac"), state24);
                        }

                        // Data for Load Utilization (Channel 5)
                        if (event.get("dev5LoadChannelUtilization") != null) {
                            Map<String, Object> state5 = new HashMap<>();
                            state5.put("type", "channel_5");
                            state5.put("wireless_station", event.get("devInterfaceMac"));
                            state5.put("timestamp", time_now);
                            state5.put("value", Integer.valueOf((String) event.get("dev5LoadChannelUtilization")));
                            state5.putAll(enrichment);
                            send("rb_state", (String) event.get("devInterfaceMac"), state5);
                        }

                        send("rb_state", (String) event.get("devInterfaceMac"), state);
                    }

                    if ((Boolean) event.get("validForStats")) {
                        for (String direction : directions) {
                            Map<String, Object> directionStats = new HashMap<>();
                            Long bytes = (Long) event.get("devInterfaceSentBytes");
                            Long pkts = (Long) event.get("devInterfaceSentPkts");
                            if (direction.equals("ingress")) {
                                bytes = (Long) event.get("devInterfaceSentPkts");
                                pkts = (Long) event.get("devInterfaceSentBytes");
                            }
                            if (bytes < 0 || pkts < 0) {
                                log.warn("Flow's lower than 0!. Sensor: {}, AP: {}, Bytes: {}, Pkts: {}",
                                        event.get("sensor_ip"), event.get("devInterfaceMac"), bytes, pkts);
                            } else {
                                directionStats.put("bytes", bytes);
                                directionStats.put("pkts", pkts);
                                directionStats.put("direction", direction);
                                directionStats.put("timestamp", time_now);
                                directionStats.put("first_switched", time_now - (Long) event.get("timeSwitched"));
                                directionStats.put("wireless_station", event.get("devInterfaceMac"));
                                directionStats.put("interface_name", event.get("devInterfaceName"));
                                directionStats.put("device_category", "stations");
                                directionStats.put("type", "snmp-stats");
                                directionStats.put("dot11_status", "ASSOCIATED");
                                directionStats.put("sensor_ip", event.get("sensorIp"));
                                directionStats.putAll(enrichment);

                                send("rb_flow", (String) event.get("devInterfaceMac"), directionStats);
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                log.info("KafkaManager is stopping ...");
            }
        }

    }

    /**
     * Stops the kafka producer and releases its resources.
     */

    public void shutdown() {
        producer.close();
        currentThread().interrupt();
    }

    /**
     * This method sends a given message, with a given key to a given kafka topic.
     *
     * @param topic   The topic where the message will be sent
     * @param key     The key of the message
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
