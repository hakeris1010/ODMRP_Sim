package NetworkSim;

import java.util.Arrays;

public class IPPacket extends Packet {
    public byte version;
    public String sourceAddr;
    public String destAddr;

    public byte[] dataPayload;

    public IPPacket(String source, String dest, byte[] payload){
        sourceAddr = source;
        destAddr = dest;
        dataPayload =  Arrays.copyOf(payload, payload.length);
    }
}
