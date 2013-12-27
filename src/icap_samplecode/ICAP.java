package icap_samplecode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
//import java.net.UnknownHostException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.HashMap;

class ICAP {
    private static final Charset StandardCharsetsUTF8 = Charset.forName("UTF-8");
    
    private String serverIP;
    private int port;
    private Socket client;
    private DataOutputStream out;
    private DataInputStream in;

    public String icapService   = "avscan";
    private final String VERSION   = "1.0";
    private final String USERAGENT = "IT-Kartellet ICAP Client/0.9";

    public final int stdPreviewSize = 1024;
    
    public enum scanResult {
        ALLOWED,
        DISALLOWED,
        ERROR
    };

    public ICAP(String s,int p) throws IOException{
        serverIP = s;
        port = p;        
        //Initialize connection
        System.out.println("Connecting to " + serverIP + " on port " + port);
        client = new Socket(serverIP, port);
        System.out.println("Succesfully connected to: " + client.getRemoteSocketAddress());


        //Openening out stream
        OutputStream outToServer = client.getOutputStream();
        out = new DataOutputStream(outToServer);

        //Openening in stream
        InputStream inFromServer = client.getInputStream();
        in = new DataInputStream(inFromServer);
    }
    
    private String getHeader(Socket client,DataInputStream in,int maxLength) throws IOException{
        byte[] endofheader = new byte[] {'\r','\n','\r','\n'};
        byte[] response = new byte[maxLength];

        int n=0;
        int offset=0;
        while((n = in.read(response, offset++, 1)) != -1) {
            if (offset>5){
                byte[] lastBytes = Arrays.copyOfRange(response, offset-4, offset);
                if (Arrays.equals(endofheader,lastBytes)){
                    String temp = new String(response,0,offset, StandardCharsetsUTF8);
                    return temp;
                }
            }
        }
        System.out.println("\nHeader NOT recieved.");
        return null; // Fixme! Find a way around 'missing return statement'
    }
    
    private String getHTTPResponse(Socket client,DataInputStream in,int maxLength) throws IOException{
        byte[] endofheader = new byte[] {'0','\r','\n','\r','\n'};
        byte[] response = new byte[maxLength];

        int n=0;
        int offset=0;
        while((n = in.read(response, offset++, 1)) != -1) {
            //System.out.print(response[offset-1]);
            if (offset>6){
                byte[] lastBytes = Arrays.copyOfRange(response, offset-5, offset);
                if (Arrays.equals(endofheader,lastBytes)){
                    String temp = new String(response,0,offset, StandardCharsetsUTF8);
                    //System.out.println("\nHeader recieved of length: "+temp.length());
                    return temp;
                }
            }
        }
        System.out.println("\nHTTP response NOT recieved.");
        return null; // Fixme! Find a way around 'missing return statement'
    }
    
    private void sendString(String requestHeader, DataOutputStream out) throws IOException{
        out.write(requestHeader.getBytes(StandardCharsetsUTF8));
    }
    
    private void sendBytes(byte[] chunk,int numberOfBytes, DataOutputStream out) throws IOException{
        for (int i=0;i<numberOfBytes;i++){
            out.write(chunk[i]);
        }
    }
    
    public String getOptions(Socket client,DataInputStream in,DataOutputStream out) throws IOException{
        //Send OPTIONS header and receive response
        //Sending and recieving
        String requestHeader = 
                  "OPTIONS icap://"+serverIP+"/"+icapService+" "+VERSION+"\r\n"
                + "Host: "+serverIP+"\r\n"
                + "User-Agent: "+USERAGENT+"\r\n"
                + "Encapsulated: null-body=0\r\n"
                + "\r\n";

        sendString(requestHeader,out);

        return getHeader(client,in,8192);
    }

