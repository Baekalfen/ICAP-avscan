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
    private Socket client = null;
    private DataOutputStream out;
    private DataInputStream in;

    private String icapService;
    private final String VERSION   = "1.0";
    private final String USERAGENT = "IT-Kartellet ICAP Client/0.9";

    private int stdPreviewSize = 1024;
    private final int stdRecieveLength = 8192;
    
    private final String ICAPTERMINATOR = "\r\n\r\n";
    private final String HTTPTERMINATOR = "0\r\n\r\n";

    /**
     * Initializes the socket connection and IO streams. It asks the server for the available options and
     * changes settings to match it.
     * @param s The IP address to connect to.
     * @param p The port in the host to use.
     * @param icapService The service to use (fx "avscan").
     * @throws IOException
     * @throws ICAPException 
     */
    public ICAP(String s,int p, String icapService) throws IOException, ICAPException{
        this.icapService = icapService;
        this.serverIP = s;
        this.port = p;        
        //Initialize connection
        if ((client = new Socket(serverIP, port)) == null){
            throw new ICAPException("Could not open socket connection");
        }

        //Openening out stream
        OutputStream outToServer = client.getOutputStream();
        out = new DataOutputStream(outToServer);

        //Openening in stream
        InputStream inFromServer = client.getInputStream();
        in = new DataInputStream(inFromServer);
        
        
        String parseMe = getOptions();
        Map<String,String> responseMap = parseHeader(parseMe);

        if (responseMap.get("StatusCode") != null){
            int status=Integer.parseInt(responseMap.get("StatusCode"));
            //System.out.println("Status Code:"+status);

            switch (status){
                case 200:
                    String var;
                    if ((var = responseMap.get("Preview")) != null){
                        stdPreviewSize=Integer.parseInt(var);
                    };break;
                default: throw new ICAPException("Could not get preview size from server");
            }
        }
        else{
            throw new ICAPException("Could not get options from server");
        }
    }
    
    /**
     * Initializes the socket connection and IO streams. This overload doesn't
     * use getOptions(), instead a previewSize is specified.
     * @param s The IP address to connect to.
     * @param p The port in the host to use.
     * @param icapService The service to use (fx "avscan").
     * @param previewSize Amount of bytes to  send as preview.
     * @throws IOException
     * @throws ICAPException 
     */
    public ICAP(String s,int p, String icapService, int previewSize) throws IOException, ICAPException{
        this.icapService = icapService;
        serverIP = s;
        port = p;        
        //Initialize connection
        if ((client = new Socket(serverIP, port)) == null){
            throw new ICAPException("Could not open socket connection");
        }

        //Openening out stream
        OutputStream outToServer = client.getOutputStream();
        out = new DataOutputStream(outToServer);

        //Openening in stream
        InputStream inFromServer = client.getInputStream();
        in = new DataInputStream(inFromServer);
        
        stdPreviewSize = previewSize;
    }
    
    /**
     * Automatically asks for the servers available options and returns the raw response as a String.
     * @return String of the servers response.
     * @throws IOException
     * @throws ICAPException 
     */
    private String getOptions() throws IOException, ICAPException{
        //Send OPTIONS header and receive response
        //Sending and recieving
        String requestHeader = 
                  "OPTIONS icap://"+serverIP+"/"+icapService+" "+VERSION+"\r\n"
                + "Host: "+serverIP+"\r\n"
                + "User-Agent: "+USERAGENT+"\r\n"
                + "Encapsulated: null-body=0\r\n"
                + "\r\n";

        sendString(requestHeader);

        return getHeader(ICAPTERMINATOR);
    }
    
    /**
     * Receive an expected ICAP header as response of a request. The returned String should be parsed with parseHeader()
     * @param maxLength
     * @return String of the raw response
     * @throws IOException
     * @throws ICAPException 
     */
    private String getHeader(String terminator) throws IOException, ICAPException{
        byte[] endofheader = terminator.getBytes(StandardCharsetsUTF8);//new byte[] {'\r','\n','\r','\n'};
        byte[] response = new byte[stdRecieveLength];

        int n;
        int offset=0;
        while(((n = in.read(response, offset, stdRecieveLength - offset)) != -1) && (offset < stdRecieveLength)) {
            offset += n;
            if (offset>endofheader.length+13){ // 13 is the smallest possible message "ICAP/1.0 xxx "
                byte[] lastBytes = Arrays.copyOfRange(response, offset-endofheader.length, offset);
                if (Arrays.equals(endofheader,lastBytes)){
                    String temp = new String(response,0,offset, StandardCharsetsUTF8);
                    return temp;
                }
            }
        }
        throw new ICAPException("Header NOT recieved.");
    }
    
    /**
     * Sends a String through the socket connection. Used for sending ICAP/HTTP headers.
     * @param requestHeader
     * @throws IOException 
     */
    private void sendString(String requestHeader) throws IOException{
        out.write(requestHeader.getBytes(StandardCharsetsUTF8));
    }
    
    /**
     * Sends bytes of data from a byte-array through the socket connection. Used to send filedata.
     * @param chunk The byte-array to send.
     * @throws IOException 
     */
    private void sendBytes(byte[] chunk) throws IOException{
        for (int i=0;i<chunk.length;i++){
            out.write(chunk[i]);
        }
    }

    /**
     * Given a filepath, it will send the file to the server and return true,
     * if the server accepts the file. Visa-versa, false if the server rejects it.
     * @param filename Relative or absolute filepath to a file.
     * @return Returns true when no infection is found.
     */
    public boolean scanFile(String filename) throws IOException,ICAPException{
        
        //Differs from C# version. it uses a using statement for the filestream
        // If Java 7 is accepted, then use try(FileInputStream fileInStream = new FileInputStream(file)){}
        FileInputStream fileInStream = null;
        int fileSize;

        try {
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

            sendString(requestBuffer);

            //Sending preview or, if smaller than previewSize, the whole file.
            byte[] chunk= new byte[previewSize];

            fileInStream.read(chunk);
            sendBytes(chunk);
            sendString("\r\n");
            if (fileSize<=previewSize){
                sendString("0; ieof\r\n\r\n");
            }
            else{
                sendString("0\r\n\r\n");
            }

            // Parse the response! It might not be "100 continue"
            // if fileSize<previewSize, then this is acutally the respond
            // otherwise it is a "go" for the rest of the file.
            if (fileSize>previewSize){
                String parseMe = getHeader(ICAPTERMINATOR);
                Map<String,String> responseMap = parseHeader(parseMe);

                if (responseMap.get("StatusCode") != null){
                    int status=Integer.parseInt(responseMap.get("StatusCode"));

                    switch (status){
                        case 100: break; //Continue transfer
                        case 204: return true;
                        case 404: throw new ICAPException("404: ICAP Service not found");
                        default: throw new ICAPException("Server returned unknown status code:"+status);
                    }
                }
            }


            //Sending remaining part of file
            if (fileSize>previewSize){
                while ((fileInStream.read(chunk)) != -1) {
                    sendString(Integer.toHexString(chunk.length) +"\r\n");
                    sendBytes(chunk);
                    sendString("\r\n");
                }
                //Closing file transfer.
                requestBuffer = "0\r\n\r\n";
                sendString(requestBuffer);
            }

            //fileInStream.close();

            Map<String,String> responseMap;
            String response = getHeader(ICAPTERMINATOR);
            responseMap = parseHeader(response);

            String status=responseMap.get("StatusCode");
            if (status != null && (status.equals("200") || status.equals("204"))){
                if (status.equals("204")){return true;} //Unmodified

                if (status.equals("200")){ //OK
                    response = getHeader(HTTPTERMINATOR);
                    //response = getHeader(ICAPTERMINATOR);
                    int x = response.indexOf(" ",0);
                    int y = response.indexOf(" ",x+1);
                    String statusCode = response.substring(x+1,y);
                    if (statusCode.equals("403")){
                        return false;
                    }
                }
            }
            throw new ICAPException("Unrecognized or no status code in response header.");
        }
        finally{
            if (fileInStream != null){
                fileInStream.close();
            }
        }
    }
    
    /**
     * Given a raw response header as a String, it will parse through it and return a HashMap of the result
     * @param response A raw response header as a String.
     * @return 
     */
    private Map<String,String> parseHeader(String response){
        Map<String,String> headers = new HashMap<String, String>();

        /****SAMPLE:****
         * ICAP/1.0 204 Unmodified
         * Server: C-ICAP/0.1.6
         * Connection: keep-alive
         * ISTag: CI0001-000-0978-6918203
         */
        // The status code is located between the first 2 whitespaces.
        // Read status code
        int x = response.indexOf(" ",0);
        int y = response.indexOf(" ",x+1);
        String statusCode = response.substring(x+1,y);
        headers.put("StatusCode", statusCode);
        
        // Each line in the sample is ended with "\r\n". 
        // When (i+2==response.length()) The end of the header have been reached.
        // The +=2 is added to skip the "\r\n".
        // Read headers
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
    
    /**
     * Terminates the socket connecting to the ICAP server.
     * @throws IOException 
     */
    private void disconnect() throws IOException{
        if(client != null) {
            client.close();
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            disconnect();
        } finally {
            super.finalize();
        }
    }
}
