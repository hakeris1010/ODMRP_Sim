package NetworkSim;

import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;

import java.lang.reflect.Array;
import java.util.*;
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
                        //Logger.logfn("[ScheduleThread]: entering wait state for time: "+(refreshTime.get() - System.currentTimeMillis()));
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

                /*
                // DEBUG:
                if(refreshCount.get() >= MAX_REFRESH){
                    endRequest.set(true);
                    synchronized (this){
                        this.notify();
                    }
                }*/
            }
        });
    }

    public Network(){ }

    /**
     * Adds a node to the current network nodes, and connects with the specified neighbors
     * @param node - a new node to add to da network.
     * @param neighbors - neighbor nodes
     * @return the node just added.
     */
    private Node addNetNode(Node node, final List<Node> neighbors){
        System.out.println("Adding node:"+node);
        if(neighbors != null) {
            for (Node n : neighbors) {
                node.connectNode(n);
            }
        }
        netNodes.add(node);
        return node;
    }

    private Node getNodeByIP(String ip){
        for(int i=0; i<netNodes.size(); i++){
            if(netNodes.get(i).getIpAddress().equals(ip))
                return netNodes.get(i);
        }
        return null;
    }

    private Node addNetNode(Node node) throws Node.NodeConnectException{
        System.out.println("Adding node:"+node);
        if(node!=null && getNodeByIP(node.getIpAddress()) == null)
            netNodes.add(node);
        else throw new Node.NodeConnectException("Node exists!");
        return node;
    }

    /**
     * starts the Scheduler Thread, and runs UI on current thread.
     */
    public void startNetwork(){
        scheduleThread.start();
        runUserInterface();
        //startNetworkTest();
    }

    private void startNetworkTest(){
        // Add nodes to network.
        /* Topology - simple:
           A - B - C
                 \ |
                   D - E
         */

        // Starting the Scheduler.
        scheduleThread.start();

        try {
            Node A=null, B=null, C=null, D=null, E=null;

            // Add and connect nodes.
            B = addNetNode(new Node(activeNodes, "192.168.0.100", "224.0.0.1"));
            Thread.sleep(200);

            A = addNetNode(new Node(activeNodes, "192.168.0.101", "224.0.0.2", Arrays.asList("224.0.0.1"),
                    Collections.singletonList(B) ));
            Thread.sleep(200);

            C = addNetNode(new Node(activeNodes, "192.168.0.102", null, null,
                    Collections.singletonList(B) ));
            Thread.sleep(200);

            D = addNetNode(new Node(activeNodes, "192.168.0.103", null, Arrays.asList("224.0.0.2"),
                    Arrays.asList(B, C) ));
            Thread.sleep(200);

            E = addNetNode(new Node(activeNodes, "192.168.0.104", null, Arrays.asList("224.0.0.1") ,
                    Collections.singletonList(D) ));

            Thread.sleep(1000);

            // Send 1st round packets.
            System.out.println("\n= = = = = = = = = = = = = = = = = = = = =\nSENDING IP PACKETS!!!\n");
            IPPacket pack = new IPPacket(Packet.CastMode.UNICAST, A.getIpAddress(), E.getIpAddress(), 16, "Unicast packet", 0, true);
            A.sendPacket(pack);

            System.out.println("\n= = = = = = = = = = = = = = = = = = = = =\nSENDING MultiCast IP PACKETS!!!\n");
            pack = new IPPacket(Packet.CastMode.MULTICAST, B.getIpAddress(), B.getMulticastSourceAddress(), 16, "Multicast packet", 0, true);
            B.sendPacket(pack);

            Thread.sleep(500);

            //showNodeData("\n********************\nNode routing tables BEFORE REMOVAL:\n**********************\n");

            // Remove some nodes.
            B.disconnectAllNodes();

            Thread.sleep(1000);

            endRequest.set(true);
            synchronized (this){
                this.notify();
            }

        } catch ( Exception e ) {
            System.out.println("\n^^^^^^^^^^^^^^^\nException occured: " + e);
        } finally {
            // At the end of Packet Scheduler propagation, check the Routing Tables.
            showNodeData("Propagation End! Node routing tables:");
        }
    }

    private void showNodeData(String msg){
        System.out.println("\n===============================\n"+msg+"\n");
        for(Node i : netNodes){
            System.out.println(i.customToString(true, false, true, true, true));
        }
    }

    /** ==================== UI Land =======================
     * Runs the Console UI to interactively control the network.
     */
    public void runUserInterface(){
        System.out.println("For help type \"help\" or \"h\"");
        Scanner scn = new Scanner( System.in );
        boolean quit = false;
        while( !quit ){
            System.out.print("\n>> ");
            try{
                Iterator<String> inp = Arrays.asList( scn.nextLine().split("\\s+") ).iterator();
                if(inp.hasNext()) {
                    switch (inp.next()) {
                        case "exit":
                        case "e":
                            quit = true;
                            break;

                        case "help":
                        case "h":
                            System.out.println( displayHelp() );
                            break;

                        case "add":
                        case "a":
                            Node node = new Node(activeNodes);
                            while(inp.hasNext()){
                                String cur = inp.next(), IP = inp.next();
                                if(IP.matches(Packet.IPV4_MULTICAST_REGEX)){
                                    if(cur.equals("-ms"))
                                         node.setMulticastSourceAddress(IP);
                                    else if(cur.equals("-mg"))
                                         node.addMulticastGroup(IP);
                                    else throw new InputMismatchException(cur);
                                }
                                else if(IP.matches(Packet.IPV4_REGEX_STRING)){
                                     if(cur.equals("-ip"))
                                         node.setIpAddress(IP);
                                     else if(cur.equals("-n") && node.isReady())
                                         node.connectNode( getNodeByIP(IP) );
                                     else throw new InputMismatchException(cur);
                                }
                                else throw new InputMismatchException(IP);
                            }
                            if(!node.isReady())
                                System.out.println("Node is not ready! IP was not supplied!");
                            else
                                addNetNode( node );
                            break;

                        case "query":
                        case "q":
                            if((node = getNodeByIP( inp.next() )) != null ){
                                System.out.println("\n-----------------------------------------\nNode data:\n"+
                                node.customToString(true, true, true, true, true) );
                            }
                            break;

                        case "list":
                        case "l":
                            System.out.println("\nListing all nodes in the network:");
                            for(Node n : netNodes){
                                System.out.print(" ["+n.getIpAddress()+"] -> ");
                                for(String m : n.getNeigborIPs())
                                    System.out.print( m + " " );
                                System.out.println();
                            }
                            break;

                        case "send":
                        case "s":
                            String s1 = inp.next(), src, dest;
                            if(s1.equals("-v"))
                                src = inp.next();
                            else
                                src = s1;
                            dest = inp.next();
                            if(!src.matches(Packet.IPV4_REGEX_STRING) || !dest.matches(Packet.IPV4_REGEX_STRING))
                                throw new InputMismatchException("Wrong IP Address.");
                            if((node = getNodeByIP(src)) == null)
                                throw new InputMismatchException("Wrong source!");

                            String payld = "";
                            while(inp.hasNext()){
                                payld += " " + inp.next();
                            }
                            if(payld.equals(""))
                                payld = "Nice packet";

                            IPPacket pack = new IPPacket(Packet.getAddressType(dest), src, dest, 16, payld, 0, false);
                            System.out.println("Sending IP Packet from "+pack.sourceAddr+" to "+pack.destAddr);
                            pack.verbose = s1.equals("-v");
                            node.sendPacket(pack);

                            break;

                        case "route":
                        case "ro":
                            src = inp.next();
                            dest = inp.next();
                            // System.out.println("Src.IP: "+src+", Dst.IP: "+dest);
                            node = getNodeByIP(src);
                            if(node==null)
                                throw new NoSuchElementException("Node with ip "+src+" does not exist!");
                            Routing.RoutingEntry ror = node.getNextHopToDestination( dest );
                            if(ror == null)
                                System.out.println("No route to destination!");
                            else
                                System.out.println("dst: "+ror.destinationAddress+", nextHop: "+ror.nextHopAddress+", cost: "+ror.cost);
                            break;

                        case "connect":
                        case "c":
                            Node n2, n1 = getNodeByIP(inp.next());
                            if(n1 == null)
                                throw new NoSuchElementException();
                            while(inp.hasNext()){
                                n2 = getNodeByIP( inp.next() );
                                if(n2!=null)
                                    n1.connectNode(n2);
                                else
                                    throw new NoSuchElementException("No such node!");
                            }
                            break;

                        case "remove":
                            Node n = getNodeByIP(inp.next());
                            System.out.println("Removing node: "+n.getIpAddress());
                            n.disconnectAllNodes();
                            netNodes.remove(n);
                            break;

                        default:
                            throw new NoSuchElementException("No valid command found!");
                    }
                }

            } catch (InputMismatchException e){
                System.out.println("Wrong input format! "+e.getMessage());
            } catch (NoSuchElementException e){
                System.out.println("Illegal input! Maybe try \"help\"? : "+e.getMessage());
            } catch (IllegalStateException e){
                System.out.println("Fatal exception occured: "+e);
                break;
            } catch (Node.NodeConnectException e){
                System.out.println(e);
            }
        }
        // Stop the network simulation.
        System.out.println("Posting quit message to the Network Scheduler...");
        endRequest.set(true);
        synchronized (this){
            this.notify();
        }
    }

    public String displayHelp(){
        return "Available commands:\n add (a) -ip IP [-ms MultiCastSrc] [-mg MulticastGroups]... [-n Neighbors]... - add node\n " +
                "query (q) node - query node info:\n route (ro) NODE DEST - query route to destination DEST\n " +
                "list (l) - list IP addresses of all nodes on network\n " +
                "remove (r) node - remove node from network\n connect (c) NODE node1 node2... - connect NODE with " +
                "nodes node1, node2, ...\n disconnect (d) NODE node1 node2... - disconnect nodes node1, node2... " +
                "from node NODE.\n send (s) [-v] src dest - send an IP packet from node src to dest, if v - verbose. " +
                "Multicast IP addresses can be specified too.\n help (h) - display this message.\n " +
                "exit (e) - quits the simulation.\n";
    }

}
