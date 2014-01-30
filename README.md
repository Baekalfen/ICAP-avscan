<!--
Table of Contents
=================
 -->

Introduction
============
This project is intended to be used with Blue Coat ProxyAV (BCP), but other ICAP anti-virus system might work as well. The library is split into 2 versions, 1 written in Java and 1 written in C#. The versions are identical in behavior and more or less identical code-wise too.

The C# version has the addition of a 'FolderWatch' application (also available as a service) that watches for files added to a specified directory. When a new file is found, it is scanned and sorted depending on it's virus-status into 2 different subdirectories.

Example of use
==============



The ICAP Protocol
=================

Introduction
------------
The __Internet Content Adaption Protocol__ is heavily inspired by HTTP but the use differs on some core aspects. ICAP is normally implemented as an addition to HTTP, where the HTTP request for a web page can be encapsulated and modified before the user gets the content. This way a content filter, like a anti-virus software, can be transparent to the end-user.
In this project, it is just used as a file transfer protocol with a feedback from the server about the file's virus-status.

Error codes
-----------

When working with the protocol, it often comes in handy to have the error codes and descriptions

| Code | Description                                                                                                                                                           |
|------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|      | __Informational Codes__                                                                                                                                               |
| 100  | Continue After ICAP preview                                                                                                                                           |
|      | __Success codes__                                                                                                                                                     |
| 200  | OK                                                                                                                                                                    |
| 204  | No modification needed                                                                                                                                                |
|      | __Client error codes__                                                                                                                                                |
| 400  | Bad request                                                                                                                                                           |
| 403  | Forbidden                                                                                                                                                             |
| 404  | ICAP Service not found                                                                                                                                                |
| 405  | Method not allowed for service                                                                                                                                        |
| 408  | Request timeout ICAP server gave up waiting for a request from an ICAP client.                                                                                        |
|      | __Server error codes__                                                                                                                                                |
| 500  | Server error. Error on the ICAP server, such as ``out of disk space''.                                                                                                |
| 501  | Method not implemented. This response is illegal for an OPTIONS request since implementation of OPTIONS is mandatory.                                                 |
| 502  | Bad Gateway. This is an ICAP proxy and proxying produced an error.                                                                                                    |
| 503  | Service overloaded. The ICAP server has exceeded a maximum connection limit associated with this service; the ICAP client should not exceed this limit in the future. |
| 505  | ICAP version not supported by server.


Use of The Libraries
============================

Introduction
------------
Both versions have the same interface of public and private methods. Although they are not equal in the way of handling exceptions.
Both versions will disconnect automatically when caught by the garbage collection.
The ICAP class is __NOT__ thread-safe. If you have to transfer more than one file at a time, then instantiate the ICAP class for each connection.

Example of C# Version
---------------------
The example below is fairly simple and involves only scanning 1 file. The scanFile() method returns __true__, if the file is __clean__ and __false__ if the file is __infected__.

    class Program
    {
        static void Main(string[] args)
        {
            try
            {
                ICAPNameSpace.ICAP icap = new ICAPNameSpace.ICAP("192.168.1.5",1344,"avscan");
                try
                {
                    string file = @"C:\foo\bar.exe";
                    Console.Write(file + ": ");
                    bool result = icap.scanFile(file);
                    Console.WriteLine(result);
                }
                catch (Exception e)
                {
                    Console.WriteLine("Could not scan file " + file + ":" + e);
                }
            }
            catch(Exception e)
            {
                Console.WriteLine("Error occurred when connecting" + e);
            }
        }
    }

Example of Java Version
-----------------------
The example below is fairly simple and involves only scanning 1 file. The scanFile() method returns __true__, if the file is __clean__ and __false__ if the file is __infected__.

    public class Tester
    {
        public static void main(String[] args)
        {
            try
            {
                ICAP icap = new ICAP("192.168.1.5",1344,"avscan");
                try
                {
                    String file = "\foo\bar.txt"
                    System.out.print(file + ": ");
                    boolean result = icap.scanFile(file);
                    System.out.println(result);
                }
                catch (Exception e) {
                    System.out.println("Could not scan file " + file + ": " + e.getMessage());
                }
            }
            catch(Exception e){
                System.out.println("Error occurred when connecting" + e.getMessage());
            }
       }
    }

Public Java methods
------------

###ICAP(String __IP__, int __port__, String __ICAP service__)
Given an IP-address, a port and an ICAP service name it will initialize a socket connection to the BCP. The preview size will be determined by an 'option' request from the BCP.
This method throws an IOException if the socket connection or IO streams cannot be started. This lets the user of the class responsible for making a decision if such an error occurs. There is no good way to solve this exception automatically.

###ICAP(String __IP__, int __port__, String __ICAP service__, int __preview size__)
Same as the one above, but the preview size assigned in the method call will be used to transfer files. Use this method to minimize overhead, but be sure to not change the BCP settings.

###scanFile(String __filename__)
Given a filename, it will send the given file through the initialized connection to the BCP. If the file is __clean__ it will return __true__ and if the file is __infected__ it will return __false__.


Public C# methods
----------

_Exceptions?_

###ICAP(String __IP__, int __port__, String __ICAP service__, int __preview size (optional parameter)__)
Given an IP-address, a port, an ICAP service name and __optionally__ an preview size it will initialize a socket connection to the BCP. If the preview size is assigned in the method call it will be used to transfer files. Use this method to minimize overhead, but be sure to not change the BCP settings. If the preview size is __not__ assigned, the preview size will be determined by an 'option' request from the BCP.

###scanFile(String __filename__)
Given a filename, it will send the given file through the initialized connection to the BCP. If the file is __clean__ it will return __true__ and if the file is __infected__ it will return __false__.

Use of FolderWatch
==========================

Introduction
------------
The FolderWatch subproject allow a Windows machine to continually scan files in a directory and sort them depending on their virus-status. FolderWatch can be run from a command prompt or as a service. The directory to scan and the subdirectories to sort files into are defined in the __app.config__ file associated.

Setup
-----


Behavior
--------
When the FolderWatch application starts, it will add all the current files in the directory to a scan-queue. This list is iterated through when there is open for new connections. The max amount of connections is specified in the app.config as 'maxInTransfer'. The scan-queue limit is defined in app.config as 'maxInQueue'.

Apart from adding all current files in the directory to the scan-queue, it will also watch the directory for created files. When a file is created, it is added to the scan-queue. The maxInQueue limit ensure that system memory is not clogged with waiting files. If the scan-queue is full when a new file is added, it will be ignored.

In the app.config a 'addAllFilesInterval' is defined in milliseconds. When the time has passed, it will added files in the directory to the scan-queue so that they aren't ignored completely.


The console application will print out to the command prompt with informations, error and so on. Alternatively, if it is installed as a service, it will send all messages to the system's event log.