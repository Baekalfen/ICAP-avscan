package icap_samplecode;

import java.io.IOException;

public class Tester {
    public static void main(String[] args)
    {
        try{
            ICAP icap = new ICAP("localhost",1344);

            ICAP.scanResult[] results = new ICAP.scanResult[5];

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

            icap.disconnect();
        }
        catch(IOException e){
            System.out.println(e.getMessage());
        }
        
   }
}
