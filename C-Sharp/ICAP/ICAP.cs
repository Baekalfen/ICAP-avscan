using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Net;
using System.Net.Sockets;
using System.IO;

namespace ICAPNameSpace
{
    public class ICAP
    {
        private String serverIP;
        private int port;

        private String icapService;
        private const String VERSION = "1.0";
        private const String USERAGENT = "IT-Kartellet ICAP Client/0.9";
        private const String ICAPTERMINATOR = "\r\n\r\n";
        private const String HTTPTERMINATOR = "0\r\n\r\n";

        private int stdPreviewSize;
        private const int stdRecieveLength = 8192;
        private const int stdSendLength = 8192;

        private byte[] buffer = new byte[8192];

        private Socket sender;


        private String tempString;
        /**
            * Initializes the socket connection and IO streams. It askes the server for the available options and
            * changes settings to match it.
            * @param s The IP address to connect to.
            * @param p The port in the host to use.
            * @param icapService The service to use (fx "avscan").
            * @throws IOException
            * @throws ICAPException 
            */
        public ICAP(String serverIP, int port, String icapService)
        {
            this.icapService = icapService;
            this.serverIP = serverIP;
            this.port = port;

            //Initialize connection
            IPAddress ipAddress = IPAddress.Parse(serverIP);
            IPEndPoint remoteEP = new IPEndPoint(ipAddress, port);

            // Create a TCP/IP  socket.
            sender = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
            sender.Connect(remoteEP);            
            
            String parseMe = getOptions();
            Dictionary<string, string> responseMap = parseHeader(parseMe);

            responseMap.TryGetValue("StatusCode", out tempString);
            if (tempString != null)
            {
                int status = Convert.ToInt16(tempString);

                switch (status)
                {
                    case 200:
                        responseMap.TryGetValue("Preview", out tempString);
                        if (tempString != null)
                        {
                            stdPreviewSize = Convert.ToInt16(tempString);
                        }; break;
                    default: throw new ICAPException("Could not get preview size from server");
                }
            }
            else
            {
                throw new ICAPException("Could not get options from server");
            }
        }

        public bool scanFile(String filepath)
        {
            FileStream fileStream = new FileStream(filepath, FileMode.Open);
            int fileSize = (int)fileStream.Length;

            //First part of header
            String resBody = "Content-Length: " + fileSize + "\r\n\r\n";

            int previewSize = stdPreviewSize;
            if (fileSize < stdPreviewSize)
            {
                previewSize = fileSize;
            }

            byte[] requestBuffer = Encoding.ASCII.GetBytes(
                "RESPMOD icap://" + serverIP + "/" + icapService + " ICAP/" + VERSION + "\r\n"
                + "Host: " + serverIP + "\r\n"
                + "User-Agent: " + USERAGENT + "\r\n"
                + "Allow: 204\r\n"
                + "Preview: " + previewSize + "\r\n"
                + "Encapsulated: res-hdr=0, res-body=" + resBody.Length + "\r\n"
                + "\r\n"
                + resBody
                + previewSize.ToString("X") + "\r\n");

            sender.Send(requestBuffer);

            //Sending preview or, if smaller than previewSize, the whole file.
            byte[] chunk = new byte[previewSize];

            fileStream.Read(chunk, 0, previewSize);
            sender.Send(chunk);
            sender.Send(Encoding.ASCII.GetBytes("\r\n"));
            if (fileSize <= previewSize)
            {
                sender.Send(Encoding.ASCII.GetBytes("0; ieof\r\n\r\n"));
            }
            else
            {
                sender.Send(Encoding.ASCII.GetBytes("0\r\n\r\n"));
            }

            // Parse the response! It might not be "100 continue".
            // if fileSize<previewSize, then the stream waiting is actually the allowed/disallowed signal
            // otherwise it is a "go" for the rest of the file.
            Dictionary<String, String> responseMap = new Dictionary<string,string>();
            int status;

            if (fileSize > previewSize)
            {
                String parseMe = getHeader(ICAPTERMINATOR);
                responseMap = parseHeader(parseMe);

                responseMap.TryGetValue("StatusCode", out tempString);
                if (tempString != null)
                {
                    status = Convert.ToInt16(tempString);

                    switch (status)
                    {
                        case 100: break; //Continue transfer
                        case 204: return true;
                        case 404: throw new ICAPException("404: ICAP Service not found");
                        default: throw new ICAPException("Server returned unknown status code:" + status);
                    }
                }
            }

            //Sending remaining part of file
            if (fileSize > previewSize)
            {
                int offset = previewSize;
                int n;
                byte[] buffer = new byte[stdSendLength];
                while ((n = fileStream.Read(buffer, 0, stdSendLength)) > 0)
                {
                    offset += n;  // offset for next reading
                    sender.Send(Encoding.ASCII.GetBytes(buffer.Length.ToString("X") + "\r\n"));
                    sender.Send(buffer);
                    sender.Send(Encoding.ASCII.GetBytes("\r\n"));
                }
                //Closing file transfer.
                sender.Send(Encoding.ASCII.GetBytes("0\r\n\r\n"));
            }
            fileStream.Close();

            responseMap.Clear();
            String response = getHeader(ICAPTERMINATOR);
            responseMap = parseHeader(response);

            responseMap.TryGetValue("StatusCode", out tempString);
            if (tempString != null)
            {
                status = Convert.ToInt16(tempString);


                if (status==204) { return true; } //Unmodified

                if (status==200) //OK - The ICAP status is ok, but the encapsulated HTTP status will likely be different
                {
                    response = getHeader(HTTPTERMINATOR);
                    int x = response.IndexOf(" ", 0);                           // See how this works in parseHeader()
                    int y = response.IndexOf(" ", x + 1);                       //
                    String statusCode = response.Substring(x + 1, y - x - 1);   //

                    if (statusCode.Equals("403"))
                    {
                        return false;
                    }
                }
            }
            throw new ICAPException("Unrecognized or no status code in response header.");
        }

