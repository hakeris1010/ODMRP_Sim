package NetworkSim;

import java.util.Arrays;

public class IPPacket extends Packet {
    byte version = 4;
    String sourceAddr;
    String destAddr;
    byte timeToLive;
    byte hopsTraveled;

    byte[] dataPayload;

    public IPPacket(int mode, String source, String dest, byte ttl, byte[] payload){
        this.populate(mode, source, dest, ttl, payload);
    }
    public IPPacket(IPPacket pack){
        this.populate(pack.mode, pack.sourceAddr, pack.destAddr, pack.timeToLive, pack.dataPayload);
    }

    private void populate(int mode, String source, String dest, byte ttl, byte[] payload){
        super.populate(mode);
        sourceAddr = source;
        destAddr = dest;
        timeToLive = ttl;
        dataPayload =  Arrays.copyOf(payload, payload.length);
    }

    @Override
    public Object clone(){
        return new IPPacket(this);
    }

    @Override
    public String toString(){
        return "IPPacket:\n src: "+sourceAddr+"\n dst: "+destAddr+"\n ttl: "+(int)timeToLive+", hops: "+(int)hopsTraveled+"\n";
    }
}
