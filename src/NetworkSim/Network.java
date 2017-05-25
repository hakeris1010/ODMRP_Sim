package NetworkSim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A Network object containing the thread which runs all nodes, firing events when needed.
 */
public class Network {
    private final List<Node> netNodes = Collections.synchronizedList( new ArrayList<>() );
    private final BlockingQueue<Node> activeNodes = new LinkedBlockingQueue<>();
    private final Thread scheduleThread;
    private AtomicLong refreshTime = new AtomicLong(0);
    private AtomicBoolean endRequest = new AtomicBoolean(false);

    private AtomicLong refreshCount = new AtomicLong(0);
    private AtomicLong successProcCount = new AtomicLong(0);

    private final static long MAX_REFRESH = 2;
    private final static long MAX_SUCCESSFULL_PROCESSINGS = 9;

    {
        // The Schedule thread. Runs all nodes, and schedules updates.
        scheduleThread = new Thread(() -> {
            while(!endRequest.get()){
                refreshTime.set(System.currentTimeMillis()+1); // Minimum time when refresh will be needed.
                // Call nodes to check their Times Until Refresh, and add themselves into the activeNodes
                // queue if they need refresh now.
                for(int i = 0; i < netNodes.size() && !endRequest.get(); i++){ // Iterate like this for synchronization.
                    long tm = netNodes.get(i).checkIfProcessingNeeded();
                    if(tm > System.currentTimeMillis()+1 && tm < refreshTime.get()) {
                        refreshTime.set(tm);
                    }
                    else if(tm - System.currentTimeMillis() <= 0) // On Refresh!!!
                        refreshCount.incrementAndGet();
                }

                // Now process all active nodes.
                while(!activeNodes.isEmpty() && !endRequest.get()) {
                    if(activeNodes.poll().process())
                        successProcCount.incrementAndGet();
                }
                // No active nodes left - wait until next refresh.
                if(!endRequest.get()) {
                    synchronized (this) {
                        long refTime = refreshTime.get() - System.currentTimeMillis();
                        if (refTime > 0) {
                            try {
                                this.wait(refTime);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                // DEBUG:
                if(refreshCount.get() >= MAX_REFRESH){
                    endRequest.set(true);
                    synchronized (this){
                        this.notify();
                    }
                }
            }
        });
    }

    public Network(){ }

    public void startNetwork(){
        // Add nodes to network.
        /* Topology - simple:
           A - B - C
                 \ D
         */

        Node A = new Node(activeNodes, "192.168.0.101", null, Arrays.asList("224.0.0.2"), null);
        Node B = new Node(activeNodes, "192.168.0.100", null);
        Node C = new Node(activeNodes, "192.168.0.102", null, Arrays.asList("224.0.0.1"), null);
        //Node D = new Node(activeNodes, "192.168.0.103", null, Arrays.asList("224.0.0.1"), null);

        netNodes.add(A);
        netNodes.add(B);
        netNodes.add(C);
        //netNodes.add(D);

        A.connectNode(B);
        B.connectNode(C);

        System.out.println();
        for(Node i : netNodes){
            System.out.println(i);
        }

        scheduleThread.start();

        // Wait X ms, and stop.
        try {
            Thread.sleep(900);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        endRequest.set(true);
        synchronized (this){
            this.notify();
        }

        //Wait until MAX_REFRESH refreshes happened.
        /*try {
            synchronized (this) {
                this.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/

        // At the end of 100 millisecond Join Query propagation, check the Routing Tables.
        System.out.println("\n===============================\nPropagation End!\nRouting tables:\n");
        for(Node i : netNodes){
            System.out.println("\n["+i.getIpAddress()+"]: Routing Table:\n"+ i.getRoutingTable() );
        }
    }
}
