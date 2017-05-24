package NetworkSim;

import java.util.*;

/**
 * A class defining the specification of ODMRP ad-hoc routing protocol.
 * Contains state classes and tables maintained by each node,
 * and the API to use them.
 */
public class ODMRP_Proto extends Routing {
    /**
     * Table containing the multicast groups this node is forwarding.
     */
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

    /**
     * Cache for identifying duplicate Join Query/Reply packets.
     */
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
     * Base ODMRP Routing Data Packet
     */
    public static class ODMRPPacket extends Packet{
        byte type;
        String sourceAddr;
        String multicastGroupIP;
        String previousHopIP;
        int sequenceNumber;
    }

    /**
     * Join Query Header Format
     */
    public static class JoinQueryPacket extends ODMRPPacket {
        byte timeToLive, hopCount;
        // GPS Data
        int prevHopX, prevHopY;
        short prevHopSpeed, prevHopDirection;
        int minExpTime;
        // Payload attached at the end.
        byte[] payload;

        { type = JOINQUERY_TYPE; }
    }

    /**
     * Join Reply Header Format
     */
    public static class JoinReplyPacket extends ODMRPPacket {
        public static class SenderNextHop{
            String senderIP;
            String nextHopIP;
            int routeExpirationTime = 0;

            SenderNextHop(RoutingEntry e){
                senderIP = e.destinationAddress;
                nextHopIP = e.nextHopAddress;
            }
        }

        byte count;
        boolean ackReq, forwardGroup;

        List<SenderNextHop> senderData = new ArrayList<>();

        { type = JOINREPLY_TYPE; }
    }

    /**
     * Constanta
     */
    public static final int MSG_CACHE_SIZE = 2048;
    public static final byte JOINQUERY_TYPE = 0x01;
    public static final byte JOINREPLY_TYPE = 0x02;
    public static final byte DEFAULT_TTL = 32;

    // Intervals, in milliseconds.
    public static final long DEFAULT_ROUTE_REFRESH = 1000;
    public static final long DEFAULT_FORWARDING_TIMEOUT = 3000;

    /** =============================================================================
     * The state data tables.
     */
    // Message cache. A fixed-size queue, used to track duplicate Join Queries.
    private final SortedSet<MessageCacheEntry> messageCache = new TreeSet<>();

    // Forwarding group table. Contains information about Multicast groups node is in.
    private final SortedSet<ForwardingGroupTableEntry> forwardingGroupTable = new TreeSet<>();

    // Timers
    private long lastRouteRefresh;

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
     * Forwarding Group API
     */
    public boolean addGroupTableEntry(ForwardingGroupTableEntry e) {
        return forwardingGroupTable.add(e);
    }
    public boolean removeGroupTableEntry(ForwardingGroupTableEntry e){
        return forwardingGroupTable.remove(e);
    }

    public ForwardingGroupTableEntry getGroupEntryByID(String groupID, boolean deleteIfExpired){
        ForwardingGroupTableEntry dummy = new ForwardingGroupTableEntry(groupID, 0), en;
        en = ((TreeSet<ForwardingGroupTableEntry>)forwardingGroupTable).ceiling( dummy );
        // Delete group entry if timed out.
        if( en!=null && en.equals(dummy) && deleteIfExpired &&
            System.currentTimeMillis() - en.lastRefreshedTime > DEFAULT_FORWARDING_TIMEOUT){
              forwardingGroupTable.remove(en);
              en = null;
        }
        if(en!=null && en.equals(dummy))
            return en;
        return null;
    }

    /**
     * Timer API
     */
    public long getLastRouteRefresh(){ return lastRouteRefresh; }
    public void resetLastRouteRefresh(){
        lastRouteRefresh = System.currentTimeMillis();
    }
    public boolean isRouteRefreshNeeded(){
        return (System.currentTimeMillis() - lastRouteRefresh > DEFAULT_ROUTE_REFRESH);
    }

}
