package net.redborder.snmp.workers;

import net.redborder.snmp.tasks.SnmpTask;
import net.redborder.snmp.util.AccessPointDB;
import net.redborder.snmp.util.SnmpOID;
import org.snmp4j.CommunityTarget;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SnmpWorker extends Thread {
    SnmpTask snmpTask;
    ExecutorService executorService = Executors.newFixedThreadPool(5);
    LinkedBlockingQueue<Map<String, Object>> queue;
    AccessPointDB cache = new AccessPointDB();
    Long pullingTime = 5 * 60L;


    public SnmpWorker(SnmpTask snmpTask, LinkedBlockingQueue<Map<String, Object>> queue) {
        this.snmpTask = snmpTask;
        this.queue = queue;
        this.pullingTime = snmpTask.getPullingTime();
    }

    @Override
    public void run() {
        try {

            Address targetAddress = GenericAddress.parse(snmpTask.getIP() + "/" + snmpTask.getPort());
            TransportMapping transport = new DefaultUdpTransportMapping();
            Snmp snmp = new Snmp(transport);
            transport.listen();


            // setting up target
            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString(snmpTask.getCommunity()));
            target.setAddress(targetAddress);
            target.setRetries(3);
            target.setTimeout(1000 * 3);
            target.setVersion(SnmpConstants.version2c);

            while (true) {
                Long start = System.currentTimeMillis();
                DefaultPDUFactory defaultPDUFactory = new DefaultPDUFactory();
                TreeUtils treeUtils = new TreeUtils(snmp, defaultPDUFactory);
                List<TreeEvent> events = new ArrayList<>();

                for (OID oid : SnmpOID.toList()) {
                    events.addAll(treeUtils.getSubtree(target, oid));
                }

                Map<String, String> results = new HashMap<>();

                // Get snmpwalk result.
                for (TreeEvent event : events) {
                    if (event != null) {
                        if (!event.isError()) {
                            VariableBinding[] varBindings = event.getVariableBindings();

                            for (VariableBinding varBinding : varBindings) {
                                results.put(varBinding.getOid().toString(), varBinding.getVariable().toString());
                            }
                        } else {
                            // TODO
                        }
                    } else {
                        System.out.println("Event: null");
                        // TODO
                    }
                }
                System.out.println("Executed in " + (System.currentTimeMillis() - start) + "ms.");

                List<String> devices = getDevicesOIDs(results);
                List<Map<String, List<String>>> devicesInterfaces = getDevicesInterfacesOIDs(results, devices);
                List<Map<String, Object>> accessPoints = getAccessPoints(results, devicesInterfaces);
                System.out.println("MAPA: " + accessPoints);


                try {
                    for(Map<String, Object> acessPoint : accessPoints) {
                        queue.put(acessPoint);
                    }

                    TimeUnit.SECONDS.sleep(pullingTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            // TODO
        }
    }

    public List<String> getDevicesOIDs(Map<String, String> results) {
        List<String> devices = new ArrayList<>();

        for (String key : results.keySet()) {
            if (key.contains(SnmpOID.DEV_MAC.toString() + ".")) {
                devices.add(key.replace(SnmpOID.DEV_MAC.toString() + ".", ""));
            }
        }
        return devices;
    }

    public List<Map<String, List<String>>> getDevicesInterfacesOIDs(Map<String, String> results, List<String> devices) {
        List<Map<String, List<String>>> devicesInterfacesOIDs = new ArrayList<>();
        for (String device : devices) {
            Map<String, List<String>> deviceInterfacesOIDs = new HashMap<>();
            List<String> interfacesOIDs = new ArrayList<>();
            for (String key : results.keySet()) {
                if (key.contains(SnmpOID.DEV_INTERFACE_SENT_PKTS.toString() + "." + device + ".")) {
                    interfacesOIDs.add(key.replace(SnmpOID.DEV_INTERFACE_SENT_PKTS.toString() + "." + device + ".", ""));
                }

            }
            deviceInterfacesOIDs.put(device, interfacesOIDs);
            devicesInterfacesOIDs.add(deviceInterfacesOIDs);
        }
        return devicesInterfacesOIDs;
    }

    public String parseStatus(String status) {
        String parsedStatus;
        if (status.equals("1"))
            parsedStatus = "ON";
        else
            parsedStatus = "OFF";
        return parsedStatus;
    }

    public List<Map<String, Object>> getAccessPoints(Map<String, String> results, List<Map<String, List<String>>> devicesInterfaces) {

        List<Map<String, Object>> accessPoints = new ArrayList<>();

        for (Map<String, List<String>> deviceMap : devicesInterfaces) {
            Map<String, Object> accessPoint = new HashMap<>();

            Map.Entry<String, List<String>> entry = deviceMap.entrySet().iterator().next();
            String deviceOID = entry.getKey();
            List<String> interfacesOIDs = entry.getValue();
            if (!interfacesOIDs.isEmpty()) {
                Long sentPkts = 0L;
                Long recvPkts = 0L;
                Long sentBytes = 0L;
                Long recvBytes = 0L;

                accessPoint.put("mac_address", results.get(SnmpOID.DEV_MAC + "." + deviceOID));
                accessPoint.put("name", results.get(SnmpOID.DEV_NAME + "." + deviceOID));
                accessPoint.put("ip_address", results.get(SnmpOID.DEV_IP + "." + deviceOID));
                accessPoint.put("clients", results.get(SnmpOID.DEV_CLIENT_COUNT + "." + deviceOID));
                accessPoint.put("status", parseStatus(results.get(SnmpOID.DEV_STATUS + "." + deviceOID)));

                for (String interfaceOID : interfacesOIDs) {
                    sentPkts += Long.parseLong(results.get(SnmpOID.DEV_INTERFACE_SENT_PKTS + "." + deviceOID + "." + interfaceOID));
                    recvPkts += Long.parseLong(results.get(SnmpOID.DEV_INTERFACE_RECV_PKTS + "." + deviceOID + "." + interfaceOID));
                    sentBytes += Long.parseLong(results.get(SnmpOID.DEV_INTERFACE_SENT_BYTES + "." + deviceOID + "." + interfaceOID));
                    recvBytes += Long.parseLong(results.get(SnmpOID.DEV_INTERFACE_RECV_BYTES + "." + deviceOID + "." + interfaceOID));
                }

                Map<String, Long> accessPointCache = cache.getFlows((String) accessPoint.get("mac_address"));

                if (accessPointCache == null) {
                    accessPoint.put("is_first", true);
                } else {
                    accessPoint.put("is_first", false);
                    accessPoint.put("sent_pkts", sentPkts - accessPointCache.get("sent_pkts"));
                    accessPoint.put("recv_pkts", recvPkts - accessPointCache.get("recv_pkts"));
                    accessPoint.put("sent_bytes", sentBytes - accessPointCache.get("sent_bytes"));
                    accessPoint.put("recv_bytes", recvBytes - accessPointCache.get("recv_bytes"));
                }

                Map<String, Long> accessPointFlows = new HashMap<>();
                accessPointFlows.put("sent_pkts", sentPkts);
                accessPointFlows.put("recv_pkts", recvPkts);
                accessPointFlows.put("sent_bytes", sentBytes);
                accessPointFlows.put("recv_bytes", recvBytes);

                cache.addCache((String) accessPoint.get("mac_address"), accessPointFlows);

                accessPoint.put("sent_pkts", sentPkts);
                accessPoint.put("recv_pkts", recvPkts);
                accessPoint.put("sent_bytes", sentBytes);
                accessPoint.put("recv_bytes", recvBytes);

                accessPoint.put("sensor_ip", snmpTask.getIP());
                accessPoint.put("enrichment", snmpTask.getEnrichment());

                accessPoints.add(accessPoint);
            }
        }

        return accessPoints;
    }
}

