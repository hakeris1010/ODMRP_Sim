package NetworkSim;

import java.util.Arrays;

public class IPPacket extends Packet {
    byte version = 4;
    String sourceAddr;
    String destAddr;
    byte timeToLive;
    byte hopsTraveled;

    byte[] dataPayload;

    public IPPacket(String source, String dest, byte ttl, byte[] payload){
        sourceAddr = source;
        destAddr = dest;
        timeToLive = ttl;
        dataPayload =  Arrays.copyOf(payload, payload.length);
    }

    @Override
    public String toString(){
        return "IPPacket:\n src: "+sourceAddr+"\n dst: "+destAddr+"\n ttl: "+(int)timeToLive+", hops: "+(int)hopsTraveled+"\n";
    }
}
