package net.redborder.snmp.util;

import java.util.HashMap;
import java.util.Map;

public class AccessPointDB {
    private Map<String, Map<String, Long>> cache = new HashMap<>();

    public void addCache(String accessPoint, Map<String, Long> flows) {
        cache.put(accessPoint, flows);
    }

    public Map<String, Long> getFlows(String accessPoint){
        return cache.get(accessPoint);
    }
}
