package NetworkSim;

import java.util.ArrayList;
import java.util.List;

@Deprecated
public class UnicastRouting {
    private final Routing routes = new Routing();

    // EXPERIMENTAL: unicast route queries currently processed.
    private final List<ODMRP_Proto.MessageCacheEntry> unicastRequestCache = new ArrayList<>();

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
    /*public Routing.RoutingEntry getUnicastRoute(Node current, Node lastHop, String destination, String sourceAddr, long ID){
        // Check if this query is already being processed (called by looper)
        ODMRP_Proto.MessageCacheEntry dummyEntry = new ODMRP_Proto.MessageCacheEntry(sourceAddr, ID);
        if(unicastRequestCache.contains(dummyEntry))
            return null;
        unicastRequestCache.add(dummyEntry);

        Routing.RoutingEntry min = null, rt;
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
    }*/

}
