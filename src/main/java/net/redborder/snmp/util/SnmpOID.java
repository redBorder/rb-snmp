package net.redborder.snmp.util;

import org.snmp4j.smi.OID;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SnmpOID {
    public static class Meraki {
        public static final OID DEV_NAME = new OID("1.3.6.1.4.1.29671.1.1.4.1.2");
        public static final OID DEV_STATUS = new OID("1.3.6.1.4.1.29671.1.1.4.1.3");
        public static final OID DEV_CLIENT_COUNT = new OID("1.3.6.1.4.1.29671.1.1.4.1.5");

        public static final OID DEV_INTERFACE_MAC = new OID("1.3.6.1.4.1.29671.1.1.5.1.1");
        public static final OID DEV_INTERFACE_NAME = new OID("1.3.6.1.4.1.29671.1.1.5.1.3");
        public static final OID DEV_INTERFACE_SENT_PKTS = new OID("1.3.6.1.4.1.29671.1.1.5.1.4");
        public static final OID DEV_INTERFACE_RECV_PKTS = new OID("1.3.6.1.4.1.29671.1.1.5.1.5");
        public static final OID DEV_INTERFACE_SENT_BYTES = new OID("1.3.6.1.4.1.29671.1.1.5.1.6");
        public static final OID DEV_INTERFACE_RECV_BYTES = new OID("1.3.6.1.4.1.29671.1.1.5.1.7");

        public static List<OID> toList() {
            return Arrays.asList(DEV_INTERFACE_MAC, DEV_INTERFACE_NAME, DEV_NAME, DEV_STATUS, DEV_CLIENT_COUNT,
                    DEV_INTERFACE_RECV_BYTES, DEV_INTERFACE_SENT_BYTES, DEV_INTERFACE_SENT_PKTS, DEV_INTERFACE_RECV_PKTS);
        }
    }

    public static class Ruckus {
        public static final OID DEV_NAME = new OID("1.3.6.1.4.1.25053.1.2.2.1.1.2.1.1.10");
        public static final OID DEV_MAC = new OID("1.3.6.1.4.1.25053.1.2.2.1.1.2.1.1.1");
        public static final OID DEV_STATUS = new OID("1.3.6.1.4.1.25053.1.2.2.1.1.2.1.1.3");
        public static final OID DEV_CLIENT_COUNT = new OID("1.3.6.1.4.1.25053.1.2.2.1.1.2.1.1.15");

        public static final OID DEV_CLIENT_SENT_DATA = new OID("1.3.6.1.4.1.25053.1.2.2.1.1.2.1.1.61");
        public static final OID DEV_CLIENT_RECV_DATA = new OID("1.3.6.1.4.1.25053.1.2.2.1.1.2.1.1.62");

        public static List<OID> toList() {
            return Arrays.asList(DEV_NAME,DEV_MAC, DEV_STATUS, DEV_CLIENT_COUNT, DEV_CLIENT_SENT_DATA, DEV_CLIENT_RECV_DATA);
        }
    }

    public static class WirelessLanController {
        public static final OID DEV_MAC = new OID("1.3.6.1.4.1.9.9.513.1.2.3.1.1");
        public static final OID DEV_NAME = new OID("1.3.6.1.4.1.9.9.513.1.1.1.1.5");
        public static final OID DEV_CLIENTS_COUNT = new OID("1.3.6.1.4.1.9.9.513.1.1.1.1.54");
        public static final OID DEV_LOAD_CHANNEL_UTILIZATION = new OID("1.3.6.1.4.1.14179.2.2.13.1.3");

        public static List<OID> toList() {
            return Arrays.asList(DEV_MAC, DEV_NAME, DEV_CLIENTS_COUNT, DEV_LOAD_CHANNEL_UTILIZATION);
        }
    }

    public static class Standard {
        public static final OID IF_NUMBER = new OID("1.3.6.1.2.1.2.1.0");
        // Interface name
        public static final OID IF_DESCRIPTION = new OID("1.3.6.1.2.1.2.2.1.2");
        // MAC address
        public static final OID IF_MAC = new OID("1.3.6.1.2.1.2.2.1.6");
        // Operation status
        public static final OID IF_OPER_STATUS = new OID("1.3.6.1.2.1.2.2.1.8");
        // Interface Input Octets
        public static final OID IF_IN_OCTETS = new OID("1.3.6.1.2.1.2.2.1.10");
        // Interface Output Octets
        public static final OID IF_OUT_OCTETS = new OID("1.3.6.1.2.1.2.2.1.16");

        public static Set<String> FILTER = new HashSet<>();

        public static List<OID> toList() {
            return Arrays.asList(IF_NUMBER, IF_DESCRIPTION, IF_MAC, IF_OPER_STATUS, IF_IN_OCTETS, IF_OUT_OCTETS);
        }
    }
}
