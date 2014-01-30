<!--
Table of Contents
=================
 -->

Introduction
============
This project is intended to be used with Blue Coat ProxyAV (BCP), but other ICAP anti-virus system might work aswell. The library is split into 2 versions, 1 written in Java and 1 written in C#. The versions are identical in behavior and more or less identical code-wise too.

The C# version has the addition of a 'FolderWatch' application (also available as a service) that watches for files added to a specified directory. When a new file is found, it is scanned and sorted depending on it's virus-status into 2 different subdirectories.



The ICAP Protocol
=================

Introduction
------------

Error codes
-----------
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
Both versions will disconnect automatically when caught by the garbage collection

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

this is too

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

Setup
-----

Behavior
--------