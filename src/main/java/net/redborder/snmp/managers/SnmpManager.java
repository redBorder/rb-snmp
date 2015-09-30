package net.redborder.snmp.managers;

import net.redborder.clusterizer.Task;
import net.redborder.clusterizer.TasksChangedListener;
import net.redborder.snmp.util.AccessPointDB;
import net.redborder.snmp.workers.SnmpWorker;
import net.redborder.snmp.tasks.SnmpTask;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class SnmpManager extends Thread implements TasksChangedListener {

    List<SnmpWorker> workers = new ArrayList<>();
    LinkedBlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
    KafkaManager kafkaManager = new KafkaManager();

    @Override
    public void run() {
        while (isInterrupted()) {
            Map<String, Object> event = new HashMap<>();

            try {
                event = queue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (event != null) {
                String directions[] = new String[]{"ingress", "egress"};
                Map<String, Object> state = new HashMap<>();
                Long time_now = System.currentTimeMillis() / 1000;
                Map<String, Object> enrichment = (Map<String, Object>) event.get("enrichment");

                state.put("wireless_station", event.get("mac_address"));
                state.put("wireless_station_ip", event.get("ip_address"));
                state.put("type", "snmp_apMonitor");
                state.put("timestamp", time_now);
                state.put("status", event.get("status"));
                state.put("sensor_ip", event.get("sensor_ip"));
                state.putAll(enrichment);

                if ((Boolean) event.get("is_first")) {
                    for (String direction : directions) {
                        Map<String, Object> directionStats = new HashMap<>();
                        Long bytes = (Long) event.get("sent_bytes");
                        Long pkts = (Long) event.get("sent_pkts");
                        if (direction.equals("ingress")) {
                            bytes = (Long) event.get("recv_bytes");
                            pkts = (Long) event.get("recv_pkts");
                        }

                        directionStats.put("bytes", bytes);
                        directionStats.put("pkts", pkts);
                        directionStats.put("direction", direction);
                        directionStats.put("timestamp", time_now);
                        directionStats.put("time_switched", time_now - (5 * 60));
                        directionStats.put("sensor_ip", "");
                        directionStats.put("wireless_station", event.get("mac_address"));
                        directionStats.put("wireless_station_ip", event.get("ip_address"));
                        directionStats.put("device_category", "stations");
                        directionStats.put("type", "snmpstats");
                        directionStats.put("sensor_ip", event.get("sensor_ip"));
                        directionStats.putAll(enrichment);

                        kafkaManager.send("rb_flow", (String) event.get("mac_address"), directionStats);
                    }
                }
                kafkaManager.send("rb_state", (String) event.get("mac_address"), state);
            }
        }
    }

    @Override
    public void updateTasks(List<Task> list) {
        for (Task t : list) {
            SnmpTask snmpTask = (SnmpTask) t;
            SnmpWorker worker = new SnmpWorker(snmpTask, queue);
            workers.add(worker);
            worker.start();
        }
    }
}
