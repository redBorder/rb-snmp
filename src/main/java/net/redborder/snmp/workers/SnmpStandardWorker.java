package net.redborder.snmp.workers;


import net.redborder.snmp.tasks.SnmpTask;
import net.redborder.snmp.util.InterfacesFlowsDB;
import net.redborder.snmp.util.SnmpOID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TableEvent;
import org.snmp4j.util.TableUtils;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SnmpStandardWorker extends Worker {

    final Logger log = LoggerFactory.getLogger(SnmpStandardWorker.class);
    SnmpTask snmpTask;
    InterfacesFlowsDB cache = new InterfacesFlowsDB();

    LinkedBlockingQueue<Map<String, Object>> queue;
    Long pullingTime;
    Long last_time;

    volatile AtomicBoolean running = new AtomicBoolean(false);

    public SnmpStandardWorker(SnmpTask snmpTask, LinkedBlockingQueue<Map<String, Object>> queue) {
        this.snmpTask = snmpTask;
        this.queue = queue;
        this.pullingTime = snmpTask.getPullingTime().longValue();
    }

    @Override
    public void run() {
        try {
            running.set(true);
            log.info("{} - Start snmp worker: {} with community: {}", snmpTask.getIP(), snmpTask.getIP(), snmpTask.getCommunity());

            TransportMapping transport = new DefaultUdpTransportMapping();
            Snmp snmp = new Snmp(transport);
            transport.listen();

            // Setting up target
            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString(snmpTask.getCommunity()));
            target.setAddress(new UdpAddress(String.format("%s/%s", snmpTask.getIP(), snmpTask.getPort())));
            target.setRetries(3);
            target.setTimeout(3000);
            target.setVersion(SnmpConstants.version2c);

            // Simple PDU
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(SnmpOID.Standard.IF_NUMBER));
            pdu.setType(PDU.GET);
            pdu.setRequestID(new Integer32(1));

            ResponseEvent response = snmp.get(pdu, target);

            if (response != null) {
                PDU responsePDU = response.getResponse();

                if (responsePDU != null) if (responsePDU.getErrorStatus() == PDU.noError) {
                    VariableBinding varBinding = (VariableBinding) responsePDU.getVariableBindings().get(0);
                    int numOfInterfaces = Integer.valueOf(varBinding.getVariable().toString());

                    log.info("SNMP response: Number of interfaces {}", numOfInterfaces);

                    while (running.get()) {
                        Long start = System.currentTimeMillis();
                        Long timeStart = start / 1000L;
                        TableUtils utils = new TableUtils(snmp, new DefaultPDUFactory(PDU.GETBULK));
                        List<TableEvent> results = utils.getTable(target, new OID[]{
                                SnmpOID.Standard.IF_DESCRIPTION,
                                SnmpOID.Standard.IF_MAC,
                                SnmpOID.Standard.IF_OPER_STATUS,
                                SnmpOID.Standard.IF_IN_OCTETS,
                                SnmpOID.Standard.IF_OUT_OCTETS
                        }, null, null);

                        log.info("Getting data for {} interfaces.", results.size());
                        Long exec_time = (System.currentTimeMillis() - start) / 1000;
                        log.info("SNMP response in {} ms.", (System.currentTimeMillis() - start));

                        List<Map<String, Object>> devicesData = new ArrayList<>();
                        List<String> devicesMacAddress = new ArrayList<>();
                        Set<String> filter = snmpTask.getFilter();

                        log.info("Applying filter for interfaces : {}", filter);
                        int counter = 1;
                        timeStart = timeStart - (timeStart % 60);

                        for (TableEvent event : results) {
                            if (filter.contains(String.valueOf(counter)) || filter.isEmpty()) {
                                VariableBinding[] varBindings = event.getColumns();
                                String ifName = varBindings[0].getVariable().toString();
                                String macAddress = varBindings[1].getVariable().toString();

                                if (macAddress != null && !macAddress.equals("")) {
                                    devicesMacAddress.add(macAddress);

                                    Map<String, Object> deviceData = new HashMap<>();
                                    Map<String, Long> data = cache.getFlows(ifName);

                                    Long sendData = Long.valueOf(varBindings[4].getVariable().toString());
                                    Long recvData = Long.valueOf(varBindings[3].getVariable().toString());

                                    if (data == null) {
                                        Map<String, Long> newData = new HashMap<>();
                                        newData.put("devInterfaceSentBytes", sendData);
                                        newData.put("devInterfaceRecvBytes", recvData);
                                        deviceData.put("validForStats", false);
                                        cache.addCache(ifName, newData);
                                    } else {
                                        deviceData.put("validForStats", true);
                                        deviceData.put("devInterfaceSentBytes", sendData - data.get("devInterfaceSentBytes"));
                                        deviceData.put("devInterfaceRecvBytes", recvData - data.get("devInterfaceRecvBytes"));

                                        Map<String, Long> newData = new HashMap<>();
                                        newData.put("devInterfaceSentBytes", sendData);
                                        newData.put("devInterfaceRecvBytes", recvData);
                                        cache.addCache(ifName, newData);
                                    }

                                    deviceData.put("sensorIp", snmpTask.getIP());
                                    deviceData.put("enrichment", snmpTask.getEnrichment());
                                    deviceData.put("first_switched", last_time);
                                    deviceData.put("timestamp", timeStart);
                                    deviceData.put("type", "ap-stats");
                                    deviceData.put("devName", ifName);
                                    deviceData.put("devInterfaceMac", macAddress);
                                    deviceData.put("devStatus", parseStatus(varBindings[2].getVariable().toString()));


                                    devicesData.add(deviceData);
                                    filter.remove(String.valueOf(counter));
                                }
                            }

                            counter++;
                        }

                        log.debug("Result : {}", devicesData);

                        try {
                            for (Map<String, Object> interfaceData : devicesData) {
                                queue.put(interfaceData);
                            }

                            if ((pullingTime - exec_time) > 0)
                                TimeUnit.SECONDS.sleep(pullingTime - exec_time);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        last_time = timeStart;
                    }
                }
            }

            snmp.close();
            transport.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String parseStatus(String status) {
        String parsedStatus;

        if (status.equals("1"))
            parsedStatus = "up";
        else if (status.equals("2"))
            parsedStatus = "down";
        else if (status.equals("3"))
            parsedStatus = "testing";
        else if (status.equals("4"))
            parsedStatus = "unknown";
        else if (status.equals("5"))
            parsedStatus = "dormant";
        else if (status.equals("6"))
            parsedStatus = "notPresent";
        else
            parsedStatus = "lowerLayerDown";

        return parsedStatus;
    }

    @Override
    public void shutdown() {
        running.set(false);
    }

    @Override
    public SnmpTask getSnmpTask() {
        return snmpTask;
    }
}
