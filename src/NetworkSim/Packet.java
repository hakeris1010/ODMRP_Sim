package NetworkSim;

import java.io.Serializable;
import java.util.regex.Pattern;

public abstract class Packet implements Serializable, Cloneable {
    static final int PACKETMODE_NOADDR    = 0;
    static final int PACKETMODE_UNICAST   = 1;
    static final int PACKETMODE_MULTICAST = 2;
    static final int PACKETMODE_BROADCAST = 3;

    static final String IPV4_REGEX_STRING = "^(?:(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
    static final String IPV4_MULTICAST_REGEX = "^(?:2[23][4-9])\\." +
                                                "(?:(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){2}" +
                                                "(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
    static final String IPV4_BROADCAST_REGEX = "255.255.255.255";

    int mode;

    protected Packet(){}
    protected Packet(int sendMode){
        mode = sendMode;
    }
    protected Packet(Packet pack){ this.mode = pack.mode; }

    protected void populate(int md){ mode = md; }

    @Override
    public abstract Object clone();
}
