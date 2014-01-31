using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace ICAPNameSpace
{
    /// <summary>
    /// Basic Exception to use in relation with ICAP
    /// </summary>
    public class ICAPException: Exception
    {
        public ICAPException(string message)
            : base(message)
        {
        }

    }
}
