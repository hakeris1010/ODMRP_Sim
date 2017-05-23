package NetworkSim;

import java.io.Serializable;

public class Packet implements Serializable {
    public static final int PACKETMODE_UNICAST   = 1;
    public static final int PACKETMODE_MULTICAST = 2;
    public static final int PACKETMODE_BROADCAST = 3;

    public String sourceAddr, destAddr;
    public int mode;
    public byte[] dataPayload;

    public Packet(){}
    public Packet(String src, String dest, int sendMode, byte[] payload){
        sourceAddr = src;
        destAddr = dest;
        mode = sendMode;
        dataPayload = payload;
    }
}
