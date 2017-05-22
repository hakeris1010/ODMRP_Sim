package NetworkSim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Network object containing the thread which runs all nodes, firing events when needed.
 */
public class Network {
    private final List<Node> netNodes = Collections.synchronizedList( new ArrayList<>() );
    private final Thread controlThread;

    {
        controlThread = new Thread(() -> {
            // Perform node-based routing and stuff.
        });
    }

    public Network(){ }
    public Network(List<Node> nodes){
        netNodes.addAll(nodes);
    }

    public Node getNode(int i){
        return netNodes.get(i);
    }

    public void addNode(Node node){
        netNodes.add(node);
    }

    public List<Node> waitForActiveNodes(){
        return null;
    }
}
