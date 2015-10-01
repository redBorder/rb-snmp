package net.redborder.snmp.util;


import net.redborder.clusterizer.Task;
import net.redborder.snmp.tasks.SnmpTask;
import org.ho.yaml.Yaml;
import org.snmp4j.Snmp;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Configuration {
    private final String CONFIG_FILE_PATH = "/opt/rb/etc/rb-snmp/config.yml";
    private Map<String, Object> general;
    private List<Map<String, Object>> sensors;
    public static Configuration configuration = new Configuration();

    List<Task> snmpTasks = new ArrayList<>();

    public static Configuration getConfiguration() {
        try {
            configuration.readConfiguration();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return configuration;
    }

    public void readConfiguration() throws FileNotFoundException {
        Map<String, Object> map = (Map<String, Object>) Yaml.load(new File(CONFIG_FILE_PATH));
        general = (Map<String, Object>) map.get("general");
        sensors = (List<Map<String, Object>>) map.get("sensors");

        for (Map<String, Object> sensor : sensors) {
            String type = (String) sensor.get("type");
            if (!type.equals("MERAKI") || !type.equals("WLC")) {
                SnmpTask snmpTask = new SnmpTask();

                snmpTask.setType(type);
                snmpTask.setPort((String) sensor.get("port"));
                snmpTask.setIP((String) sensor.get("ip_address"));
                snmpTask.setCommunity((String) sensor.get("community"));
                snmpTask.setEnrichment((Map<String, Object>) sensor.get("enrichment"));

                snmpTasks.add(snmpTask);
            }
        }
    }

    public List<Task> getSnmpTasks(){
        return snmpTasks;
    }

    public <T> T getFromGeneral(String property) {
        T ret = null;

        if (general != null) {
            ret = (T) general.get(property);
        }

        return ret;
    }

    public static class Dimensions {
        public static final String ZKCONNECT = "zk_connect";
        public static final String ZKPATH = "zk_path";
        public static final String KAFKABROKERS = "kafka_brokers";
    }
}
