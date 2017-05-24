import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class Main {

    /**
     * Simulation implementing ODMRP Multicast-Mesh Ad-Hoc Routing Protocol.
     * @param args - cmd line.
     */

    public static void main(String[] args){
        ArrayList<String> names = new ArrayList<>(Arrays.asList("a", "b", "c1", "c20,", "gaga"));

        System.out.println("Before:");
        Iterator<String> i = names.iterator();
        while(i.hasNext()){
            System.out.println(i.next());
        }

        // Modify
        i = names.iterator();
        while(i.hasNext()){
            String st = i.next();
            if(st.matches(".*\\d.*")) // Got a number inside
                i.remove();
        }

        System.out.println("\n------------\nAfter:");
        i = names.iterator();
        while(i.hasNext()){
            System.out.println(i.next());
        }
    }
}
