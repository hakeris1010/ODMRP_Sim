package NetworkSim;

import java.util.Arrays;

public class IPPacket extends Packet {
    byte version = 4;
    String sourceAddr;
    String destAddr;
    int timeToLive = 32;
    int hopsTraveled = 0;
    boolean verbose = false;

    String dataPayload;

    public IPPacket(){}
    public IPPacket(CastMode mode, String source, String dest, int ttl, String payload){
        this.populate(mode, source, dest, ttl, payload);
    }
    public IPPacket(IPPacket pack){
        this.populate(pack.mode, pack.sourceAddr, pack.destAddr, pack.timeToLive, pack.dataPayload);
    }

    private void populate(CastMode mode, String source, String dest, int ttl, String payload){
        super.populate(mode);
        sourceAddr = source;
        destAddr = dest;
        timeToLive = ttl;
        hopsTraveled = 0;
        dataPayload = payload;
    }

    @Override
    public Object clone(){
        return new IPPacket(this);
    }

    @Override
    public String toString(){
        return "IPPacket ("+mode+"):\n src: "+sourceAddr+"\n dst: "+destAddr+"\n ttl: "+timeToLive+", hops: "+hopsTraveled+"\n";
    }
}
