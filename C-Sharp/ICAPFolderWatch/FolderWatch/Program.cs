using System;
using System.Linq;
using System.Collections.Generic;
using System.Text;
using System.Threading.Tasks;
using System.Threading;
using System.IO;
using System.Timers;
using System.Configuration;
using System.Collections.Specialized;
using System.Diagnostics;


namespace FolderWatchNameSpace
{
    public class Program
    {
        private static System.Timers.Timer timer;

        private static string watchDir;
        private static string infectedSubDir;
        private static string cleanSubDir;

        private static int addAllFilesInterval;
        private static int transferTimeout;
        private static int maxInQueue;
        private static int maxInTransfer;
        private static int DEBUGLVL;

        private static Queue<String> filesInQueue = new Queue<String>();
        private static List<String> filesInTransfer = new List<String>();

        /// <summary>
        /// The eventlog to use. If the eventlog is null, stdout will be used instead.
        /// </summary>
        public static EventLog eventLog;// = new System.Diagnostics.EventLog();

        /// <summary>
        /// 1. The main method loads all the settings from app.config.
        /// 2. It creates the required directories for sorting
        /// 3. It instansiates a FileSystemWatcher to look for created files in the watchDir.
        /// 4. Triggers and resets the add-all-files timer and starts transfering.
        /// </summary>
        /// <param name="args">Not used</param>
        public static void Main(string[] args)
        {
            ///////////////////////////////////////////////////////
            // Load App.config
            AppSettingsReader reader = new AppSettingsReader();
            NameValueCollection appSettings = ConfigurationManager.AppSettings;

            infectedSubDir = appSettings["infectedSubDir"];
            cleanSubDir = appSettings["cleanSubDir"];
            watchDir = appSettings["watchDir"];
            maxInQueue = Int32.Parse(appSettings["maxInQueue"]);
            maxInTransfer = Int32.Parse(appSettings["maxInTransfer"]);
            addAllFilesInterval = Int32.Parse(appSettings["addAllFilesInterval"]);
            transferTimeout = Int32.Parse(appSettings["transferTimeout"]);
            DEBUGLVL= Int32.Parse(appSettings["DEBUGLVL"]);

            ///////////////////////////////////////////////////////
            // Check and create directories
            if (!Directory.Exists(watchDir))
            {
                logWarning("Watch path not found!");
                return;
            }

            if (!Directory.Exists(watchDir + infectedSubDir))
            {
                Directory.CreateDirectory(watchDir + infectedSubDir);
                logInformation("Infected folder created");
            }

            if (!Directory.Exists(watchDir + cleanSubDir))
            {
                Directory.CreateDirectory(watchDir + cleanSubDir);
                logInformation("Clean folder created");
            }

            ///////////////////////////////////////////////////////
            // Folder Watch For Items To Scan
            FileSystemWatcher folderWatcher;
            folderWatcher = new FileSystemWatcher(watchDir);
            folderWatcher.Created += (sender, e) =>
            {
                addToQueue(e.FullPath);
                startTransfers();
            };
            folderWatcher.Filter = "*";
            folderWatcher.EnableRaisingEvents = true;

            ///////////////////////////////////////////////////////
            // Start main processes
            initAllFilesTimer();
            addAllFiles(watchDir);
            startTransfers();

            //Console.ReadKey();
        }

        /// <summary>
        /// Determines wheter it should log to the system eventlog or to stdout.
        /// Sends the message to the appropriate place.
        /// </summary>
        /// <param name="msg">The message to log</param>
        /// <param name="debuglvl">The debug level</param>
        private static void logInformation(String msg, int debuglvl = 1)
        {
            if (DEBUGLVL>=debuglvl)
            {
                if (eventLog == null)
                {
                    Console.WriteLine(msg);
                }
                else
                {
                    eventLog.WriteEntry(msg, EventLogEntryType.Information);
                }
            }
        }

        /// <summary>
        /// Determines wheter it should log to the system eventlog or to stdout.
        /// Sends the message to the appropriate place.
        /// </summary>
        /// <param name="msg">The message to log</param>
        /// <param name="debuglvl">The debug level</param>
        private static void logWarning(String msg)
        {
            if (DEBUGLVL >= 0)
            {
                if (eventLog == null)
                {
                    Console.WriteLine(msg);
                }
                else
                {
                    eventLog.WriteEntry(msg, EventLogEntryType.Warning);
                }
            }
        }

        /// <summary>
        /// If more transfers are allowed, it will begin the next in the queue.
        /// </summary>
        private static void startTransfers()
        {
            while (filesInTransfer.Count < maxInTransfer && filesInQueue.Count != 0)
            {
                String file = filesInQueue.Dequeue();
                ThreadPool.QueueUserWorkItem(new WaitCallback(sendFile), file);
                filesInTransfer.Add(file); //It has to be added immediatly, or else the same file can get in the ThreadPool twice
            }
        }

