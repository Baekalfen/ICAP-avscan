package icap_samplecode;

import java.io.IOException;


public class TesterDirect {
    public static void main(String[] args)
    {
        try{
        	 ICAP icap = new ICAP("93.16.212.37",1344,"resp");
        	//Malware test downloadable from http://www.eicar.org/85-0-Download.html
        	 
        	 String[] files = new String[]{
     			"C:\\Something.txt","C:\\eicarzip.zip","C:\\Something2.txt"
     		};
            
            for(String file : files) {
                try {
                    System.out.print(file + ": ");
                    boolean result = icap.scanFile(file);
                    System.out.println(result ? "Clean" : "Infected");
                } catch (ICAPException ex) {
                    System.err.println("Could not scan file " + file + ": " + ex.getMessage());
                } catch (IOException ex) {
                    System.err.println("IO error occurred when scanning file " + file + ": " + ex.getMessage());
                }
            }
        }
        catch(IOException e){
        	System.out.println("this");
            System.out.println(e.getMessage());
        }
        catch(ICAPException e){
        	System.out.println("these");
            System.out.println(e.getMessage());
        }
        
   }
}