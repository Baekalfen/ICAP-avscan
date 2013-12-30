using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace ICAPNameSpace
{
    public class ICAPException: Exception
    {
        public ICAPException(string message)
            : base(message)
        {
        }

    }
}
