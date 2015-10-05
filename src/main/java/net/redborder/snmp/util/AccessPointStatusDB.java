package net.redborder.snmp.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccessPointStatusDB {
    private List<Map<String, Object>> cache = new ArrayList<>();

    public void addCache(String macAddress, Map<String, Object> accessPointData) {
        Map<String, Object> accessPoint = new HashMap<>();
        accessPoint.put(macAddress, accessPointData);
        cache.add(accessPoint);
    }

    public List<Map<String, Object>> getAccessPoint() {
        return cache;
    }
}
