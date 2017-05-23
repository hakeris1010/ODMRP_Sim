package NetworkSim;

import java.util.*;

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

public class Node {
    // Is node working at the moment
    private boolean down = false;

    // Node's IP (assigned by itself (?))
    private String ipAddress;

    // This node's Multicast Source group address.
    private String multicastSourceAddress;

    // The addresses of multicast groups this node is part of.
    private final List<String> multicastGroups = new ArrayList<>();

    // The nodes this node is directly connected to.
    private final Set<Node> neighbors = Collections.synchronizedSortedSet(new TreeSet<>());

    // The tables and other structures of Routing Protocol being used (in this case ODMRP).
    private final ODMRP_Proto odmrp = new ODMRP_Proto();

    // Pending packet queues
    private final Queue<Packet> pendingReceivePackets = new LinkedList<>();
    private final Queue<Packet> pendingSendPackets = new LinkedList<>();

    // A set containing routes we are requesting at the moment, it helps to avoid loops.
    private final SortedSet<String> routeRequestCache = new TreeSet<>();

    // If this is set, we must broadcast the join query next turn.
    private ODMRP_Proto.JoinQueryPacket joinQueryNext = null;
    private boolean waitingForJoinReplies = false;
    private boolean sendReceiveModeToggle = false;

    private static final int PENDING_PACKET_QUESIZE = 256;

    /**
     * Constructors.
     * We can construct this node by specifying the nodes to connect to, too.
     */
    public Node(){ }
    public Node(String ip, String multicastIp){
        ipAddress = ip;
        multicastSourceAddress = multicastIp;
    }
    public Node(String ip, String multicastIp, List<String> groups, List<Node> connectNodes){
        this(ip, multicastIp);
        if(groups!=null)
            multicastGroups.addAll(groups);
        if(connectNodes!=null){
            for(Node i : connectNodes){
                this.connectNode(i);
            }
        }
    }

    /** =====================================================================
     * Node Property Modification API.
     * - Simple getters and setters for basic properties.
     * - Can be called from inside or outside of the Node.
     */
    public String getIpAddress(){ return ipAddress; }
    public String getMulticastSourceAddress(){ return multicastSourceAddress; }
    public String[] getParticipatedMulticastGroups(){ return (String[])(multicastGroups.toArray()); }

    public void addMulticastGroup(String... groupAddr){
        multicastGroups.addAll(Arrays.asList(groupAddr));
    }
    public boolean gotPendingReceivePackets(){ return !pendingReceivePackets.isEmpty(); }
    public boolean gotPendingSendPackets(){ return !pendingSendPackets.isEmpty(); }

