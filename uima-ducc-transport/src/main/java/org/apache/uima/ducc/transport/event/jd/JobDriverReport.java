/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package org.apache.uima.ducc.transport.event.jd;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.uima.ducc.common.jd.files.workitem.RemoteLocation;
import org.apache.uima.ducc.common.utils.id.DuccId;
import org.apache.uima.ducc.container.common.Util;
import org.apache.uima.ducc.container.common.logger.IComponent;
import org.apache.uima.ducc.container.common.logger.Logger;
import org.apache.uima.ducc.container.jd.mh.iface.IOperatingInfo;
import org.apache.uima.ducc.container.jd.mh.iface.IProcessInfo;
import org.apache.uima.ducc.container.jd.mh.iface.IWorkItemInfo;
import org.apache.uima.ducc.container.net.iface.IMetaCasTransaction.JdState;
import org.apache.uima.ducc.transport.event.common.DuccPerWorkItemStatistics;
import org.apache.uima.ducc.transport.event.common.DuccProcessWorkItems;
import org.apache.uima.ducc.transport.event.common.IDuccCompletionType.JobCompletionType;
import org.apache.uima.ducc.transport.event.common.IDuccPerWorkItemStatistics;
import org.apache.uima.ducc.transport.event.common.IDuccProcess;
import org.apache.uima.ducc.transport.event.common.IDuccProcessMap;
import org.apache.uima.ducc.transport.event.common.IDuccProcessWorkItems;
import org.apache.uima.ducc.transport.event.common.IRationale;
import org.apache.uima.ducc.transport.event.jd.IDriverState.DriverState;

public class JobDriverReport implements Serializable, IDriverStatusReport {

	private static Logger logger = Logger.getLogger(JobDriverReport.class, IComponent.Id.JD.name());
	
	private static final long serialVersionUID = 200L;

	private DuccId duccId = null;
	private String node = null;
	private int port = 0;
	private String jdState = null;
	private String jmxUrl = null;
	
	private long workItemsTotal = 0;
	private int workItemsProcessingCompleted = 0;
	private int workItemsProcessingError = 0;
	private int workItemsRetry = 0;
	private int workItemsDispatched = 0;
	private int workItemsPreempted = 0;
	
	private long wiMillisMin = 0;
	private long wiMillisMax = 0;
	private long wiMillisAvg = 0;
	private long wiMillisOperatingLeast = 0;
	private long wiMillisCompletedMost = 0;
	
	private long wiTodMostRecentStart = 0;
	
	private boolean wiPending = true;
	private boolean wiPendingProcessAssignment = false;
	
	private boolean killJob = false;
	
	private ArrayList<IWorkItemInfo> listActiveWorkItemInfo = null;
	
	private ConcurrentHashMap<RemoteLocation, Long> mapProcessOperatingMillis = null;
	
	private String jpAe = null;
	
	private JobCompletionType jobCompletionType = JobCompletionType.EndOfJob;
	
	private IDuccPerWorkItemStatistics duccPerWorkItemStatistics = null;
	
	private DuccProcessWorkItemsMap duccProcessWorkItemsMap = null;
	
	private long max(long a, long b) {
		long retVal = a;
		if(b > a) {
			retVal = b;
		}
		return retVal;
	}
	
	private long min(long a, long b) {
		long retVal = a;
		if(b < a) {
			retVal = b;
		}
		return retVal;
	}
	
