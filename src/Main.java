import NetworkSim.Network;

import java.util.*;

public class Main {

    /**
     * Simulation implementing ODMRP Multicast-Mesh Ad-Hoc Routing Protocol.
     */
    public static final String VERSION = "v0.1";

    public static void main(String[] args){
        System.out.println("ODMRP Simulation "+ VERSION + "\n============================\n");

        Network net = new Network();

        net.startNetwork();
    }
}
