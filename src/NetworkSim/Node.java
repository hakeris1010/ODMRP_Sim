package NetworkSim;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This network simulation demonstrates the working of ODMRP:
 * The On-Demand Multicast Routing Protocol (Ad-Hoc Mesh-based Multi/Unicast RP.
 * - RFC Draft: https://tools.ietf.org/html/draft-ietf-manet-odmrp-04
 * - No official RFC number is assigned because protocol is experimental.
 *
 * This simulation implements the most basic version of ODMRP (Nodes are stationary,
 * no GPS is used, no Passive Clustering (PC) is used).
 *
 * Typical scenario of node joining an ODMRP-routed ad-hoc mesh network:
 *
 * 1. Node discovers it's neighbors by wireless advertising or by other means
 *    - Layers: Physical, Data Link (MAC).
 *
 * 2. Node asks some of it's neighbors (nodes it has direct connection with)
 *    to connect it to their network. Authentication and stuff takes place.
 *    - Layers: Network. In case of authentication, Layers 4 and/or 5 might be utilised too.
 *
 * 3. Node builds it's routing and multicast/forward tables according to
 *    the routing protocol being used.
 *
 *    In case of ODMRP (That we're developing):
 *
 *   3.1. If current node has packets to send (multicast or unicast), and doesn't know any
 *        routes, it advertises itself and at the same time requests routes by broadcasting a
 *        Join Query packet.
 *
         - The neighbors broadcast this Join Query to their neighbors, so the query
 *         propagates through the whole Ad-Hoc network.
 *
 *       - This way current node advertises it's presence on the network, so other nodes
 *         could update their routing tables for route to this node.
 *
 *       - The multicast-interested nodes send the Join Replies to current node, which has
 *         became the multicast-source. The reply propagation process builds a Forwarding group
 *         for routing of multicast packets belonging to this group.
 *
 *   3.1.1. If Node has Multicast Packets available, it sends a Join Query to it's neighbors,
 *          specifying it's IP as Source Address, and the specific Multicast Group address.
 *
 *   3.1.2. If node wants to Unicast a packet, but no route is known, in Join Query, it
 *          specifies the Unicast Destination IP in the Multicast Group Address field.
 *          - This way, when the node with that Destination IP gets this Join Query, it
 *            broadcasts a Join Reply. This way current node gets the route needed.
 *
 *          - TODO: This mechanism will be updated with the UnicastRoute flag in Join Query.
 *            This way the first node knowing the route to destination will respond and no
 *            longer broadcast.
 *
 *   3.2. Join Replies from multicast receivers reach the current node, and current node
 *        updates it's routing table.
 *        - Then it can send the multicast traffic through the now-updated forwarding group.
 *        - While the Replies are propagating through a network, the nodes which process them
 *          update their routing tables and forwarding group memberships.
 *
 *   3.3. When all routes are determined, the current node sends it's packets over the network.
 *
 *        - In case of Multicasting, the packets are broadcasted, and intermediate nodes check
 *          the source IP (multicast group's address).
 *          If intermediate node is part of the packet's Multicast address's Forwarding Group,
 *          it broadcasts the packet to it's neighbors, except the node it came from.
 *
 *        - In Unicasting, packet is forwarded according to the Routing Table.
 *
 *   3.4. All nodes must periodically send Join Queries to update the routes and forwarding
 *        groups in this ad-hoc network.
 *
 *  =========================================================================================
 *
 *  - In our network model, we presume Static IP and Multicast addresses.
 *
 *  - Node data processing must happen on a schedule thread. No processing is invoked directly
 *    when nodes communicate between themselves - all data sent to other nodes is first put
 *    into the pendingReceivePackets queue.
 *
 *  - When scheduler calls process() method on a node, it processes the packets it has got
 *    in a queue, and updates timers and stuff.
 *
 *  - Note that process() won't run long tasks, it only runs 1 "operation", like join query
 *    or reply processing, or packet forwarding.
 *
 */

public class Node implements Comparable {

    public static class NodeConnectException extends RuntimeException{
        NodeConnectException(){}
        NodeConnectException(String what){ super(what); }
    }

    // Is node working at the moment
    private AtomicBoolean down = new AtomicBoolean(false), isReady = new AtomicBoolean(false);

    // Node's IP (assigned by itself (?))
    private String ipAddress;

    // This node's Multicast Source group address.
    private String multicastSourceAddress;

    // The addresses of multicast groups this node is part of.
    private final List<String> multicastGroups = new ArrayList<>();
    private final Set<String> multicastReceivers = new TreeSet<>();

    // The nodes this node is directly connected to.
    private final List<Node> neighbors = Collections.synchronizedList(new ArrayList<>());

    // The tables and other structures of Routing Protocol being used (in this case ODMRP).
    private final ODMRP_Proto odmrp = new ODMRP_Proto();

    // Pending packet queues
    private final Queue<Packet> pendingReceivePackets = new ConcurrentLinkedQueue<>();
    private final Queue<Packet> pendingSendPackets = new ConcurrentLinkedQueue<>();

    // A set containing routes we are requesting at the moment, it helps to avoid loops.
    private final SortedSet<String> routeRequestCache = new TreeSet<>();

    // If this is set, we must broadcast the join query next turn.
    private ODMRP_Proto.JoinQueryPacket joinQueryNext = null;
    private boolean sendReceiveModeToggle = false;

    private static final int PENDING_PACKET_QUESIZE = 256;

    // Blocking Queue of Active Nodes. Used by the Scheduler for determining which nodes need processing
    private final AtomicReference<BlockingQueue<Node>> activeNodes = new AtomicReference<>();

    /**
     * Constructors.
     * We can construct this node by specifying the nodes to connect to, too.
     */
    public Node(BlockingQueue<Node> actNodeQueue){
        activeNodes.set(actNodeQueue);
    }
    public Node(BlockingQueue<Node> actNodeQueue, String ip, String multicastIp){
        activeNodes.set(actNodeQueue);
        ipAddress = ip;
        multicastSourceAddress = multicastIp;
        multicastGroups.add(ipAddress); // Our IP is also a multicast group.
        this.isReady.set(true);
    }
    public Node(BlockingQueue<Node> actNodeQueue, String ip, String multicastIp, List<String> groups, List<Node> connectNodes){
        this(actNodeQueue, ip, multicastIp);
        if(groups!=null)
            multicastGroups.addAll(groups);
        if(connectNodes!=null){
            for(Node i : connectNodes){
                this.connectNode(i);
            }
        }
    }

    /** ==================================================================================
     * Overrides.
     */
    @Override
    public String toString(){
        return customToString(true, true, false, false, false);
    }

    @Override
    public int compareTo(Object o){ // Compare only by IP Addresses.
        if(o instanceof Node)
            return this.ipAddress.compareTo(((Node)o).ipAddress);
        return 0xdeadbeef; // Not even a node.
    }

    /** =====================================================================
     * Node Property Modification API.
     * - Simple getters and setters for basic properties.
     * - Can be called from inside or outside of the Node.
     */
    public String getIpAddress(){ return ipAddress; }
    public String getMulticastSourceAddress(){ return multicastSourceAddress; }
    public String[] getParticipatedMulticastGroups(){ return (String[])(multicastGroups.toArray()); }

    public void setIpAddress(String ip){
        if(!this.isReady.get()) {
            ipAddress = ip;
            this.isReady.set(true);
        }
    }
    public void setMulticastSourceAddress(String adr){
        multicastSourceAddress = adr;
    }

    public boolean isReady(){ return this.isReady.get(); }

    public void addMulticastGroup(String... groupAddr){
        multicastGroups.addAll(Arrays.asList(groupAddr));
    }
    public boolean gotPendingReceivePackets(){ return !pendingReceivePackets.isEmpty(); }
    public boolean gotPendingSendPackets(){ return !pendingSendPackets.isEmpty(); }

    public String getRoutingTable(){ return odmrp.routingTableToString(); }
    public String getForwardingGroupTable(){ return odmrp.forwardingTableToString(); }

    public String[] getNeigborIPs(){
        ArrayList<String> lst = new ArrayList<>();
        for(int i=0; i<neighbors.size(); i++){
            lst.add( neighbors.get(i).getIpAddress() );
        }
        return (String[])(lst.toArray());
    }

    /**
     * Adds new node to the current node's network.
     * - Typically called by the node that wants to add itself, after discovering this
     *   node by using this node's advertisement data.
     * - Authentication and stuff happens there.
     * - Notice that this method adds node at Level 3 (Network).
     * @param add - if true, node will be addes. If false, removed.
     * @param node - the node to add to current node's network.
     * @param firstWay - true if first-way connection is being made.
     *                 False if first-way already made, and making back-way connection.
     */
    private void addRemoveNodePriv(boolean add, Node node, boolean firstWay) throws NodeConnectException {
        if(!this.isReady.get()) return;

        // Add only if node hasn't got same IP as this, and if there isn't a neighbor with the IP of node being added.
        if(node!=null && !node.ipAddress.equals(this.ipAddress) && (add ? !neighbors.contains(node) : neighbors.contains(node))) {
            if(add) neighbors.add(node);
            else neighbors.remove(node);

            if(firstWay)
                node.addRemoveNodePriv(add, this, false);
            Logger.logfn("["+this.ipAddress+"] connected with ["+node.ipAddress+"]");
        }
        else {
            throw new NodeConnectException("Connecting node "+this.ipAddress+" to node "+(node!=null ? node.ipAddress : "(null)"));
        }
    }

    /**
     * Public front-end to connectNodePriv()
     */
    public void connectNode(Node node){
        addRemoveNodePriv(true, node, true);
    }

    /**
     * Public front end to node disconnecting.
     */
    public void disconnectNode(Node node){
        addRemoveNodePriv(false, node, true);
    }

    public void disconnectAllNodes(){
        if(!this.isReady.get()) return;
        for(int i=0; i<neighbors.size(); i++){
            addRemoveNodePriv(false, neighbors.get(i), true);
        }
    }

    /**
     * Checks time left until refresh is needed. If needed, pushes itself into the activeNodes queue.
     * Used by Schedule Thread to determine active nodes based on refresh needed.
     * @return time in millis when refresh will be needed.
     */
    long checkIfProcessingNeeded(){
        if(!this.isReady.get()) return 0;

        long time = odmrp.getLastRouteRefresh() + ODMRP_Proto.DEFAULT_ROUTE_REFRESH;
        if(time < System.currentTimeMillis() || !pendingReceivePackets.isEmpty() || !pendingSendPackets.isEmpty() )
            activeNodes.get().add(this);
        return time;
    }

    /**
     * Sets the new Active Node Queue
     */

    /** =====================================================================
     *  Node Action-Invoking API.
     *  - These methods must be called from the Schedule Thread, or anywhere outside the Node.
     *
     *  Process the pending data and/or update state.
     *  Executes exactly ONE Routing Operation, which may be (but not limited to) one of these:
     *  - Pending send data:
     *    - no route is known -> broadcast Join Query.
     *    - route is known -> send multi/unicast data to specific routes.
     *  - pending receive data: packet is:
     *    - Join Query - if Multicast Receiver, send Join Reply, and broadcast the query.
     *    - Join Reply - check the next hop and stuff, update stuff, send.
     *    - Multicast Data - if Forwarding Group, forward where needed.
     *    - Unicast Data - send according to Routing Table.
     *  - route refresh timer value approached -> broadcast Join Query
     *  @return true, if active operation was processed, false if no operation was processed.
     */
    public boolean process(){
        if(!this.isReady.get()) return false;

        Logger.logfn("\n["+ipAddress+"]: Processing.");
        Packet recPack = null, sentPack = null;

        // Firstly, check if we gotta send Join Queries, by analyzing timers and if there
        // was a query specified on last iteration.
        if(joinQueryNext != null || odmrp.isRouteRefreshNeeded()){
            if(joinQueryNext == null){ // Timer approached -> broadcast JQ with our Multicast Source.
                joinQueryNext =  prepareJoinQuery(multicastSourceAddress);
            }
            // Add to msg.cache.
            odmrp.addMessageCacheEntry(new ODMRP_Proto.MessageCacheEntry(joinQueryNext.sourceAddr, joinQueryNext.sequenceNumber));

            Logger.logfn("Broadcasting Join Query! "+ joinQueryNext);

            broadcastPacket(joinQueryNext, null);

            // At the end, reset Route Refresh timer, and set joinQueryNext to null.
            odmrp.resetLastRouteRefresh();
            joinQueryNext = null;
        }
        else {
            // At this moment, when no high-priority ODMRP queries are pending, we analyze pending send/receive packets.

            boolean gottaSend = !pendingSendPackets.isEmpty() && (pendingReceivePackets.isEmpty() || sendReceiveModeToggle);

            // Send IP packets (The packets this node originates and sends to the network).
            // Notice that ONLY IP PACKETS can be added to the PendingSendPacks Queue.
            //if (gottaSend) {
            if(!pendingSendPackets.isEmpty() && (sentPack = pendingSendPackets.poll()) instanceof IPPacket ) {
                IPPacket sendPack = (IPPacket)sentPack;
                Logger.logfn("Sending packet!" + sendPack);

                // Check the sendPacket mode. If BroadCast or MultiCast, broadcast.
                if (sendPack.mode == Packet.CastMode.MULTICAST || sendPack.mode == Packet.CastMode.BROADCAST) {
                    broadcastPacket(sendPack, null);
                }
                // For unicasted sendPackets, route according to routing table.
                else if (sendPack.mode == Packet.CastMode.UNICAST) {
                    // Firstly check if the route for this sendPacket is unknown, but already being searched for.
                    if (!routeRequestCache.contains(sendPack.destAddr)) {
                        // If no successful route found, we need to repair our routing table by broadcasting a JQ.
                        if (!routePacket(sendPack)) {
                            // Put the UnSent sendPacket at the end of the Queue for resending when route is known,
                            // and add the destination to routes currently being requested. Request a route on next iteration
                            // by a Join Query. Note that on Multicast Group field we specify the Destination IP of the sendPacket,
                            // so the node with that IP will send back a Join Reply.
                            Logger.logfn("No valid route found for packet!");
                            //pendingSendPackets.offer(sendPack);
                            //routeRequestCache.add(sendPack.destAddr);
                            joinQueryNext = prepareJoinQuery(sendPack.destAddr);
                        }
                    } else { // If sendPacket's route is being searched for, we can move to Receive Packet queue.
                        pendingSendPackets.offer(sendPack);
                        gottaSend = false;
                    }
                }
            }
            // Check receive packets, if there are pending and no sending were done.
            if (!pendingReceivePackets.isEmpty()) {
                recPack = pendingReceivePackets.poll();

                // Check the type of recPacket got: Join Query, Join Reply, IP Data.
                if (recPack instanceof ODMRP_Proto.JoinQueryPacket) {
                    ODMRP_Proto.JoinQueryPacket odp = (ODMRP_Proto.JoinQueryPacket) recPack;
                    Logger.logfn("Got ODMRP Join Query Packet! SeqNum: "+odp.sequenceNumber);

                    // Check if it's in message cache. If not, broadcast.
                    if (!odmrp.isEntryInMessageCache(new ODMRP_Proto.MessageCacheEntry(odp.sourceAddr, odp.sequenceNumber))) {
                        Logger.logf("Packet is Not in Message Cache: " + odp);

                        // Add a message entry, and update routes.
                        odmrp.addMessageCacheEntry(new ODMRP_Proto.MessageCacheEntry(odp.sourceAddr, odp.sequenceNumber));
                        odmrp.addRoutingEntry(new Routing.RoutingEntry(odp.sourceAddr, odp.previousHopIP));

                        // If we're part of Query's Multicast Group, make a Join Reply to the Source of the
                        // Multicast Group of this query, and broadcast it.
                        if (multicastGroups.contains(odp.multicastGroupIP)) {
                            Logger.logfn("We got a Query for a Group We Are Part Of! Broadcasting Join Reply...");
                            broadcastPacket(prepareJoinReply(odp.multicastGroupIP, Arrays.asList(odp.sourceAddr)), null);
                        }

                        // Update Hop Count / TTL
                        odp.hopCount++;
                        if (odp.timeToLive > 1) { // If time to live hasn't expired, broadcast.
                            odp.timeToLive--;
                            String lastHop = odp.previousHopIP;
                            odp.previousHopIP = this.ipAddress;

                            Logger.logfn("Broadcasting an updated JQ packet! "+odp);
                            broadcastPacket(odp, Arrays.asList(lastHop));
                        }
                    } else
                        recPack = null;
                }
                // ODMRP Join Reply
                else if (recPack instanceof ODMRP_Proto.JoinReplyPacket) {
                    ODMRP_Proto.JoinReplyPacket odp = (ODMRP_Proto.JoinReplyPacket) recPack;
                    Logger.logf("Got ODMRP Join Reply Packet: " + odp);

                    // Update routes.
                    odmrp.addRoutingEntry(new Routing.RoutingEntry(odp.sourceAddr, odp.previousHopIP));

                    // Check the entries of Next Hops that match current node IP.
                    // Remove all entries which are not for us or reached the final destination.
                    Iterator<ODMRP_Proto.JoinReplyPacket.SenderNextHop> i = odp.senderData.iterator();
                    while (i.hasNext()) {
                        ODMRP_Proto.JoinReplyPacket.SenderNextHop cur = i.next();
                        if (!cur.nextHopIP.equals(this.ipAddress) || cur.senderIP.equals(this.ipAddress)) {
                            if(cur.senderIP.equals(this.ipAddress)){
                                Logger.logfn("We got Join Reply FOR US! Adding the source to the Receivers List...");
                                multicastReceivers.add(odp.sourceAddr);
                            }

                            i.remove(); // Remove the element I is pointing to now.
                            continue;
                        }

                        cur.nextHopIP = odmrp.getRouteForDestination(cur.senderIP).nextHopAddress;
                        if (cur.nextHopIP == null) // No route found
                            i.remove();
                    }
                    odp.count = (byte) (odp.senderData.size());
                    // If no entries of Next Hops match current node's IP, do nothing.
                    // If there are valid entries left, broadcast the modified join reply.
                    if (odp.senderData.size() > 0) {
                        Logger.logfn("We are part of Multicast Forward Group! Broadcasting the updated Join Reply.");
                        // Update the Forwarding Group, and broadcast.
                        odmrp.addGroupToForwardingTable(odp.multicastGroupIP);
                        String lastHop = odp.previousHopIP;
                        odp.previousHopIP = this.ipAddress;
                        broadcastPacket(odp, Arrays.asList(lastHop));
                    }
                }
                // IP Packet
                else if (recPack instanceof IPPacket) {
                    IPPacket pck = (IPPacket) recPack;
                    Logger.logf("Got IP Packet: " + pck);

                    if (pck.destAddr.equals(this.ipAddress) || multicastGroups.contains(pck.destAddr)) { // Check if this recPacket is meant for us.
                        passToTransportLayer(pck);
                    } else { // If packet is not meant for us, route it.
                        if (pck.timeToLive > 1) {
                            (pck.timeToLive)--;
                            (pck.hopsTraveled)++;

                            // If packet is Unicast, route it. If no route is found, discard packet.
                            if (pck.mode == Packet.CastMode.UNICAST){
                                if( !routePacket(pck) )
                                    Logger.logfn("Packet can't be routed! No valid route found!");
                            }
                            else if (pck.mode == Packet.CastMode.BROADCAST || (pck.mode == Packet.CastMode.MULTICAST &&
                                    odmrp.getGroupEntryByID(pck.destAddr, true) != null)) {
                                Logger.logfn("Packet is broadcast, or FG_FLAG for the Multicast group is set. Broadcasting packet.");
                                broadcastPacket(pck, null);
                            }
                        }
                    }
                }
            }
        }

        if(!pendingSendPackets.isEmpty() || !pendingReceivePackets.isEmpty())
            activeNodes.get().add(this);

        sendReceiveModeToggle = !sendReceiveModeToggle; // Switch between receive/send modes.
        return sentPack!=null || recPack!=null;
    }


    private boolean putPacketToQueue(Packet pack, Queue<Packet> pendingQue, String msg, boolean putToActiveNodes){
        if(this.down.get() || !this.isReady.get()) // If Node Down flag set, don't perform anything.
            return false;
        // Add packet to the pending packet queue.
        if(pendingQue.size() >= PENDING_PACKET_QUESIZE)
            pendingQue.poll();

        // Add a New Copy of the packet. We must make a copy so the modified version while processing
        // won't be the same instance other nodes got.
        pendingQue.offer( (Packet) ( pack.clone() ) );

        Logger.logfn("["+this.ipAddress+"]: "+msg+" Type: "+
                (pack instanceof ODMRP_Proto.JoinQueryPacket ? "JoinQuery" :
                (pack instanceof ODMRP_Proto.JoinReplyPacket ? "JoinReply" :
                (pack instanceof IPPacket ? "IPPacket" : "UnKnown") ) ) );

        // Push this node to the Queue of Nodes In Need Of Processing.
        activeNodes.get().offer(this);

        return true;
    }

    /**
     * Schedules a packet to be sent from this node over the network.
     * @param pack - a packet to send.
     */
    public boolean sendPacket(IPPacket pack){
        return putPacketToQueue(pack, pendingSendPackets, "Sending packet!", true);
    }

    public void originateIPPacket(Packet.CastMode castMode, String dest, String payload){
        sendPacket(new IPPacket(castMode, this.ipAddress, dest, ODMRP_Proto.DEFAULT_TTL, payload));
    }

    /** =====================================================================
     *  Inter-Node/Intra-Node API
     *  - These methods must be called only Inside the Node Class.
     *  - That's how routing and data exchange between Nodes happen.
     *
     * Put a packet to be processed next time node is scheduled.
     * @param pack - a packet.
     */
    private boolean acceptPacket(Packet pack){
        return putPacketToQueue(pack, pendingReceivePackets, "Accepted packet!", true);
    }

    /** - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
     * Private Helper API
     *
     * Method Prepares ODMRP Join Query packet to be originated by This Node.
     * @param multicastAddr - value of the Multicast Group field of the packet. Can be assigned
     *          to Multicast Group Address if multicasting, or Destination IP if unicasting.
     * @return - prepared Join Query.
     */
    private ODMRP_Proto.JoinQueryPacket prepareJoinQuery(String multicastAddr){
        ODMRP_Proto.JoinQueryPacket joinQuery = new ODMRP_Proto.JoinQueryPacket();
        // Fill in the data
        joinQuery.timeToLive = ODMRP_Proto.DEFAULT_TTL;
        joinQuery.hopCount = 0;
        joinQuery.multicastGroupIP = multicastAddr;
        joinQuery.sequenceNumber = new Random().nextInt();
        joinQuery.sourceAddr = this.ipAddress; // Source and previousHop - this one.
        joinQuery.previousHopIP = this.ipAddress;

        // No GPS fields are used.
        return joinQuery;
    }

    /**
     * Prepare ODMRP Join Reply packet to be originating from This Node.
     * @param multicastAddr - the Multicast Group address.
     * @param multicastSources - Sources of Multicast Group. Find Next Hops from routing table.
     * @return - prepared Join Reply.
     */
    private ODMRP_Proto.JoinReplyPacket prepareJoinReply(String multicastAddr, List<String> multicastSources){
        ODMRP_Proto.JoinReplyPacket joinReply = new ODMRP_Proto.JoinReplyPacket();

        joinReply.sourceAddr = this.ipAddress;
        joinReply.ackReq = false;
        joinReply.forwardGroup = false; // No FG_FLAG because this packet is not transmitted by forw.node.
        joinReply.multicastGroupIP = multicastAddr;
        joinReply.previousHopIP = this.ipAddress;
        joinReply.sequenceNumber = new Random().nextInt();

        // Now add routes and senders.
        for(String addr : multicastSources){
            Routing.RoutingEntry rt = odmrp.getRouteForDestination(addr);
            if(rt!=null){
                joinReply.senderData.add(new ODMRP_Proto.JoinReplyPacket.SenderNextHop(rt));
            }
        }
        joinReply.count = (byte)(joinReply.senderData.size());

        return joinReply;
    }

    /**
     * Returns the Node of the neighbor with IP Address specified.
     * @param ip - address of a neighbor
     * @return neighbor node, if exists, or null, if no node with that IP found.
     */
    private Node getNeighborByIP(String ip){
        for(int i = 0; i<neighbors.size(); i++){
            if(neighbors.get(i).getIpAddress().equals(ip))
                return neighbors.get(i);
        }
        return null;
    }

    /**
     * Sends the unicast packet to appropriate routes.
     * @param pack - a packet to send.
     * @return true, if sent successfully, false otherwise.
     */
    private boolean routePacket(IPPacket pack){
        Routing.RoutingEntry rt;
        // Check all possible routes.
        while(true){
            rt = odmrp.getRouteForDestination(pack.destAddr);
            if(rt == null)
                break; // Null means no possible routes.
            Node neigh = getNeighborByIP(rt.nextHopAddress);
            // If packet successfully sent, break loop. Send a Clone of the packet.
            if( neigh != null && neigh.acceptPacket( pack ) )
                break;
            else // If packet send failed -> host is down, or neighbor doesn't exist, so route is invalid --> delete route.
                odmrp.removeRoutingEntry(rt);
        }
        // Return value indicates if routed successfully.
        return (rt != null);
    }

    /**
     * Broadcasts packet to all neighbors.
     * @param pack - the Packet to broadcast.
     * @return true, if at least one node accepted the packet, false otherwise.
     */
    private boolean broadcastPacket(Packet pack, List<String> except){
        boolean accepted = false;
        for(int i=0; i<neighbors.size(); i++){
            if((except!=null && !except.contains(neighbors.get(i).ipAddress)) || except==null){
                if(neighbors.get(i).acceptPacket( pack ))
                    accepted = true;
            }
        }
        return accepted;
    }

    /**
     * Pass packet data to Transport Layer (4) from Network Layer (3).
     * @param pack - packet data.
     */
    void passToTransportLayer(IPPacket pack){
        System.out.println("\n----------------------------\n["+ipAddress+"] Got Packet: "+pack+"Data: "+
                pack.dataPayload+"\n---------------------------\n");
    }

    /**
     * Prints representation of this object by flags specified.
     * @return string representation.
     */
    public String customToString(boolean neighbors, boolean multicastGroups, boolean multicastReceivers,
                                 boolean routingTable, boolean forwardingTable){
        if(!this.isReady.get())
            return "Node is not yet ready.";

        StringBuilder ret = new StringBuilder();
        ret.append("Node: [").append(ipAddress).append("]: Multicast source address: ").append(multicastSourceAddress);

        if(multicastGroups) {
            ret.append("\n MulticastGroups: ");
            for (String gr : this.multicastGroups) {
                ret.append(gr).append(" , ");
            }
        }
        if(multicastReceivers){
            ret.append("\n MulticastReceivers: ");
            for (String r : this.multicastReceivers) {
                ret.append(r).append(" , ");
            }
        }
        if(neighbors) {
            ret.append("\n Neighbors: ");
            for (int i=0; i< this.neighbors.size(); i++) {
                ret.append(this.neighbors.get(i).ipAddress).append(" , ");
            }
        }
        if(routingTable){
            ret.append("\nRouting Table:\n").append(odmrp.routingTableToString());
        }
        if(forwardingTable){
            ret.append("\nForwarding Table:\n").append(odmrp.forwardingTableToString());
        }

        return ret.append("\n").toString();
    }
}