	private DuccId getDuccId(IOperatingInfo operatingInfo) {
		DuccId retVal = null;
		try {
			String jobId = operatingInfo.getJobId();
			long value = Long.parseLong(jobId);
			retVal = new DuccId(value);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return retVal;
	}
	
	public JobDriverReport(IOperatingInfo operatingInfo, IDuccProcessMap dpMap) {
		String location = "JobDriverReport";
		setDuccId(getDuccId(operatingInfo));
		setJdState(operatingInfo.getJdState());
		//setJmxUrl(driverContainer.getJmxUrl());
		setWorkItemsTotal(operatingInfo.getWorkItemCrTotal());
		setWorkItemsProcessingCompleted(operatingInfo.getWorkItemEndSuccesses());
		setWorkItemsProcessingError(operatingInfo.getWorkItemEndFailures());
		setWorkItemsRetry(operatingInfo.getWorkItemUserProcessingErrorRetries());
		setWorkItemsDispatched(operatingInfo.getWorkItemDispatcheds());
		// min of finished & running
		long fMin = operatingInfo.getWorkItemFinishedMillisMin();
		long min = fMin;
		long rMin = operatingInfo.getWorkItemRunningMillisMin();
		if(rMin > 0) {
			min = min(fMin, rMin);
		}
		setWiMillisMin(min);
		// max of finished & running
		long fMax = operatingInfo.getWorkItemFinishedMillisMax();
		long max = fMax;
		long rMax = operatingInfo.getWorkItemRunningMillisMax();
		if(rMax > 0) {
			max = max(fMax, rMax);
		}
		setWiMillisMax(max);
		// avg of finished
		long avg = operatingInfo.getWorkItemFinishedMillisAvg();
		setWiMillisAvg(avg);
		// min of running
		setWiMillisOperatingLeast(rMin);
		// max of finished
		setWiMillisCompletedMost(fMax);
		// most recent start TOD
		setMostRecentStart(operatingInfo.getWorkItemTodMostRecentStart());
		// pending means CR fetches < crTotal
		setWiPending(operatingInfo.isWorkItemCrPending());
		// kill job?
		if(operatingInfo.isKillJob()) {
			setKillJob();
		}
		// operating map
		setActiveWorkItemInfo(operatingInfo.getActiveWorkItemInfo());
		// JpAe
		setJpAe(operatingInfo.getJpAe());
		// per work statistics
		DuccPerWorkItemStatistics perWorkItemStatistics = new DuccPerWorkItemStatistics(
			operatingInfo.getWorkItemFinishedMillisMax(),
			operatingInfo.getWorkItemFinishedMillisMin(),
			operatingInfo.getWorkItemFinishedMillisAvg(),
			operatingInfo.getWorkItemFinishedMillisStdDev()
			);
		setPerWorkItemStatistics(perWorkItemStatistics);
		// per process statistics
		ArrayList<IProcessInfo> list = operatingInfo.getProcessItemInfo();
		if(list != null) {
			if(!list.isEmpty()) {
				duccProcessWorkItemsMap = new DuccProcessWorkItemsMap();
				for(IProcessInfo pi : list) {
					String ip = pi.getNodeAddress();
					int pid = pi.getPid();
					IDuccProcess dp = dpMap.findProcess(ip, ""+pid);
					if(dp != null) {
						DuccId key = dp.getDuccId();
						IDuccProcessWorkItems value = new DuccProcessWorkItems(pi);
						duccProcessWorkItemsMap.put(key, value);
					}
					else {
						logger.debug(location, null, "process not found: "+"ip="+ip+" "+"pid="+pid);
						int i = 0;
						for(Entry<DuccId, IDuccProcess> entry : dpMap.entrySet()) {
							IDuccProcess value = entry.getValue();
							logger.debug(location, null, "process["+i+"]: "+"ip="+value.getNodeIdentity().getIp()+" "+"pid="+value.getPID());
							i++;
						}
					}
				}
			}
			else {
				logger.debug(location, null, "list is empty");
			}
		}
		else {
			logger.debug(location, null, "list is null");
		}
	}
	
	private void setDuccId(DuccId value) {
		duccId = value;
	}
	
	public void setNode(String value) {
		node = value;
	}
	
	public void setPort(int value) {
		port = value;
	}
	
	public void setJdState(String value) {
		jdState = value;
	}
	
	private void setJmxUrl(String value) {
		jmxUrl = value;
	}
	
	private void setWorkItemsTotal(long value) {
		workItemsTotal = value;
	}
	
	private void setWorkItemsProcessingCompleted(int value) {
		workItemsProcessingCompleted = value;
	}
	
	private void setWorkItemsProcessingError(int value) {
		workItemsProcessingError = value;
	}
	
	private void setWorkItemsRetry(int value) {
		workItemsRetry = value;
	}
	
	private void setWorkItemsDispatched(int value) {
		workItemsDispatched = value;
	}
	
	private void setWiMillisMin(long value) {
		wiMillisMin = value;
	}
	
	private void setWiMillisMax(long value) {
		wiMillisMax = value;
	}
	
	private void setWiMillisAvg(long value) {
		wiMillisAvg = value;
	}
	
	private void setWiMillisOperatingLeast(long value) {
		wiMillisOperatingLeast = value;
	}
	
	private void setWiMillisCompletedMost(long value) {
		wiMillisCompletedMost = value;
	}
	
	private void setMostRecentStart(long value) {
		wiTodMostRecentStart = value;
	}
	
	private void setWiPending(boolean value) {
		wiPending = value;
	}
	
	private void setKillJob() {
		killJob = true;
	}
	
	private void setActiveWorkItemInfo(ArrayList<IWorkItemInfo> value) {
		listActiveWorkItemInfo = value;
	}
	
	private void setJpAe(String value) {
		jpAe = value;
	}
	
	private void setPerWorkItemStatistics(IDuccPerWorkItemStatistics value) {
		duccPerWorkItemStatistics = value;
	}
	
	@Override
	public long getVersion() {
		return serialVersionUID;
	}
	
	@Override
	public DuccId getDuccId() {
		return duccId;
	}

	@Override
	public String getNode() {
		return node;
	}
	
	@Override
	public int getPort() {
		return port;
	}

	@Override
	public String getJdState() {
		return jdState;
	}
	
	@Override
	public String getLogReport() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getWorkItemsTotal() {
		return workItemsTotal;
	}

	@Override
	public int getWorkItemsProcessingCompleted() {
		return workItemsProcessingCompleted;
	}

	@Override
	public int getWorkItemsProcessingError() {
		return workItemsProcessingError;
	}

	@Override
	public int getWorkItemsRetry() {
		return workItemsRetry;
	}

	@Override
	public int getWorkItemsDispatched() {
		return workItemsDispatched;
	}

	@Override
	public int getWorkItemsPreempted() {
		return workItemsPreempted;
	}

	@Override
	public int getWorkItemsLost() {
		return 0;
	}

	@Override
	public int getWorkItemPendingProcessAssignmentCount() {
		return 0;
	}

	@Override
	public long getWiMillisMin() {
		return wiMillisMin;
	}

	@Override
	public long getWiMillisMax() {
		return wiMillisMax;
	}

	@Override
	public long getWiMillisAvg() {
		return wiMillisAvg;
	}

	@Override
	public long getWiMillisOperatingLeast() {
		return wiMillisOperatingLeast;
	}

	@Override
	public long getWiMillisCompletedMost() {
		return wiMillisCompletedMost;
	}

	@Override
	public long getMostRecentStart() {
		return wiTodMostRecentStart;
	}

	@Override
	public boolean isPending() {
		return wiPending;
	}

	@Override
	public boolean isWorkItemPendingProcessAssignment() {
		return wiPendingProcessAssignment;
	}

	@Override
	public boolean isKillJob() {
		return killJob;
	}

	@Override
	public boolean isOperating(String nodeIP, String PID) {
		boolean retVal = false;
		if(listActiveWorkItemInfo != null) {
			for(IWorkItemInfo wii : listActiveWorkItemInfo) {
				if(Util.compare(wii.getNodeAddress(), nodeIP)) {
					if(Util.compare(""+wii.getPid(), PID)) {
						retVal = true;
						break;
					}
				}
			}
		}
		return retVal;
	}

	@Override
	public String getJdJmxUrl() {
		return jmxUrl;
	}

	@Override
	public String getUimaDeploymentDescriptor() {
		return null;
	}

	@Override
	public String getUimaAnalysisEngine() {
		return jpAe;
	}
	
	@Override
	public Iterator<DuccId> getKillDuccIds() {
		// TODO Auto-generated method stub
		return null;
	}

	@Deprecated
	@Override
	public DriverState getDriverState() {
		DriverState retVal = DriverState.Undefined;
		String state = getJdState();
		if(state != null) {
			if(state.equals(JdState.Initializing.name())) {
				retVal = DriverState.Initializing;
			}
			else if(state.equals(JdState.Active.name())) {
				retVal = DriverState.Running;
			}
			else if(state.equals(JdState.Ended.name())) {
				retVal = DriverState.Completed;
			}
		}
		return retVal;
	}

	@Override
	public JobCompletionType getJobCompletionType() {
		return jobCompletionType;
	}

	@Override
	public IRationale getJobCompletionRationale() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IDuccPerWorkItemStatistics getPerWorkItemStatistics() {
		return duccPerWorkItemStatistics;
	}

	@Override
	public DuccProcessWorkItemsMap getDuccProcessWorkItemsMap() {
		return duccProcessWorkItemsMap;
	}

	@Override
	public ConcurrentHashMap<RemoteLocation, Long> getOperatingMillisMap() {
		if(mapProcessOperatingMillis == null) {
			mapProcessOperatingMillis = new ConcurrentHashMap<RemoteLocation, Long>();
			if(listActiveWorkItemInfo != null) {
				for(IWorkItemInfo wii: listActiveWorkItemInfo) {
					String nodeIP = wii.getNodeAddress();
					String PID = ""+wii.getPid();
					RemoteLocation rl = new RemoteLocation(nodeIP, PID);
					if(!mapProcessOperatingMillis.containsKey(rl)) {
						mapProcessOperatingMillis.put(rl, new Long(0));
					}
					long millis = wii.getOperatingMillis() + mapProcessOperatingMillis.get(rl);
					mapProcessOperatingMillis.put(rl, new Long(millis));
				}
			}
		}
		return mapProcessOperatingMillis;
	}

	@Override
	public ConcurrentHashMap<Integer, DuccId> getLimboMap() {
		ConcurrentHashMap<Integer, DuccId> map = new ConcurrentHashMap<Integer, DuccId>();
		return map;
	}

	@Override
	public ConcurrentHashMap<String, DuccId> getCasQueuedMap() {
		ConcurrentHashMap<String, DuccId> map = new ConcurrentHashMap<String, DuccId>();
		return map;
	}

}
