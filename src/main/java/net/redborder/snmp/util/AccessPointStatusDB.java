package net.redborder.snmp.util;

import java.util.HashMap;
import java.util.Map;

public class AccessPointStatusDB {
    private Map<String, Map<String, Object>> cache = new HashMap<>();

    public void addCache(String macAddress, Map<String, Object> accessPointData) {
        cache.put(macAddress, accessPointData);
    }

    public Map<String, Map<String, Object>> getAccessPoints() {
        return cache;
    }
}
