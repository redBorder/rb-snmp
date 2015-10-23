package net.redborder.snmp.managers;

import net.redborder.clusterizer.Task;
import net.redborder.clusterizer.TasksChangedListener;
import net.redborder.snmp.workers.SnmpMerakiWorker;
import net.redborder.snmp.tasks.SnmpTask;
import net.redborder.snmp.workers.SnmpWLCWorker;
import net.redborder.snmp.workers.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class SnmpManager extends Thread implements TasksChangedListener {
    private static final Logger log = LoggerFactory.getLogger(KafkaManager.class);

    private List<SnmpTask> tasks = new ArrayList<>();
    private List<String> currentTasksUUIDs = new ArrayList<>();
    private LinkedBlockingQueue<Map<String, Object>> queue;
    private Map<String, Worker> workers = new HashMap<>();
    private Object run = new Object();
    volatile AtomicBoolean running = new AtomicBoolean(false);

    public SnmpManager(LinkedBlockingQueue<Map<String, Object>> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        log.info("KafkaManager is started!");

        running.set(true);
        while (running.get()) {
            try {
                synchronized (run) {
                    run.wait();
                }

                for (SnmpTask task : tasks){
                    String uuid = task.getUUID();
                    currentTasksUUIDs.add(uuid);
                    if (!workers.containsKey(uuid)) {
                        log.info("Starting {}", uuid);

                        if(task.getType().toUpperCase().equals("MERAKI")){
                            SnmpMerakiWorker worker = new SnmpMerakiWorker(task, queue);
                            workers.put(uuid, worker);
                            worker.start();
                        } else {
                            SnmpWLCWorker worker = new SnmpWLCWorker(task, queue);
                            workers.put(uuid, worker);
                            worker.start();
                        }
                    }
                }

                List<String> keys = Arrays.asList(workers.keySet().toArray(new String[workers.size()]));

                for (String workerUuid : keys) {
                    if (!currentTasksUUIDs.contains(workerUuid)) {
                        log.info("Removing {}", workerUuid);
                        Worker worker = workers.remove(workerUuid);
                        worker.shutdown();
                    }
                }

            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }

            List<String> runningTask = new ArrayList<>();
            for(Worker worker : workers.values()){
                runningTask.add(worker.getSnmpTask().getUUID());
            }

            currentTasksUUIDs.clear();
            log.info("Running task: {} ", runningTask);
        }

    }

    public void shutdown(){
        running.set(false);
        synchronized (run) {
            run.notifyAll();
        }

        for (Worker worker : workers.values()){
            worker.shutdown();
        }
    }

    @Override
    public void updateTasks(List<Task> list) {
        log.info("Update {} tasks", list.size());
        tasks.clear();
        for (Task t : list) {
            SnmpTask snmpTask = new SnmpTask(t.asMap());
            tasks.add(snmpTask);
        }

        synchronized (run) {
            run.notifyAll();
        }
    }
}
