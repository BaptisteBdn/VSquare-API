/*================================================================================
Copyright (c) 2008 VMware, Inc. All Rights Reserved.
 
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
 
 * Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.
 
 * Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.
 
 * Neither the name of VMware, Inc. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior
written permission.
 
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL VMWARE, INC. OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
================================================================================*/
package fr.eseo.vsquare.utils;

import com.vmware.vim25.mo.HttpNfcLease;

import java.rmi.RemoteException;
 
/**
 * http://vijava.sf.net
 * @author Steve Jin (sjin at vmware.com)
 */

class LeaseProgressUpdater extends Thread
{
    private HttpNfcLease httpNfcLease = null;
    private int progressPercent = 0;
    private int updateInterval;
 
    public LeaseProgressUpdater(HttpNfcLease httpNfcLease, int updateInterval)
    {
        this.httpNfcLease = httpNfcLease;
        this.updateInterval = updateInterval;
    }
 
    @Override
    public void run()
    {
        while (true)
        {
            try
            {
                httpNfcLease.httpNfcLeaseProgress(progressPercent);
                Thread.sleep(updateInterval);
            }
            catch(InterruptedException ie)
            {
            	Thread.currentThread().interrupt();
                break;
            }
            catch(RemoteException e)
            {
                throw new RuntimeException2(e);
            }
        }
    }
 
    public void setPercent(int percent)
    {
        this.progressPercent = percent;
    }
    
    private class RuntimeException2 extends RuntimeException
    {
		private static final long serialVersionUID = 1L;
		public RuntimeException2(RemoteException s){
			super(s);
		}
    	
    }
}