    /**
     * Adds new node to the current node's network.
     * - Typically called by the node that wants to add itself, after discovering this
     *   node by using this node's advertisement data.
     * - Authentication and stuff happens there.
     * - Notice that this method adds node at Level 3 (Network).
     * @param node - the node to add to current node's network.
     */
    public void connectNode(Node node){
        System.out.println("["+this.ipAddress+"]: Connecting node: "+node);
        neighbors.add(node);
    }

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
     */
    public boolean process(){
        // Firstly, check if we gotta send Join Queries, by analyzing timers and if there
        // was a query specified on last iteration.
        if(joinQueryNext != null || odmrp.isRouteRefreshNeeded()){
            if(joinQueryNext == null){ // Timer approached -> broadcast JQ with our Multicast Source.
                joinQueryNext =  prepareJoinQuery(multicastSourceAddress);
            }
            // Now join query is ready to broadcast.
            for(Node n : neighbors){
                n.acceptPacket(joinQueryNext);
            }
            // At the end, reset timer if this was caused by a timer, and set joinQueryNext to null.
            // Also specify that we are expecting Join Replies at this moment.
            odmrp.resetLastRouteRefresh();
            joinQueryNext = null;
            waitingForJoinReplies = true;
            // Return true, indicating that we successfully completed an operation.
            return true;
        }

        // At this moment, when no high-priority ODMRP queries are pending, we analyze pending send/receive packets.
        Packet pack = null;
        boolean gottaSend = !pendingSendPackets.isEmpty() && (pendingReceivePackets.isEmpty() || sendReceiveModeToggle);

        // Check send packets (The packets this node originates and sends to the network).
        if(gottaSend){
            pack = pendingSendPackets.poll();

            // Check the packet mode. Multicasted packets are broadcasted if node is part of Forw.Group.
            if( pack.mode == Packet.PACKETMODE_MULTICAST && odmrp.getGroupEntryByID(pack.destAddr, true) != null ||
                pack.mode == Packet.PACKETMODE_BROADCAST ) {
                // Todo: maybe check if at least one node accepted the packet.
                // Just broadcast to all neighbors.
                for(Node n : neighbors){
                    n.acceptPacket(pack);
                }
            }
            // For unicasted packets, route according to routing table.
            else if(pack.mode == Packet.PACKETMODE_UNICAST) {
                // Firstly check if the route for this packet is unknown, but already being searched for.
                if (!routeRequestCache.contains(pack.destAddr)) {
                    // If no successful route found, we need to repair our routing table by broadcasting a JQ.
                    if (!routePacket(pack)) {
                        // Put the UnSent packet at the end of the Queue for resending when route is known,
                        // and add the destination to routes currently being requested. Request a route on next iteration
                        // by a Join Query. Note that on Multicast Group field we specify the Destination IP of the packet,
                        // so the node with that IP will send back a Join Reply.
                        pendingSendPackets.offer(pack);
                        routeRequestCache.add(pack.destAddr);
                        joinQueryNext = prepareJoinQuery(pack.destAddr);
                    }
                } else { // If packet's route is being searched for, we can move to Receive Packet queue.
                    pendingSendPackets.offer(pack);
                    gottaSend = false;
                }
            }
        }
        // Check receive packets, if there are pending and no sending were done.
        if(!pendingReceivePackets.isEmpty() && !gottaSend){
            pack = pendingReceivePackets.peek();

            // Check the type of packet got: Join Query, Join Reply, Data.
            if(pack instanceof ODMRP_Proto.JoinQueryPacket){

            }
            else if (pack instanceof ODMRP_Proto.JoinReplyPacket){

            }
            else{ // Data Packet.
                if(pack.destAddr.equals(this.ipAddress)){ // Check if this packet is meant for us.

                }
            }
        }

        sendReceiveModeToggle = !sendReceiveModeToggle; // Switch between receive/send modes.
        return pack != null;
    }

    public void sendPacket(Packet pack){
        if(pendingSendPackets.size() >= PENDING_PACKET_QUESIZE)
            pendingSendPackets.poll();
        pendingSendPackets.offer(pack);
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
        if(this.down) // If Node Down flag set, don't perform anything.
            return false;

        if(pendingReceivePackets.size() >= PENDING_PACKET_QUESIZE)
            pendingReceivePackets.poll();
        pendingReceivePackets.offer(pack);
        return true;
    }

    /** - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
     * Private Helper API
     *
     * Method Prepares ODMRP Join Query packet.
     * @param multicastAddr - value of the Multicast Group field of the packet. Can be assigned
     *          to Multicast Group Address if multicasting, or Destination IP if unicasting.
     */
    private ODMRP_Proto.JoinQueryPacket prepareJoinQuery(String multicastAddr){
        ODMRP_Proto.JoinQueryPacket joinQuery = new ODMRP_Proto.JoinQueryPacket();
        // Fill in the data
        joinQuery.type = ODMRP_Proto.JOINQUERY_TYPE;
        joinQuery.timeToLive = ODMRP_Proto.DEFAULT_TTL;
        joinQuery.hopCount = 0;
        joinQuery.multicastGroupIP = multicastAddr;
        joinQuery.sequeceNumber = new Random().nextInt();
        joinQuery.sourceIP = this.ipAddress; // Source and previousHop - this one.
        joinQuery.previousHopIP = this.ipAddress;
        // No GPS fields are used.
        return joinQuery;
    }

    /**
     * Returns the Node of the neighbor with IP Address specified.
     * @param ip - address of a neighbor
     * @return neighbor node, if exists, or null, if no node with that IP found.
     */
    private Node getNeighborByIP(String ip){
        for(Node n : neighbors){
            if(n.getIpAddress().equals(ip))
                return n;
        }
        return null;
    }

    private boolean routePacket(Packet pack){
        Routing.RoutingEntry rt;
        // Check all possible routes.
        while(true){
            rt = odmrp.getRouteForDestination(pack.destAddr);
            if(rt == null)
                break; // Null means no possible routes.
            // If packet successfully sent, break loop.
            if( getNeighborByIP(rt.nextHopAddress).acceptPacket(pack) )
                break;
            else // If packet send failed --> host is down, route is invalid --> delete route.
                odmrp.removeRoutingEntry(rt);
        }
        // Return value indicates if routed successfully.
        return (rt != null);
    }

}
