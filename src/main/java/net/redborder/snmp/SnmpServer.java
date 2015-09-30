package net.redborder.snmp;

import net.redborder.clusterizer.ZkTasksHandler;
import net.redborder.snmp.managers.SnmpManager;
import net.redborder.snmp.util.Configuration;
import sun.misc.Signal;
import sun.misc.SignalHandler;

public class SnmpServer {
    public static void main(String[] args) {
        Configuration configuration = Configuration.getConfiguration();

        String zkConnect = configuration.getFromGeneral(Configuration.Dimensions.ZKCONNECT);
        String zkPath = configuration.getFromGeneral(Configuration.Dimensions.ZKPATH);
        ZkTasksHandler zkTasksHandler = new ZkTasksHandler(zkConnect, zkPath);
        SnmpManager snmpManager = new SnmpManager();

        zkTasksHandler.addListener(snmpManager);

        /* List oft SnmpTasks */
       // zkTasksHandler.setTasks();


        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                // Shutdown
            }
        });

        Signal.handle(new Signal("HUP"), new SignalHandler() {
            public void handle(Signal signal) {
                // Reload
            }
        });
    }
}