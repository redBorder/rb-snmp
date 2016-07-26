package net.redborder.snmp;

import net.redborder.clusterizer.RecoverFromFailed;
import net.redborder.clusterizer.Task;
import net.redborder.clusterizer.ZkTasksHandler;
import net.redborder.snmp.managers.KafkaManager;
import net.redborder.snmp.managers.SnmpManager;
import net.redborder.snmp.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class SnmpServer {
    public static void main(String[] args) {
        final Logger log = LoggerFactory.getLogger(SnmpServer.class);

        final Configuration configuration = Configuration.getConfiguration();

        String zkConnect = configuration.getFromGeneral(Configuration.Dimensions.ZKCONNECT);
        String zkPath = configuration.getFromGeneral(Configuration.Dimensions.ZKPATH);
        final ZkTasksHandler zkTasksHandler = new ZkTasksHandler(zkConnect, zkPath);

        RecoverFromFailed recoverFromFailed = new RecoverFromFailed() {
            @Override
            public List<Task> recover() {
                try {
                    configuration.readConfiguration();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                return configuration.getSnmpTasks();
            }
        };

        zkTasksHandler.setRecoverFromFailed(recoverFromFailed);

        LinkedBlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        final SnmpManager snmpManager = new SnmpManager(queue);
        final KafkaManager kafkaManager = new KafkaManager(queue);

        /* List oft SnmpTasks */
        zkTasksHandler.addListener(snmpManager);

        kafkaManager.start();
        snmpManager.start();

        zkTasksHandler.setTasks(configuration.getSnmpTasks());
        zkTasksHandler.wakeup();

        log.info("SnmpServer is started!");
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                log.info("Starting shutdown ...");
                kafkaManager.shutdown();
                snmpManager.shutdown();
            }
        });

        Signal.handle(new Signal("HUP"), new SignalHandler() {
            public void handle(Signal signal) {
                try {
                    log.info("Starting reload ...");
                    configuration.readConfiguration();
                    List<Task> tasks = configuration.getSnmpTasks();
                    zkTasksHandler.setTasks(tasks);
                    zkTasksHandler.wakeup();
                    log.info("Reload end!");

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}