package net.redborder.snmp.managers;

import net.redborder.clusterizer.Task;
import net.redborder.clusterizer.TasksChangedListener;
import net.redborder.snmp.util.AccessPointDB;
import net.redborder.snmp.workers.SnmpWorker;
import net.redborder.snmp.tasks.SnmpTask;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class SnmpManager extends Thread implements TasksChangedListener {

    private List<SnmpTask> tasks = new ArrayList<>();
    private List<String> workersUuids = new ArrayList<>();
    private LinkedBlockingQueue<Map<String, Object>> queue;
    private Map<String, SnmpWorker> workers = new HashMap<>();
    private Object run = new Object();
    volatile AtomicBoolean running = new AtomicBoolean(false);

    public SnmpManager(LinkedBlockingQueue<Map<String, Object>> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        running.set(true);
        while (running.get()) {
            try {
                synchronized (run) {
                    run.wait();
                }

                for (SnmpTask task : tasks){
                    String uuid = task.getIP() + "_" + task.getCommunity();
                    workersUuids.add(uuid);
                    if (!workers.containsKey(uuid)) {
                        SnmpWorker worker = new SnmpWorker(task, queue);
                        workers.put(uuid, worker);
                        worker.start();
                    }
                }

                for (String workerUuid : workers.keySet()) {
                    if (!workersUuids.contains(workerUuid)) {
                        workers.get(workerUuid).shutdown();
                        workers.remove(workerUuid);
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public void shutdown(){
        running.set(false);
        synchronized (run) {
            run.notifyAll();
        }

        for (SnmpWorker worker : workers.values()){
            worker.shutdown();
        }
    }

    @Override
    public void updateTasks(List<Task> list) {
        for (Task t : list) {
            SnmpTask snmpTask = (SnmpTask) t;
            tasks.add(snmpTask);
        }

        synchronized (run) {
            run.notifyAll();
        }
    }
}
