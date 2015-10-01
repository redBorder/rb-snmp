package net.redborder.snmp;

import net.redborder.clusterizer.Task;
import net.redborder.clusterizer.ZkTasksHandler;
import net.redborder.snmp.managers.KafkaManager;
import net.redborder.snmp.managers.SnmpManager;
import net.redborder.snmp.util.Configuration;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class SnmpServer {
    public static void main(String[] args) {
        final Configuration configuration = Configuration.getConfiguration();

        String zkConnect = configuration.getFromGeneral(Configuration.Dimensions.ZKCONNECT);
        String zkPath = configuration.getFromGeneral(Configuration.Dimensions.ZKPATH);
        final ZkTasksHandler zkTasksHandler = new ZkTasksHandler(zkConnect, zkPath);

        LinkedBlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        final SnmpManager snmpManager = new SnmpManager(queue);
        final KafkaManager kafkaManager = new KafkaManager(queue);

        /* List oft SnmpTasks */
        zkTasksHandler.addListener(snmpManager);
        zkTasksHandler.setTasks(configuration.getSnmpTasks());

        kafkaManager.start();
        snmpManager.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                kafkaManager.shutdown();
                snmpManager.shutdown();
            }
        });

        Signal.handle(new Signal("HUP"), new SignalHandler() {
            public void handle(Signal signal) {
                try {
                    configuration.readConfiguration();
                    zkTasksHandler.setTasks(configuration.getSnmpTasks());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}