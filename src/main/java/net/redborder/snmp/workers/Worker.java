package net.redborder.snmp.workers;

/**
 * Created by jjgarcia on 05/10/15.
 */
public abstract class Worker extends Thread {
    public abstract void shutdown();
}
