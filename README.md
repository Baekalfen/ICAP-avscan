<!--
Table of Contents
=================
 -->

Introduction
============
This project is intended to be used with Blue Coat ProxyAv, but other ICAP anti-virus system might work aswell. The library is split into 2 versions, 1 written in Java and 1 written in C#. The versions are identical in behavior and more or less identical codewise too.

The C# version has the addition of a 'FolderWatch' application (also available as a service) that watches for files added to a specified directory. When a new file is found, it is scanned and sorted depending on it's virus-status into 2 different subdirectories.

Use of The Libraries
============================

Introduction
------------
Both versions have the same interface of public and private methods. Although they are not equal in the way of handling exceptions.

Example of C# Version
---------------------
This is normal text

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
this is normal text

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


Public C# methods
----------


Use of FolderWatch
==========================

Introduction
------------

Setup
-----

Behavior
--------