        /// <summary>
        /// If the queue is not full and the file is not already added, the file will be added.
        /// </summary>
        /// <param name="file">file to add to queue</param>
        private static void addToQueue(String file)
        {
            if (filesInQueue.Count < maxInQueue && !filesInQueue.Contains(file) && !filesInTransfer.Contains(file))
            {
                filesInQueue.Enqueue(file);
            }
            //else: The addAllFiles should catch the rest of them later
        }

        /// <summary>
        /// Enum to help handling Exceptions in a good wway.
        /// </summary>
        enum ExecuteResult
        {
            CLEAN, INFECTED, ERROR
        }

        /// <summary>
        /// Starts a seperate thread and gives it a limited time to finish before it is forced to close.
        /// This makes sure a file transfer is not hanging.
        /// Hint: Use an anonymous function for 'func'
        /// () => someMethod(arg1,arg2,argn)
        /// </summary>
        /// <param name="func">The function to run</param>
        /// <param name="timeout">Timeout in milliseconds</param>
        /// <returns></returns>
        private static ExecuteResult timeoutExecute(Func<bool> func, int timeout)
        {
            ExecuteResult result = ExecuteResult.ERROR;
            var thread = new Thread(() =>
                {
                    try
                    {
                        if (func()){
                            result = ExecuteResult.CLEAN;
                        }
                        else
                        {
                            result = ExecuteResult.INFECTED;
                        }
                    }
                    catch {}
                }
            );

            thread.Start();

            var completed = thread.Join(timeout);

            if (!completed)
            {
                thread.Abort();
                return ExecuteResult.ERROR;
            }

            return result;
        }

        /// <summary>
        /// Initializes the timer for adding all files perodically
        /// </summary>
        private static void initAllFilesTimer()
        {
            timer = new System.Timers.Timer();
            timer.Elapsed += new ElapsedEventHandler(timer_Tick);
            timer.Interval = addAllFilesInterval;
            timer.Start();
        }

        /// <summary>
        /// The method to run when the timer runs out.
        /// </summary>
        /// <param name="sender"></param>
        /// <param name="e"></param>
        private static void timer_Tick(object sender, EventArgs e)
        {
            logInformation("Automatically adding all files",1);
            addAllFiles(watchDir);
            startTransfers();
        }

        /// <summary>
        /// All files in the directory that for some reason is not in the queue already is added.
        /// </summary>
        /// <param name="dir"></param>
        private static void addAllFiles(String dir)
        {
            String[] filesInDir = Directory.GetFiles(dir);
            foreach (String file in filesInDir)
            {
                addToQueue(file);
            }
            logInformation("Adding all files!", 2);
        }

        /// <summary>
        /// Sends a file using the ICAP class in the ICAPNameSpace.
        /// </summary>
        /// <param name="e">The file to send</param>
        private static void sendFile(object e)
        {
            // Get the current file information
            string fullFilePath = (string)e;
            string dir = Path.GetDirectoryName(fullFilePath) + @"\";
            string file = Path.GetFileName(fullFilePath);

            logInformation(String.Format("{0,-3}", filesInTransfer.Count) + "/" + String.Format("{0,5}", filesInQueue.Count) + " Scanning file: " + file,2);

            if (!File.Exists(fullFilePath) || IsFileLocked(fullFilePath))
            {
                logWarning("File is locked or doesn't exist!\nIt will be retried within " + addAllFilesInterval / 1000 / 60 + " minutes.");
            }
            else
            {
                ExecuteResult fileStatus;
                using (ICAPNameSpace.ICAP icap = new ICAPNameSpace.ICAP("192.168.1.5", 1344, "avscan", 1024))
                {
                    fileStatus = timeoutExecute(() => icap.scanFile(fullFilePath), transferTimeout); //FIXME: What if it is just a VERY large file?

                    if (fileStatus == ExecuteResult.CLEAN)
                    {
                        File.Move(dir + file, dir + cleanSubDir + @"\" + file);
                    }
                    else if (fileStatus == ExecuteResult.INFECTED)
                    {
                        File.Move(dir + file, dir + infectedSubDir + @"\" + file);
                    }
                    else if (fileStatus == ExecuteResult.ERROR)
                    {
                        logWarning("Transfer timedout or failed for: " + file);
                    }
                }
            }

            //removes itself from the queue and starts new transfers.
            filesInTransfer.Remove(fullFilePath);
            startTransfers();
            return;
        }

        /// <summary>
        /// Checks if a file is locked. In that case it can;t be moved after it is scanned.
        /// </summary>
        /// <param name="filename">The file to check</param>
        /// <returns></returns>
        private static bool IsFileLocked(string filename)
        {
            FileInfo file = new FileInfo(filename);
            FileStream stream = null;
            try
            {
                stream = file.Open(FileMode.Open, FileAccess.ReadWrite, FileShare.None);
            }
            catch (IOException)
            {
                return true;
            }
            finally
            {
                if (stream != null)
                    stream.Close();
            }
            return false;
        }
    }
}