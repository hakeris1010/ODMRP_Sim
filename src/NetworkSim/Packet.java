package NetworkSim;

import java.io.Serializable;
import java.util.regex.Pattern;

public abstract class Packet implements Serializable, Cloneable {
    public enum CastMode{
        NOADDR,
        UNICAST,
        MULTICAST,
        BROADCAST
    }

    static final String IPV4_REGEX_STRING = "^(?:(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
    static final String IPV4_MULTICAST_REGEX = "^(?:2[23][4-9])\\." +
                                                "(?:(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){2}" +
                                                "(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
    //static final String IPV4_UNICAST_REGEX = "^"
    static final String IPV4_BROADCAST_REGEX = "255.255.255.255";

    CastMode mode = CastMode.UNICAST;

    protected Packet(){}
    protected Packet(CastMode sendMode){
        mode = sendMode;
    }
    protected Packet(Packet pack){ this.mode = pack.mode; }

    protected void populate(CastMode md){ mode = md; }

    @Override
    public abstract Object clone();

    /**
     * @return IP address type - multicast, unicast, or broadcast.
     */
    public static Packet.CastMode getAddressType(String ip){
        if(ip.contains(":")) { // IPv6
            return Packet.CastMode.NOADDR; // Not yet implemented.
        }
        // IPv4
        else if(ip.matches(Packet.IPV4_MULTICAST_REGEX))
            return Packet.CastMode.MULTICAST;
        else if(ip.equals(Packet.IPV4_BROADCAST_REGEX))
            return Packet.CastMode.BROADCAST;

        return Packet.CastMode.NOADDR;
    }
}
