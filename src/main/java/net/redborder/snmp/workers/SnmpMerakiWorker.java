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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SnmpMerakiWorker extends Worker {
    final Logger log = LoggerFactory.getLogger(SnmpMerakiWorker.class);

    public SnmpTask snmpTask;
    LinkedBlockingQueue<Map<String, Object>> queue;
    InterfacesFlowsDB cache = new InterfacesFlowsDB();
    Long pullingTime;
    volatile AtomicBoolean running = new AtomicBoolean(false);
    Long last_time;


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
                Long timeStart = start / 1000L;
                DefaultPDUFactory defaultPDUFactory = new DefaultPDUFactory();
                TreeUtils treeUtils = new TreeUtils(snmp, defaultPDUFactory);
                List<TreeEvent> events = new ArrayList<>();
                Boolean errorResponse = false;

                for (OID oid : SnmpOID.Meraki.toList()) {
                    List<TreeEvent> singleEvents = treeUtils.getSubtree(target, oid);
                    for (TreeEvent singleEvent : singleEvents) {
                        errorResponse = singleEvent.isError();
                        if (errorResponse)
                            break;
                    }
                    if (errorResponse) {
                        break;
                    } else {
                        events.addAll(singleEvents);
                    }
                }
                if (!errorResponse) {
                    log.info("{} - Getting from SNMP: {}  - content: {}", snmpTask.getIP(), snmpTask.getIP(), !events.isEmpty());

                    Map<String, String> results = new HashMap<>();

                    // Get snmpwalk result.
                    for (TreeEvent event : events) {
                        if (event != null) {
                            if (!event.isError()) {
                                VariableBinding[] varBindings = event.getVariableBindings();

                                for (VariableBinding varBinding : varBindings) {
                                    results.put(varBinding.getOid().toString(), varBinding.getVariable().toString());
                                }
                            }
                        }
                    }

                    List<String> interfacesOIDs = getInterfacesOIDs(results);
                    timeStart = timeStart - (timeStart % 60);

                    List<Map<String, Object>> interfacesData = getInterfacesData(timeStart, results, interfacesOIDs);

                    log.info("{} - SNMP accesPointsInterfaces from {} count: {}", snmpTask.getIP(), snmpTask.getIP(), interfacesData.size());
                    log.info("{} - SNMP response in {} ms.", snmpTask.getIP(), (System.currentTimeMillis() - start));

                    try {
                        for (Map<String, Object> interfaceData : interfacesData) {
                            queue.put(interfaceData);
                        }
                        TimeUnit.SECONDS.sleep(pullingTime);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage(), e);
                    }
                } else {
                    log.warn("{} - No response from host: {}, community: {}", snmpTask.getIP(), snmpTask.getIP(), snmpTask.getCommunity());
                    try {
                        TimeUnit.SECONDS.sleep(1L);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
            snmp.close();
            transport.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public SnmpTask getSnmpTask() {
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

    public List<Map<String, Object>> getInterfacesData(Long timeStart, Map<String, String> results, List<String> interfacesOIDs) {

        List<Map<String, Object>> interfacesData = new ArrayList<>();
        Map<String, Long> totalBytes = new HashMap<>();

        for (String interfaceOID : interfacesOIDs) {
            Map<String, Long> interfaceCache = cache.getFlows(interfaceOID);
            Map<String, Object> interfaceData = new HashMap<>();
            Map<String, Long> interfaceFlows = new HashMap<>();

            if (results.get(SnmpOID.Meraki.DEV_INTERFACE_SENT_PKTS + "." + interfaceOID) == null ||
                    results.get(SnmpOID.Meraki.DEV_INTERFACE_RECV_PKTS + "." + interfaceOID) == null ||
                    results.get(SnmpOID.Meraki.DEV_INTERFACE_SENT_BYTES + "." + interfaceOID) == null ||
                    results.get(SnmpOID.Meraki.DEV_INTERFACE_RECV_BYTES + "." + interfaceOID) == null) {
                cache.removeCache(interfaceOID);
                log.warn("Some traffic data is null remove the interface OID {}", interfaceOID);
            } else {

                String ap = interfaceOID.substring(0, interfaceOID.lastIndexOf("."));

                interfaceData.put("interfaceOID", interfaceOID);
                interfaceData.put("sensorIp", snmpTask.getIP());
                interfaceData.put("enrichment", snmpTask.getEnrichment());
                interfaceData.put("first_switched", last_time);
                interfaceData.put("timestamp", timeStart);

                interfaceData.put("devName", results.get(SnmpOID.Meraki.DEV_NAME + "." + ap));
                interfaceData.put("devNetworkName", results.get(SnmpOID.Meraki.DEV_NETWORK_NAME + "." + ap));

                interfaceData.put("devStatus", parseStatus(results.get(SnmpOID.Meraki.DEV_STATUS + "." + ap)));

                String macAddress = results.get(SnmpOID.Meraki.DEV_INTERFACE_MAC + "." + interfaceOID);
                if (totalBytes.get(macAddress) == null) totalBytes.put(macAddress, 0L);

                if (!interfacesData.contains(macAddress)) {
                    interfaceData.put("devClientCount", results.get(SnmpOID.Meraki.DEV_CLIENT_COUNT + "." + ap));
                }

                interfaceData.put("devInterfaceMac", macAddress);
                interfaceData.put("devInterfaceName", results.get(SnmpOID.Meraki.DEV_INTERFACE_NAME + "." + interfaceOID));

                if (!(interfaceData.get("devInterfaceName").toString().toLowerCase().contains("wired") ||
                        interfaceData.get("devInterfaceName").toString().toLowerCase().contains("wifi"))) {
                    interfaceData.put("validForStats", false);
                } else {
                    interfaceData.put("validForStats", true);
                }

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
                    // devInterfaceSentPktsDiff =
                    //         Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_SENT_PKTS + "." + interfaceOID)) +
                    //                 MAX - interfaceCache.get("devInterfaceSentPkts");

                    log.warn("{} - Overflow SentPkts setting to 0", snmpTask.getIP());
                    devInterfaceSentPktsDiff = 0L;
                }

                log.debug("{} - PktSent Mac [" + macAddress + "] Cache [{}] Event [{}] Diff = " + devInterfaceSentPktsDiff, snmpTask.getIP(), interfaceCache.get("devInterfaceSentPkts"), devInterfaceSentPkts);

                Long devInterfaceRecvPkts =
                        Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_RECV_PKTS + "." + interfaceOID));
                Long devInterfaceRecvPktsDiff = devInterfaceRecvPkts - interfaceCache.get("devInterfaceRecvPkts");
                if (devInterfaceRecvPktsDiff < 0) {
                    // devInterfaceRecvPktsDiff =
                    //        Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_RECV_PKTS + "." + interfaceOID)) +
                    //                MAX - interfaceCache.get("devInterfaceRecvPkts");

                    log.warn("{} - Overflow RecvPkts setting to 0", snmpTask.getIP());
                    devInterfaceRecvPktsDiff = 0L;
                }

                log.debug("{} - PktRecv Mac [" + macAddress + "] Cache [{}] Event [{}] Diff = " + devInterfaceRecvPktsDiff, snmpTask.getIP(), interfaceCache.get("devInterfaceRecvPkts"), devInterfaceRecvPkts);

                Long devInterfaceSentBytes =
                        Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_SENT_BYTES + "." + interfaceOID));
                Long devInterfaceSentBytesDiff = devInterfaceSentBytes - interfaceCache.get("devInterfaceSentBytes");
                if (devInterfaceSentBytesDiff < 0) {
                    // devInterfaceSentBytesDiff =
                    //        Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_SENT_BYTES + "." + interfaceOID)) +
                    //                MAX - interfaceCache.get("devInterfaceSentBytes");

                    log.warn("{} - Overflow SentBytes setting to 0", snmpTask.getIP());
                    devInterfaceSentBytesDiff = 0L;
                }

                log.debug("{} - BytesSent Mac [" + macAddress + "] Cache [{}] Event [{}] Diff = " + devInterfaceSentBytesDiff, snmpTask.getIP(), interfaceCache.get("devInterfaceSentBytes"), devInterfaceSentBytes);

                Long devInterfaceRecvBytes =
                        Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_RECV_BYTES + "." + interfaceOID));
                Long devInterfaceRecvBytesDiff = devInterfaceRecvBytes - interfaceCache.get("devInterfaceRecvBytes");
                if (devInterfaceRecvBytesDiff < 0) {
                    // devInterfaceRecvBytesDiff =
                    //        Long.parseLong(results.get(SnmpOID.Meraki.DEV_INTERFACE_RECV_BYTES + "." + interfaceOID)) +
                    //                MAX - interfaceCache.get("devInterfaceRecvBytes");

                    log.warn("{} - Overflow RecvBytes setting to 0", snmpTask.getIP());
                    devInterfaceRecvBytesDiff = 0L;
                }

                log.debug("{} - BytesRecv Mac [" + macAddress + "] Cache [{}] Event [{}] Diff = " + devInterfaceRecvBytesDiff, snmpTask.getIP(), interfaceCache.get("devInterfaceRecvBytes"), devInterfaceRecvBytes);


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
        }

        for (Map.Entry<String, Long> entry : totalBytes.entrySet()) {
            log.info("{} - AP: {}, KBytes: {}", snmpTask.getIP(), entry.getKey(), entry.getValue() / 1024);
        }

        log.info("FIRST {}  NOW {}", last_time, timeStart);
        last_time = timeStart;
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