        public string getOptions()
        {
            byte[] msg = Encoding.ASCII.GetBytes(
                "OPTIONS icap://" + serverIP + "/" + icapService + " " + VERSION + "\r\n"
                + "Host: " + serverIP + "\r\n"
                + "User-Agent: " + USERAGENT + "\r\n"
                + "Encapsulated: null-body=0\r\n"
                + "\r\n");
            sender.Send(msg);

            return getHeader(ICAPTERMINATOR);
        }

        public String getHeader(String terminator) //"\r\n\r\n"
        {
            byte[] buffer = new byte[stdRecieveLength];
            byte[] endofheader = System.Text.Encoding.UTF8.GetBytes(terminator);
            int offset = 0;
            int n = 0;
            while (((n = sender.Receive(buffer, offset, stdRecieveLength - offset, SocketFlags.None)) != 0) && !(offset>stdRecieveLength)) // last part is to secure against DDOS
            {
                offset += n;
                if (offset > endofheader.Length + 13) // 13 is the smallest possible message (ICAP/1.0 xxx\r\n) or (HTTP/1.0 xxx\r\n)
                {
                    byte[] lastBytes = new byte[endofheader.Length];
                    Array.Copy(buffer, offset - endofheader.Length, lastBytes, 0, endofheader.Length);
                    if (endofheader.SequenceEqual(lastBytes))
                    {
                        return Encoding.ASCII.GetString(buffer, 0, offset);
                    }
                }
            }
            throw new ICAPException("Error in getHeader() method");
        }

        public Dictionary<String, String> parseHeader(String response)
        {
            Dictionary<String, String> headers = new Dictionary<String, String>();

            /****SAMPLE:****
             * ICAP/1.0 204 Unmodified
             * Server: C-ICAP/0.1.6
             * Connection: keep-alive
             * ISTag: CI0001-000-0978-6918203
             */
            // The status code is located between the first 2 whitespaces.
            // Read status code
            int x = response.IndexOf(" ", 0);
            int y = response.IndexOf(" ", x + 1);
            String statusCode = response.Substring(x + 1, y - x - 1);
            headers.Add("StatusCode", statusCode);

            // Each line in the sample is ended with "\r\n". 
            // When (i+2==response.length()) The end of the header have been reached.
            // The +=2 is added to skip the "\r\n".
            // Read headers
            int i = response.IndexOf("\r\n", y);
            i += 2;
            while (i + 2 != response.Length)
            {
                int n = response.IndexOf(":", i);
                String key = response.Substring(i, n - i);

                n += 2;
                i = response.IndexOf("\r\n", n);
                String value = response.Substring(n, i - n);

                headers.Add(key, value);
                i += 2;
            }
            return headers;
        }

        public class ICAPException : Exception
        {
            public ICAPException(string message)
                : base(message)
            {
            }

        }

        ~ICAP()
        {
            sender.Shutdown(SocketShutdown.Both);
            sender.Dispose();
            sender.Close();
        }
    }
}
