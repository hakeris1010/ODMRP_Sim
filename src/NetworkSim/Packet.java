package NetworkSim;

import java.io.Serializable;

public class Packet implements Serializable {
    public String sourceAddr, destAddr;
    public byte[] dataPayload;

    public Packet(){}
    public Packet(String src, String dest, byte[] payload){
        sourceAddr = src;
        destAddr = dest;
        dataPayload = payload;
    }
}
