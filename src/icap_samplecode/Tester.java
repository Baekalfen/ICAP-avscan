package icap_samplecode;

import java.io.IOException;

public class Tester {
    public static void main(String[] args)
    {
        try{
            ICAP icap = new ICAP("localhost",1344);

            ICAP.scanResult[] results = new ICAP.scanResult[5];

            String[] files = new String[]{
                "/Users/mads/Downloads/Flux.zip"
            };
            
            for(String file : files) {
                try {
                    boolean result = icap.scanFile2(file);
                    System.out.println(file + ": "+ result);
                } catch (Exception ex) {
                    System.err.println("Could not scan file " + file + ":" + ex.getMessage());
                }
            }
            
            int i=0;
            results[i] = icap.scanFile("/Users/mads/Downloads/Flux.zip");i++;
            //results[i] = icap.scanFile("/home/mads/Downloads/eicar.com.txt");i++;
            //results[i] = icap.scanFile("/home/mads/Downloads/eicar2.com.txt");i++;
            //results[i] = icap.scanFile("/home/mads/Downloads/rfc3507.pdf");i++;
            //results[i] = icap.scanFile("/home/mads/Downloads/rfc3507.zip");i++;
            //results[i] = icap.scanFile("/home/mads/Downloads/rfc3507.zip2");

            for (ICAP.scanResult value:results){
                System.out.println(value);
            }

        }
        catch(IOException e){
            System.out.println(e.getMessage());
        }
        
   }
}
