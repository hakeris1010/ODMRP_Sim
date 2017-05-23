package NetworkSim;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Base class for all routing protocols.
 * Contais the basic Routing Table and API for manipulating it.
 */
public class Routing {
    public static class RoutingEntry implements Comparable {
        public String destinationAddress;
        public String destSubnetMask;
        public String nextHopAddress;
        long cost;
        int flags;

        public RoutingEntry(String dest, String nextHop) {
            destinationAddress = dest;
            nextHopAddress = nextHop;
        }

        @Override
        public boolean equals(Object o) { // We just perform compareTo here.
            return compareTo(o) == 0;
        }

        @Override
        public int compareTo(Object o) {
            // MAYBE: Compare only the destination address. Only one route to destinatio
            if (o instanceof RoutingEntry) {
                int c1 = destinationAddress.compareTo(((RoutingEntry) o).destinationAddress);
                if (c1 != 0) return c1;
                return nextHopAddress.compareTo(((RoutingEntry) o).nextHopAddress);
            }
            return 0xdeadbeef; // Not even the same type.
        }
    }

    // Routing table. Used to determine path of the packet.
    private final SortedSet<RoutingEntry> routingTable = new TreeSet<>();

    /**
     * Routing table API.
     */
    public void addRoutingEntry(RoutingEntry entry){
        RoutingEntry e = ((TreeSet<RoutingEntry>)routingTable).ceiling(entry);
        if(e.equals(entry)){
            // If equal entry found, just update the cost (because comparison is done by nextHop and dest.
            e.cost = entry.cost;
        } else {
            routingTable.add(entry);
        }
    }
    public RoutingEntry getRouteForDestination(String address){
        RoutingEntry min = null;
        for(RoutingEntry e : routingTable){
            if(e.destinationAddress.equals(address)){
                if(min==null){
                    min = e;
                } else {
                    if(e.cost < min.cost)
                        min = e;
                }
            }
        }
        return min;
    }
    public boolean removeRoutingEntry(RoutingEntry entry){
        return routingTable.remove(entry);
    }
    public int removeAllRoutesToDestination(String dest){
        int ctr=0;
        for(RoutingEntry e : routingTable){
            if(e.destinationAddress.equals(dest)) {
                routingTable.remove(e);
                ctr++;
            }
        }
        return ctr;
    }
}
