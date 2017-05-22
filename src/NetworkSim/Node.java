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
 *    3.1.1. If Node has Multicast Packets available, it sends a Join Query to it's neighbors,
 *           specifying it's IP as Source Address, and it's multicast group address.
 *       - The query is broadcasted through the whole network.
 *       - This way current node advertises it's presence on the network, so other nodes
 *         could update their routing tables for route to this node.
 *       - The multicast-interested nodes send the Join Replies to current node, which has
 *         became the multicast-source. The reply propagation process builds a Forwarding group
 *         for routing of multicast packets belonging to this group.
 *
 *    3.1.2. When Join Replies reach current node, it updates it's routing table, and
 *           sends the multicast traffic through the now-updated forwarding group.
 *
 *    3.2. If node hasn't got any Multicast packets, but instead has Unicast packets available,
 *         it doesn't send Join Query --> doesn't get any Join Replies.
 *         So it doesn't know any routes to wanted destinations.
 *
 *         - If this is the situation, the ODMPR doesn't provide specification for route
 *           resolution. The node must use other, Unicast-oriented RP's to determine the route
 *           to the wanted Unicast destination.
 *
 *         - We implemented a simple mechanism of getting required data by querying neighbor's
 *           routing tables to get the route data needed.
 *
 *   3.3. If all routes are determined, the current node sends it's packets over the network.
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
 *  In our network model, we presume Static IP and Multicast addresses.
 *
 */

public class Node {
    // Node's IP (assigned by itself (?))
    private String ipAddress;

    // This node's Multicast Source group address.
    private String multicastSourceAddress;

    // The addresses of multicast groups this node is part of.
    private final List<String> multicastGroups = new ArrayList<>();

    // The nodes this node is directly connected to.
    private final Set<Node> neighbors = Collections.synchronizedSortedSet(new TreeSet<>());

    // The tables and other structures of Routing Protocol being used (in this case ODMRP).
    private final ODMRP_Proto odmrpRoutingData = new ODMRP_Proto();

    // EXPERIMENTAL: unicast route queries currently processed.
    private final List<ODMRP_Proto.MessageCacheEntry> unicastRequestCache = new ArrayList<>();

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

    /**
     * Simple getters for basic properties.
     */
    String getIpAddress(){ return ipAddress; }
    String getMulticastSourceAddress(){ return multicastSourceAddress; }
    String[] getParticipatedMulticastGroups(){ return (String[])(multicastGroups.toArray()); }

    /**
     * Adds new node to the current node's network.
     * - Typically called by the node that wants to add itself, after discovering this
     *   node by using this node's advertisement data.
     * - Authentication and stuff happens there.
     * - Notice that this method adds node at Level 3 (Network).
     * @param node - the node to add to current node's network.
     */
    public void connectNode(Node node){
        neighbors.add(node);
    }

    /** EXPERIMENTAL:
     * Simple method to get the route to Unicast destination.
     * If this node's Routing Table doesn't contain required data, ask neighbors.
     * - DON'T USE THIS METHOD for general purpose, because it causes high congestion because of
     *   flooding whole network with this type of packets if route can't be found.
     * @param lastHop - the neighbor node sending this packet
     * @param destination - the IP address which's route is being searched for.
     * @param sourceAddr - the IP of the original node requesting the route.
     * @param ID - the unique identifier of current query propagation.
     * - By using ID and sourceAddr we make sure that no routing loops are made.
     */
    public ODMRP_Proto.RoutingTableEntry getUnicastRoute(Node lastHop, String destination, String sourceAddr, long ID){
        // Check if this query is already being processed (called by looper)
        ODMRP_Proto.MessageCacheEntry dummyEntry = new ODMRP_Proto.MessageCacheEntry(sourceAddr, ID);
        if(unicastRequestCache.contains(dummyEntry))
            return null;
        unicastRequestCache.add(dummyEntry);

        ODMRP_Proto.RoutingTableEntry min = null, rt;
        // Firstly, try to find route in our own table.
        rt = odmrpRoutingData.getRouteForDestination(destination);
        // If we don't have a route, query neighbors
        if(rt == null){
            for(Node n : neighbors){
                if(n != lastHop) {
                    // Propagate recursively through neighbors.
                    rt = n.getUnicastRoute(this, destination, sourceAddr, ID);
                    if(rt != null){
                        long costToNeigh = odmrpRoutingData.getRouteForDestination(n.ipAddress).cost;
                        if(min==null) {
                            min = rt;
                            min.cost = rt.cost + costToNeigh;
                        } else {
                            if(rt.cost + costToNeigh < min.cost){
                                min = rt;
                                min.cost = rt.cost + costToNeigh;
                            }
                        }
                    }
                }
            }
            rt = min;
            // If we haven't got a route, add it.
            this.odmrpRoutingData.addRoutingTableEntry(rt);
        }
        return rt;
    }

    /** =============================================================================
     * ODMRP Land.
     *
     * Sends ODMRP Join Query packet.
     */
    public void broadcastJoinQuery(){
        ODMRP_Proto.JoinQueryPacket joinQuery = new ODMRP_Proto.JoinQueryPacket();
        // Fill in the data
        joinQuery.type = ODMRP_Proto.JOINQUERY_TYPE;
        joinQuery.timeToLive = ODMRP_Proto.DEFAULT_TTL;
        joinQuery.hopCount = 0;
        joinQuery.multicastGroupIP = this.multicastSourceAddress;
        joinQuery.sequeceNumber = new Random().nextInt();
        joinQuery.sourceIP = this.ipAddress; // Source and previousHop - this one.
        joinQuery.previousHopIP = this.ipAddress;
        // No GPS fields are used.

        // Data filled --> broadcast!
        for(Node n : neighbors){
            n.acceptJoinQuery(joinQuery);
        }
    }

    /**
     * Accepts ODMRP Join Query, and processes it.
     */
    public void acceptJoinQuery(ODMRP_Proto.JoinQueryPacket query){

    }

}
