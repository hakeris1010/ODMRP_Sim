package NetworkSim;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * Created by haker on 2017-05-25.
 */
public class Logger {
    private static final String logFile = "odmrp.log";
    private static PrintStream stream = System.out;
    private static final boolean loggerOn = true;
    static{
        try {
            stream = new PrintStream(new FileOutputStream(logFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static <T> void logf(T type){
        if(loggerOn)
             stream.print(type);
    }

    public static <T> void logfn(T type){
        if(loggerOn)
            stream.println(type);
    }
}
