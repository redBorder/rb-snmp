package net.redborder.snmp.util;

import java.util.HashMap;
import java.util.Map;

public class InterfacesFlowsDB {
    private Map<String, Map<String, Long>> cache = new HashMap<>();

    public void addCache(String interfaceOID, Map<String, Long> flows) {
        cache.put(interfaceOID, flows);
    }

    public Map<String, Long> getFlows(String interfaceOID){
        return cache.get(interfaceOID);
    }
}
