package net.redborder.snmp.workers;

import net.redborder.snmp.tasks.SnmpTask;

/**
 * Created by jjgarcia on 05/10/15.
 */
public abstract class Worker extends Thread {
    public abstract void shutdown();
    public abstract SnmpTask getSnmpTask();
}
