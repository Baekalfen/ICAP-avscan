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

        public FolderWatchService()
        {
            InitializeComponent();
            //this.EventLog = new System.Diagnostics.EventLog();

            ServiceName = "FolderWatchService";
            //EventLog = new System.Diagnostics.EventLog();
            EventLog.Source = ServiceName;
            EventLog.Log = "Application";
        }

        protected override void OnStart(string[] args)
        {
            EventLog.WriteEntry(ServiceName + " starting", EventLogEntryType.Information);
            FolderWatchNameSpace.Program.eventLog = EventLog;
            FolderWatchNameSpace.Program.Main(args);
        }

        protected override void OnStop()
        {
            EventLog.WriteEntry(ServiceName + " stopping", EventLogEntryType.Information);
        }


    }
}
