package NetworkSim;

import sun.reflect.generics.tree.Tree;

import java.util.*;

/**
 * A class defining the specification of ODMRP ad-hoc routing protocol.
 * Contains state classes and tables maintained by each node,
 * and the API to use them.
 */
public class ODMRP_Proto {
    /**
     * Table entry data
     */
    public static class RoutingTableEntry implements Comparable{
        public String destinationAddress;
        public String nextHopAddress;
        long cost;

        public RoutingTableEntry(String dest, String nextHop){ destinationAddress=dest; nextHopAddress=nextHop; }

        @Override
        public boolean equals(Object o){ // We just perform compareTo here.
            return compareTo(o)==0;
        }

        @Override
        public int compareTo(Object o) {
            // MAYBE: Compare only the destination address. Only one route to destinatio
            if(o instanceof RoutingTableEntry){
                int c1 = destinationAddress.compareTo(((RoutingTableEntry)o).destinationAddress);
                if(c1 != 0) return c1;
                return nextHopAddress.compareTo(((RoutingTableEntry)o).nextHopAddress);
            }
            return 0xdeadbeef; // Not even the same type.
        }
    }

    public static class ForwardingGroupTableEntry implements Comparable{
        public String groupID;
        public long lastRefreshedTime;

        public ForwardingGroupTableEntry(String gID, long refreshedTime){ groupID = gID; lastRefreshedTime = refreshedTime; }

        @Override
        public boolean equals(Object o){
            return compareTo(o)==0;
        }

        @Override
        public int compareTo(Object o) { // We compare only with the groupID.
            if(o instanceof ForwardingGroupTableEntry){
                return groupID.compareTo(((ForwardingGroupTableEntry)o).groupID);
            }
            return 0xdeadbeef; // Not even the same type.
        }
    }

    public static class MessageCacheEntry implements Comparable {
        public String sourceAddress;
        public long packetID;

        public MessageCacheEntry(String source, long packID){ sourceAddress=source; packetID = packID; }

        @Override
        public boolean equals(Object o){
            return compareTo(o)==0;
        }

        @Override
        public int compareTo(Object o) {
            if(o instanceof MessageCacheEntry){
                int c1 = (int) (this.packetID - ((MessageCacheEntry)o).packetID); // Firstly compare IDs.
                if(c1 != 0) return c1;
                return this.sourceAddress.compareTo(((MessageCacheEntry)o).sourceAddress);
            }
            return 0xdeadbeef; // Not even the same type.
        }
    }

    /**
     * Join Query Header Format
     */
    public static class JoinQueryPacket{
        public byte type, reserved, timeToLive, hopCount;
        public String multicastGroupIP;
        public int sequeceNumber;
        public String sourceIP;
        public String previousHopIP;
        // GPS Data
        int prevHopX, prevHopY;
        short prevHopSpeed, prevHopDirection;
        int minExpTime;
        // Payload at the end.
        byte[] payload;
    }

    /**
     * Join Reply Header Format
     */
    public static class JoinReplyPacket{
        public static class SenderNextHop{
            String multicastGroupIP;
            String previousHopIP;
            int routeExpirationTime;
        }

        byte type, count;
        boolean ackReq, forwardGroup;
        String multicastGroupIP;
        String previousHopIP;
        int sequenceNumber;

        List<SenderNextHop> senderData;
    }

    /**
     * Constanta
     */
    public static final int MSG_CACHE_SIZE = 2048;
    public static final byte JOINQUERY_TYPE = 0x01;
    public static final byte DEFAULT_TTL = 32;

    /** =============================================================================
     * The state data tables.
     */
    // Message cache. A fixed-size queue, used to track duplicate Join Queries.
    private final SortedSet<MessageCacheEntry> messageCache = new TreeSet<>();

    // Routing table. Used to determine path of the packet.
    private final SortedSet<RoutingTableEntry> routingTable = new TreeSet<>();

    // Forwarding group table. Contains information about Multicast groups node is in.
    private final SortedSet<ForwardingGroupTableEntry> forwardingGroupTable = new TreeSet<>();

    /**
     * API for easily getting required info and modifying the tables.
     * Message cache data.
     * @param entry - object to be added or searched for.
     * @return true, if added successfully (equal entry not found), false otherwise.
     */
    public boolean addMessageCacheEntry(MessageCacheEntry entry){
        boolean isAdded = messageCache.add(entry);
        // Remove first element if size is at limit.
        if(isAdded && messageCache.size() >= MSG_CACHE_SIZE) {
            ((TreeSet<MessageCacheEntry>) messageCache).pollFirst();
        }
        return isAdded;
    }
    public boolean isEntryInMessageCache(MessageCacheEntry entry){
        return messageCache.contains(entry);
    }

    /**
     * Routing table API.
     */
    public void addRoutingTableEntry(RoutingTableEntry entry){
        RoutingTableEntry e = ((TreeSet<RoutingTableEntry>)routingTable).ceiling(entry);
        if(e.equals(entry)){
            // If equal entry found, just update the cost (because comparison is done by nextHop and dest.
            e.cost = entry.cost;
        } else {
            routingTable.add(entry);
        }
    }
    public RoutingTableEntry getRouteForDestination(String address){
        RoutingTableEntry min = null;
        for(RoutingTableEntry e : routingTable){
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
    public boolean removeRoutingTableEntry(RoutingTableEntry entry){
        return routingTable.remove(entry);
    }
    public int removeAllRoutesToDestination(String dest){
        int ctr=0;
        for(RoutingTableEntry e : routingTable){
            if(e.destinationAddress.equals(dest)) {
                routingTable.remove(e);
                ctr++;
            }
        }
        return ctr;
    }

    /**
     * Forwarding Group API
     */
    public boolean addGroupTableEntry(ForwardingGroupTableEntry e) {
        return forwardingGroupTable.add(e);
    }
    public boolean removeGroupTableEntry(ForwardingGroupTableEntry e){
        return forwardingGroupTable.remove(e);
    }
    public ForwardingGroupTableEntry getGroupEntryByID(String groupID){
        ForwardingGroupTableEntry dummy = new ForwardingGroupTableEntry(groupID, 0), en;
        en = ((TreeSet<ForwardingGroupTableEntry>)forwardingGroupTable).ceiling( dummy );
        if(en.equals(dummy))
            return en;
        return null;
    }



}