    public scanResult scanFile(String filename){
        try{
            FileInputStream fileInStream;
            int fileSize;

            File file = new File(filename);
            fileInStream = new FileInputStream(file);
            fileSize = fileInStream.available();

                //First part of header
            String resBody = "Content-Length: "+fileSize+"\r\n\r\n";

            int previewSize = stdPreviewSize;
            if (fileSize<stdPreviewSize){
                previewSize = fileSize;
            }

            String requestBuffer = 
            "RESPMOD icap://"+serverIP+"/"+icapService+" ICAP/"+VERSION+"\r\n"
            +"Host: "+serverIP+"\r\n"
            +"User-Agent: "+USERAGENT+"\r\n"
            +"Allow: 204\r\n"
            +"Preview: "+previewSize+"\r\n"
            +"Encapsulated: res-hdr=0, res-body="+resBody.length()+"\r\n"
            +"\r\n"
            +resBody
            +Integer.toHexString(previewSize) +"\r\n";

            sendString(requestBuffer,out);

            //Sending preview or, if smaller than previewSize, the whole file.
            int content;
            int i=0;
            byte[] chunk= new byte[1024];

            while ((content = fileInStream.read()) != -1 && i!=previewSize) {
                chunk[i] = (byte) content;
                i++;
            }
            sendBytes(chunk,i,out);
            sendString("\r\n",out);
            if (fileSize<=previewSize){
                requestBuffer = "0; ieof\r\n\r\n";
            }
            else{
                requestBuffer = "0\r\n\r\n";
            }
            sendString(requestBuffer,out);

                // Parse the response! It might not be "100 continue"
                // if fileSize<previewSize, then this is acutally the respond
                // otherwise it is a "go" for the rest of the file.
            if (fileSize>previewSize){
                String parseMe = getHeader(client,in,8192);
                Map<String,String> responseMap = parseHeader(parseMe);

                if (responseMap.get("StatusCode") != null){
                    int status=Integer.parseInt(responseMap.get("StatusCode"));
                    //System.out.println("Status Code:"+status);

                    switch (status){
                        case 100: break; //Continue transfer
                        case 204: return scanResult.ALLOWED;
                        case 404: return scanResult.ERROR;
                        default: return scanResult.ERROR;
                    }
                }
            }


                //Sending remaining part of file
            i=0;
            if (fileSize>previewSize){
                while ((content = fileInStream.read()) != -1) {
                    chunk[i] = (byte) content;
                        //System.out.print((char) content);
                    i++;
                    if (i==chunk.length){
                        sendString(Integer.toHexString(i) +"\r\n",out);
                        sendBytes(chunk,i,out);
                        sendString("\r\n",out);
                        i=0;
                    }
                }
                    // if 'i' is not 0, it means the last part is not transferred.
                if (i!=0){
                    sendString(Integer.toHexString(i) +"\r\n",out);
                    sendBytes(chunk,i,out);
                    sendString("\r\n",out);
                }
                    //Closing file transfer.
                requestBuffer = "0\r\n\r\n";
                sendString(requestBuffer,out);
            }

            fileInStream.close();

            Map<String,String> responseMap;
            String response = getHeader(client,in,8192);
            responseMap = parseHeader(response);

            String status=responseMap.get("StatusCode");
            if (status != null && status.equals("200") || status.equals("204")){
                String infect;
                if ((infect = responseMap.get("X-Infection-Found")) != null){
                    //System.out.println("Virus found: "+infect);
                    // MAYBE NOT UNIVERSAL:
                    getHTTPResponse(client, in, 8192);
                    return scanResult.DISALLOWED;
                }
                else{
                    //System.out.println("No virus found.");
                    return scanResult.ALLOWED;
                }
            }
        }
        catch(IOException e){
            return scanResult.ERROR;
        }
        return scanResult.ERROR;
    }

    public Map<String,String> parseHeader(String response){
        Map<String,String> headers = new HashMap<String, String>();
        
        int x = response.indexOf(" ",0);
        int y = response.indexOf(" ",x+1);
        String statusCode = response.substring(x+1,y);
        headers.put("StatusCode", statusCode);
        
        int i = response.indexOf("\r\n",y);
        i+=2;
        
        while (i+2!=response.length()) {
            int n = response.indexOf(":",i);
            String key = response.substring(i, n);

            n += 2;
            i = response.indexOf("\r\n",n);
            String value = response.substring(n, i);

            headers.put(key, value);
            i+=2;
        }
        
        return headers;
    }
    
    public void disconnect() throws IOException{
        client.close();
    }
}
