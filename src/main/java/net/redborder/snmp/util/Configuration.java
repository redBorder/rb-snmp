package net.redborder.snmp.util;


import org.ho.yaml.Yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;

public class Configuration {
    private final String CONFIG_FILE_PATH = "/opt/rb/etc/rb-zkcmd/config.yml";
    private Map<String, Object> _general;

    public void readConfiguration() throws FileNotFoundException {
        Map<String, Object> map = (Map<String, Object>) Yaml.load(new File(CONFIG_FILE_PATH));
        _general = (Map<String, Object>) map.get("general");
    }


    public <T> T getFromGeneral(String property) {
        T ret = null;

        if (_general != null) {
            ret = (T) _general.get(property);
        }

        return ret;
    }

    public static class Dimensions {
        public static final String ZKCONNECT = "zk_connect";
        public static final String ZKPATH = "zk_path";

    }
}
