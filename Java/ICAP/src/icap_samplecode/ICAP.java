package icap_samplecode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
    private final String USERAGENT = "IT-Kartellet ICAP Client/1.1";
    private final String ICAPTERMINATOR = "\r\n\r\n";
    private final String HTTPTERMINATOR = "0\r\n\r\n";
    
    private int stdPreviewSize;
    private final int stdRecieveLength = 8192;
    private final int stdSendLength = 8192;

    private String tempString;
    
    /**
     * Initializes the socket connection and IO streams. It asks the server for the available options and
     * changes settings to match it.
     * @param s The IP address to connect to.
     * @param p The port in the host to use.
     * @param icapService The service to use (fx "avscan").
     * @throws IOException
     * @throws ICAPException 
     */
    public ICAP(String serverIP, int port, String icapService) throws IOException, ICAPException{
        this.icapService = icapService;
        this.serverIP = serverIP;
        this.port = port;
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
            int status = Integer.parseInt(responseMap.get("StatusCode"));

            switch (status){
                case 200:
                    tempString = responseMap.get("Preview");
                    if (tempString != null){
                        stdPreviewSize=Integer.parseInt(tempString);
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
     * Given a filepath, it will send the file to the server and return true,
     * if the server accepts the file. Visa-versa, false if the server rejects it.
     * @param filename Relative or absolute filepath to a file.
     * @return Returns true when no infection is found.
     */
    public boolean scanFile(String filename) throws IOException,ICAPException{
        
        //Differs from C# version. it uses a using statement for the filestream
        // If Java 7 is accepted, then use try(FileInputStream fileInStream = new FileInputStream(file)){}
        FileInputStream fileInStream = null;

        try {
            File file = new File(filename);
            fileInStream = new FileInputStream(file);
            int fileSize = fileInStream.available();

            //First part of header
            String resBody = "Content-Length: "+fileSize+"\r\n\r\n";

            int previewSize = stdPreviewSize;
            if (fileSize < stdPreviewSize){
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
            byte[] chunk = new byte[previewSize];

            fileInStream.read(chunk);
            sendBytes(chunk);
            sendString("\r\n");
            if (fileSize<=previewSize){
                sendString("0; ieof\r\n\r\n");
            }
            else if (previewSize != 0){
                sendString("0\r\n\r\n");
            }

            // Parse the response! It might not be "100 continue"
            // if fileSize<previewSize, then this is acutally the respond
            // otherwise it is a "go" for the rest of the file.
            Map<String,String> responseMap = new HashMap<String,String>();
            int status;
            
            if (fileSize>previewSize){
                String parseMe = getHeader(ICAPTERMINATOR);
                responseMap = parseHeader(parseMe);

                tempString = responseMap.get("StatusCode");
                if (tempString != null){
                    status = Integer.parseInt(tempString);

                    switch (status){
                        case 100: break; //Continue transfer
                        case 200: return false;
                        case 204: return true;
                        case 404: throw new ICAPException("404: ICAP Service not found");
                        default: throw new ICAPException("Server returned unknown status code:"+status);
                    }
                }
            }

            //Sending remaining part of file
            if (fileSize > previewSize){
                byte[] buffer = new byte[stdSendLength];
                while ((fileInStream.read(buffer)) != -1) {
                    sendString(Integer.toHexString(buffer.length) +"\r\n");
                    sendBytes(buffer);
                    sendString("\r\n");
                }
                //Closing file transfer.
                requestBuffer = "0\r\n\r\n";
                sendString(requestBuffer);
            }
            //fileInStream.close();

            responseMap.clear();
            String response = getHeader(ICAPTERMINATOR);
            responseMap = parseHeader(response);

            tempString=responseMap.get("StatusCode");
            if (tempString != null){
                status = Integer.parseInt(tempString);

                if (status == 204){return true;} //Unmodified

                if (status == 200){ //OK - The ICAP status is ok, but the encapsulated HTTP status will likely be different
                    response = getHeader(HTTPTERMINATOR);
                    int x = response.indexOf("<title>",0);
                    int y = response.indexOf("</title>",x);
                    String statusCode = response.substring(x+7,y);
                    
                    if (statusCode.equals("ProxyAV: Access Denied")){
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
     * Automatically asks for the servers available options and returns the raw response as a String.
     * @return String of the servers response.
     * @throws IOException
     * @throws ICAPException 
     */
    private String getOptions() throws IOException, ICAPException{
        //Send OPTIONS header and receive response
        //Sending and recieving
        String requestHeader = 
                  "OPTIONS icap://"+serverIP+"/"+icapService+" ICAP/"+VERSION+"\r\n"
                + "Host: "+serverIP+"\r\n"
                + "User-Agent: "+USERAGENT+"\r\n"
                + "Encapsulated: null-body=0\r\n"
                + "\r\n";

        sendString(requestHeader);

        return getHeader(ICAPTERMINATOR);
    }
    
    /**
     * Receive an expected ICAP header as response of a request. The returned String should be parsed with parseHeader()
     * @param terminator
     * @return String of the raw response
     * @throws IOException
     * @throws ICAPException 
     */
    private String getHeader(String terminator) throws IOException, ICAPException{
        byte[] endofheader = terminator.getBytes(StandardCharsetsUTF8);
        byte[] buffer = new byte[stdRecieveLength];

        int n;
        int offset=0;
        //stdRecieveLength-offset is replaced by '1' to not receive the next (HTTP) header.
        while((offset < stdRecieveLength) && ((n = in.read(buffer, offset, 1)) != -1)) { // first part is to secure against DOS
            offset += n;
            if (offset>endofheader.length+13){ // 13 is the smallest possible message "ICAP/1.0 xxx "
                byte[] lastBytes = Arrays.copyOfRange(buffer, offset-endofheader.length, offset);
                if (Arrays.equals(endofheader,lastBytes)){
                    return new String(buffer,0,offset, StandardCharsetsUTF8);
                }
            }
        }
        throw new ICAPException("Error in getHeader() method");
    }
    
    /**
     * Given a raw response header as a String, it will parse through it and return a HashMap of the result
     * @param response A raw response header as a String.
     * @return HashMap of the key,value pairs of the response
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
        while (i+2!=response.length() && response.substring(i).contains(":")) {

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
