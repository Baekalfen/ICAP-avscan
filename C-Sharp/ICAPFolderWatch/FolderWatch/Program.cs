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


namespace FileWatch
{
    //TODO: Make it a service
    class Program
    {
        private static string infectedSubDir;
        private static string cleanSubDir;

        private static System.Timers.Timer timer;
        private static string watchDir;

        private static int addAllFilesInterval;
        private static int transferTimeout;
        private static int maxInQueue;
        private static int maxInTransfer;

        private static Queue<String> filesInQueue = new Queue<String>();
        private static List<String> filesInTransfer = new List<String>();

        static void Main(string[] args)
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

            ///////////////////////////////////////////////////////
            // Check and create directories
            if (!Directory.Exists(watchDir))
            {
                Console.WriteLine("Watch path not found!");
                return;
            }

            if (!Directory.Exists(watchDir + infectedSubDir))
            {
                Directory.CreateDirectory(watchDir + infectedSubDir);
                Console.WriteLine("Infected folder created");
            }

            if (!Directory.Exists(watchDir + cleanSubDir))
            {
                Directory.CreateDirectory(watchDir + cleanSubDir);
                Console.WriteLine("Clean folder created");
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

            Console.ReadKey();
        }

        private static void startTransfers()
        {
            while (filesInTransfer.Count < maxInTransfer && filesInQueue.Count != 0)
            {
                String file = filesInQueue.Dequeue();
                ThreadPool.QueueUserWorkItem(new WaitCallback(FileCreated), file);
                filesInTransfer.Add(file); //It has to be added immediatly, or else the same file can get in the ThreadPool twice
            }
        }

        private static void addToQueue(String file)
        {
            if (filesInQueue.Count < maxInQueue && !filesInQueue.Contains(file) && !filesInTransfer.Contains(file))
            {
                filesInQueue.Enqueue(file);
            }
            //The addAllFiles should catch the rest of them later
        }

        enum ExecuteResult
        {
            CLEAN, INFECTED, ERROR
        }

        private static ExecuteResult TryExecute(Func<bool> func, int timeout)
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

        private static void initAllFilesTimer()
        {
            timer = new System.Timers.Timer();
            timer.Elapsed += new ElapsedEventHandler(timer_Tick);
            timer.Interval = addAllFilesInterval;
            timer.Start();
        }

        private static void timer_Tick(object sender, EventArgs e)
        {
            Console.WriteLine("Automatically adding all files");
            addAllFiles(watchDir);
            startTransfers();
        }

        private static void addAllFiles(String dir)
        {
            Console.WriteLine("Adding all files!");
            String[] filesInDir = Directory.GetFiles(dir);
            foreach (String file in filesInDir)
            {
                addToQueue(file);
            }
        }

        private static void FileCreated(object e)
        {
            string fullFilePath = (string)e;
            string dir = Path.GetDirectoryName(fullFilePath) + @"\";
            string file = Path.GetFileName(fullFilePath);

            Console.WriteLine(String.Format("{0,-3}", filesInTransfer.Count) + "/" + String.Format("{0,5}", filesInQueue.Count) + " Scanning file: " + file);

            if (!File.Exists(fullFilePath) || IsFileLocked(fullFilePath))
            {
                Console.WriteLine("File is locked or doesn't exist!\nIt will be retried within " + addAllFilesInterval / 1000 / 60 + " minutes.");
            }
            else
            {
                ExecuteResult fileStatus;
                using (ICAPNameSpace.ICAP icap = new ICAPNameSpace.ICAP("192.168.1.5", 1344, "avscan", 1024))
                {
                    fileStatus = TryExecute(() => icap.scanFile(fullFilePath), transferTimeout);

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
                        Console.WriteLine("Transfer timedout or failed for: " + file);
                    }
                }
            }

            filesInTransfer.Remove(fullFilePath);
            startTransfers();
            return;
        }

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