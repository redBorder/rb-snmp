package net.redborder.snmp.workers;

import net.redborder.snmp.tasks.SnmpTask;
import net.redborder.snmp.util.InterfacesFlowsDB;
import net.redborder.snmp.util.SnmpOID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SnmpMerakiWorker extends Worker {
    final Logger log = LoggerFactory.getLogger(SnmpMerakiWorker.class);

    public SnmpTask snmpTask;
    LinkedBlockingQueue<Map<String, Object>> queue;
    InterfacesFlowsDB cache = new InterfacesFlowsDB();
    Long pullingTime;
    final Long MAX = 4294967296L;
    volatile AtomicBoolean running = new AtomicBoolean(false);


    public SnmpMerakiWorker(SnmpTask snmpTask, LinkedBlockingQueue<Map<String, Object>> queue) {
        this.snmpTask = snmpTask;
        this.queue = queue;
        this.pullingTime = snmpTask.getPullingTime().longValue();
    }

    @Override
    public void run() {
        try {
            running.set(true);
            log.info("Start snmp worker: {} with community: {}", snmpTask.getIP(), snmpTask.getCommunity());

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

            while (running.get()) {
                Long start = System.currentTimeMillis();
                DefaultPDUFactory defaultPDUFactory = new DefaultPDUFactory();
                TreeUtils treeUtils = new TreeUtils(snmp, defaultPDUFactory);
                List<TreeEvent> events = new ArrayList<>();

                for (OID oid : SnmpOID.Meraki.toList()) {
                    events.addAll(treeUtils.getSubtree(target, oid));
                }

                log.info("Getting from SNMP: {}  - content: {}", snmpTask.getIP(), !events.isEmpty());

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
                        // TODO
                    }
                }

                List<String> interfacesOIDs = getInterfacesOIDs(results);
                List<Map<String, Object>> interfacesData = getInterfacesData(results, interfacesOIDs);

                log.info("SNMP accesPointsInterfaces from {} count: {}", snmpTask.getIP(), interfacesData.size());
                log.info("SNMP response in {} ms.", (System.currentTimeMillis() - start));

                try {
                    for (Map<String, Object> interfaceData : interfacesData) {
                        queue.put(interfaceData);
                    }
                    TimeUnit.SECONDS.sleep(pullingTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            snmp.close();
            transport.close();
        } catch (IOException e) {
            e.printStackTrace();
            // TODO
        }
    }

    @Override
    public SnmpTask getSnmpTask(){
        return snmpTask;
    }

    public List<String> getInterfacesOIDs(Map<String, String> results) {
        List<String> interfacesOIDs = new ArrayList<>();

        for (String key : results.keySet()) {
            if (key.contains(SnmpOID.Meraki.DEV_INTERFACE_MAC.toString() + ".")) {
                interfacesOIDs.add(key.replace(SnmpOID.Meraki.DEV_INTERFACE_MAC.toString() + ".", ""));
            }
        }

        return interfacesOIDs;
    }

    public List<Map<String, Object>> getInterfacesData(Map<String, String> results, List<String> interfacesOIDs) {

        List<Map<String, Object>> interfacesData = new ArrayList<>();
        Map<String, Long> totalBytes = new HashMap<>();

        for (String interfaceOID : interfacesOIDs) {
            Map<String, Long> interfaceCache = cache.getFlows(interfaceOID);
            Map<String, Object> interfaceData = new HashMap<>();
            Map<String, Long> interfaceFlows = new HashMap<>();

            String ap = interfaceOID.substring(0, interfaceOID.lastIndexOf("."));

            interfaceData.put("interfaceOID", interfaceOID);
            interfaceData.put("sensorIp", snmpTask.getIP());
            interfaceData.put("enrichment", snmpTask.getEnrichment());
            interfaceData.put("timeSwitched", pullingTime);

            interfaceData.put("devName", results.get(SnmpOID.Meraki.DEV_NAME + "." + ap));
            interfaceData.put("devStatus", parseStatus(results.get(SnmpOID.Meraki.DEV_STATUS + "." + ap)));

            String macAddress = results.get(SnmpOID.Meraki.DEV_INTERFACE_MAC + "." + interfaceOID);
            if (totalBytes.get(macAddress) == null) totalBytes.put(macAddress, 0L);

            if (!interfacesData.contains(macAddress)) {
                interfaceData.put("devClientCount", results.get(SnmpOID.Meraki.DEV_CLIENT_COUNT + "." + ap));
            }

            interfaceData.put("devInterfaceMac", macAddress);
            interfaceData.put("devInterfaceName", results.get(SnmpOID.Meraki.DEV_INTERFACE_NAME + "." + interfaceOID));

            interfaceData.put("validForStats", true);

            if (interfaceCache == null) {
                interfaceCache = new HashMap<>();
                interfaceData.put("validForStats", false);
                interfaceCache.put("devInterfaceSentPkts", 0L);
                interfaceCache.put("devInterfaceRecvPkts", 0L);
                interfaceCache.put("devInterfaceSentBytes", 0L);
                interfaceCache.put("devInterfaceRecvBytes", 0L);
            }

            Long devInterfaceSentPkts =
                    Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_SENT_PKTS + "." + interfaceOID));
            Long devInterfaceSentPktsDiff = devInterfaceSentPkts - interfaceCache.get("devInterfaceSentPkts");
            if (devInterfaceSentPktsDiff < 0) {
                devInterfaceSentPktsDiff =
                        Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_SENT_PKTS + "." + interfaceOID)) +
                                MAX - interfaceCache.get("devInterfaceSentPkts");
            }

            Long devInterfaceRecvPkts =
                    Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_RECV_PKTS + "." + interfaceOID));
            Long devInterfaceRecvPktsDiff = devInterfaceRecvPkts - interfaceCache.get("devInterfaceRecvPkts");
            if (devInterfaceRecvPktsDiff < 0) {
                devInterfaceRecvPktsDiff =
                        Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_RECV_PKTS + "." + interfaceOID)) +
                                MAX - interfaceCache.get("devInterfaceRecvPkts");
            }

            Long devInterfaceSentBytes =
                    Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_SENT_BYTES + "." + interfaceOID));
            Long devInterfaceSentBytesDiff = devInterfaceSentBytes - interfaceCache.get("devInterfaceSentBytes");
            if (devInterfaceSentBytesDiff < 0) {
                devInterfaceSentBytesDiff =
                        Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_SENT_BYTES + "." + interfaceOID)) +
                                MAX - interfaceCache.get("devInterfaceSentBytes");
            }

            Long devInterfaceRecvBytes =
                    Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_RECV_BYTES + "." + interfaceOID));
            Long devInterfaceRecvBytesDiff = devInterfaceRecvBytes - interfaceCache.get("devInterfaceRecvBytes");
            if (devInterfaceRecvBytesDiff < 0) {
                devInterfaceRecvBytesDiff =
                        Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_RECV_BYTES + "." + interfaceOID)) +
                                MAX - interfaceCache.get("devInterfaceRecvBytes");
            }

            totalBytes.put(macAddress, totalBytes.get(macAddress) + devInterfaceSentBytesDiff + devInterfaceRecvBytesDiff);

            interfaceFlows.put("devInterfaceSentPkts", devInterfaceSentPktsDiff);
            interfaceFlows.put("devInterfaceRecvPkts", devInterfaceRecvPktsDiff);
            interfaceFlows.put("devInterfaceSentBytes", devInterfaceSentBytesDiff);
            interfaceFlows.put("devInterfaceRecvBytes", devInterfaceRecvBytesDiff);

            interfaceCache.put("devInterfaceSentPkts", devInterfaceSentPkts);
            interfaceCache.put("devInterfaceRecvPkts", devInterfaceRecvPkts);
            interfaceCache.put("devInterfaceSentBytes", devInterfaceSentBytes);
            interfaceCache.put("devInterfaceRecvBytes", devInterfaceRecvBytes);

            cache.addCache(interfaceOID, interfaceCache);

            interfaceData.putAll(interfaceFlows);
            interfacesData.add(interfaceData);
        }

        for (Map.Entry<String, Long> entry : totalBytes.entrySet()){
            log.trace("AP: {}, Bytes: {} Mbs", entry.getKey(), entry.getValue() / 1024 / 1024);
        }

        return interfacesData;
    }

    public void shutdown() {
        this.running.set(false);
    }

    public String parseStatus(String status) {
        String parsedStatus;
        if (status.equals("1"))
            parsedStatus = "on";
        else
            parsedStatus = "off";
        return parsedStatus;
    }
}

