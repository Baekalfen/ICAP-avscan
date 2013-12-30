package icap_samplecode;

import java.io.IOException;

public class Tester {
    public static void main(String[] args)
    {
        try{
            ICAP icap = new ICAP("192.168.1.5",1344,"avscan");
            
            String[] files = new String[]{
                "/home/mads/Downloads/Client.py",
                "/home/mads/Downloads/eicar.com.txt",
                "/home/mads/Downloads/eicar2.com.txt",
                "/home/mads/Downloads/rfc3507.pdf",
                "/home/mads/Downloads/rfc3507.zip"
            };
            
            for(String file : files) {
                try {
                    boolean result = icap.scanFile(file);
                    System.out.println(file + ": "+ result);
                } catch (Exception ex) {
                    System.err.println("Could not scan file " + file + ":" + ex.getMessage());
                }
            }
        }
        catch(IOException e){
            System.out.println(e.getMessage());
        }
        catch(ICAPException e){
            System.out.println(e.getMessage());
        }
        
   }
}
