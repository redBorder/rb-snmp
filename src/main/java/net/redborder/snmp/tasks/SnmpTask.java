package net.redborder.snmp.tasks;

import net.redborder.clusterizer.MappedTask;

import java.util.Map;

public class SnmpTask extends MappedTask {


    public SnmpTask(){

    }

    public SnmpTask(Map<? extends String, ? extends Object> m) {
        initialize(m);
    }

    public Integer getPullingTime() {
        return getData("pullingTime") == null ? 5 * 60 : (Integer) getData("pullingTime");
    }

    public void setPullingTime(Integer pullingTime) {
        setData("pullingTime", pullingTime);
    }

    public String getType() {
        return getData("type");
    }

    public void setType(String type) {
        setData("type", type);
    }

    public void setPort(String port) {
        setData("port", port);
    }

    public String getPort() {
        return getData("port");
    }

    public void setIP(String ip) {
        setData("ip", ip);
    }

    public String getIP() {
        return getData("ip");
    }

    public void setCommunity(String community) {
        setData("community", community);
    }

    public String getCommunity() {
        return getData("community");
    }

    public void setEnrichment(Map<String, Object> enrichment) {
        setData("enrichment", enrichment);
    }

    public Map<String, Object> getEnrichment() {
        return getData("enrichment");
    }

    public String getUUID(){
        return getIP() + getCommunity() + getPullingTime() + getEnrichment();
    }
}
