using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Diagnostics;
using System.Linq;
using System.ServiceProcess;
using System.Text;
using System.Threading.Tasks;
using System.Threading;
using System.IO;
using System.Timers;
using System.Configuration;
using System.Collections.Specialized;


namespace FolderWatchService
{
    public partial class FolderWatchService : ServiceBase
    {
        /// <summary>
        /// If run in a command prompt, it will show the errors, warnings and so on through stdout.
        /// If run as a Windows service, it will show the errors, warnings and so on in the system event log.
        /// </summary>
        /// <param name="args">Not used</param>
        static void Main(string[] args)
        {
            FolderWatchService service = new FolderWatchService();

            if (Environment.UserInteractive)
            {
                service.OnStart(args);
                Console.WriteLine("Press any key to stop program");
                Console.Read();
                service.OnStop();
            }
            else
            {
                ServiceBase.Run(service);
            }
        }

        /// <summary>
        /// Initiates the eventlog for logging
        /// </summary>
        public FolderWatchService()
        {
            InitializeComponent();

            ServiceName = "FolderWatchService";
            EventLog.Source = ServiceName;
            EventLog.Log = "Application";
        }

        /// <summary>
        /// Adding a small entry in the log about starting and starts the FolderWatch main method (Not the service main method).
        /// </summary>
        /// <param name="args"></param>
        protected override void OnStart(string[] args)
        {
            EventLog.WriteEntry(ServiceName + " starting", EventLogEntryType.Information);
            FolderWatchNameSpace.Program.eventLog = EventLog;
            FolderWatchNameSpace.Program.Main(args);
        }

        /// <summary>
        /// Adding a small entry in the log about closing.
        /// </summary>
        protected override void OnStop()
        {
            EventLog.WriteEntry(ServiceName + " stopping", EventLogEntryType.Information);
        }


    }
}
