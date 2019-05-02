
package fr.eseo.vsquare.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.logging.Level;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

import com.vmware.vim25.DVPortgroupConfigSpec;
import com.vmware.vim25.DistributedVirtualPortgroupPortgroupType;
import com.vmware.vim25.HttpNfcLeaseDeviceUrl;
import com.vmware.vim25.HttpNfcLeaseInfo;
import com.vmware.vim25.HttpNfcLeaseState;
import com.vmware.vim25.ImportSpec;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.NotAuthenticated;
import com.vmware.vim25.OvfCreateDescriptorParams;
import com.vmware.vim25.OvfCreateDescriptorResult;
import com.vmware.vim25.OvfCreateImportSpecParams;
import com.vmware.vim25.OvfCreateImportSpecResult;
import com.vmware.vim25.OvfFile;
import com.vmware.vim25.OvfFileItem;
import com.vmware.vim25.OvfNetworkMapping;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VirtualCdrom;
import com.vmware.vim25.VirtualCdromIsoBackingInfo;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecFileOperation;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualDeviceConnectInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualIDEController;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VirtualMachineConfigOption;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
import com.vmware.vim25.VirtualMachineSnapshotInfo;
import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.vim25.VirtualMachineTicket;
import com.vmware.vim25.mo.ComputeResource;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.DistributedVirtualPortgroup;
import com.vmware.vim25.mo.DistributedVirtualSwitch;
import com.vmware.vim25.mo.EnvironmentBrowser;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.HttpNfcLease;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VirtualMachineSnapshot;
import com.vmware.vim25.mo.util.MorUtil;
import com.vmware.vim25.mox.VirtualMachineDeviceManager;
import com.vmware.vim25.ws.WSClient;

import fr.eseo.vsquare.model.Snapshot;
import fr.eseo.vsquare.model.Vm;

/**
 * This class provides wrapper function to use the ViJava SDK of vmware,
 * implementing functions the REST API cannot do to this day.
 * <p>
 * All public methods should use checkConnection to avoid issues
 *
 * @author Kalioz
 */
public final class VCenterManager {

	private static ServiceInstance service;
	private static Folder rootFolder;

	private static final String VIRTUAL_MACHINE = "VirtualMachine";
	private static final String HOST_SYSTEM = "HostSystem";
	private static final String VSPHERE_HOST = Utils.getString("vsphere_host");
	
	
	private VCenterManager() {
	}

	/**
	 * open the SDK connection
	 * @return true if the connection is successful
	 */
	public static boolean init() {
		try {
			service = new ServiceInstance(
					new URL("https://" + VSPHERE_HOST + "/sdk"),
					Utils.getString("vsphere_user"),
					Utils.getString("vsphere_pass"),
					true);
			WSClient wsc = service.getServerConnection().getVimService().getWsc();
			wsc.setConnectTimeout(0);
			wsc.setReadTimeout(0);
			rootFolder = service.getRootFolder();
			Logger.log(Level.INFO, "Connection to VCenter SDK successful");
		} catch (RemoteException | MalformedURLException e) {
			Logger.log(Level.SEVERE, "Connection to VCenter SDK failed : {0}", e.toString());
			return false;
		}
		return true;
	}

	/**
	 * close the SDK connection
	 */
	public static void exit() {
		if (service != null) {
			service.getServerConnection().logout();
			service = null;
		}
	}

	/**
	 * reconnect the SDK
	 * @return
	 */
	private static boolean reconnect() {
		exit();
		return init();
	}

	/**
	 * verify if the SDK connection hasn't timed out
	 */
	public static void checkConnection() {
		boolean reconnect = true;
		try {
			reconnect = getDatacenter() == null;
		} catch (Exception e) {
			//ignore
		}
		if (reconnect) {
			Logger.log(Level.INFO, "Connection to VCenter SDK lost, reconnecting...");
			reconnect();
		}

	}

	/**
	 * Return the VM as stored by the SDK; returns null if no VM is found.
	 *
	 * @param vm the VM object to find
	 * @return the VirtualMachine object
	 */
	static VirtualMachine getVmVCenter(Vm vm) {
		if (vm == null || vm.getIdVmVcenter() == null)
			return null;
		ManagedObjectReference mor = new ManagedObjectReference();
		mor.setType(VIRTUAL_MACHINE);
		mor.set_value(vm.getIdVmVcenter());
		VirtualMachine output = new VirtualMachine(service.getServerConnection(), mor);
		try {
			output.getGuest();
		} catch (RuntimeException e) {// yeah, that's the best way to guess if a VM exists or not.
			output = null;
		}
		return output;
	}

	/**
	 * Return the VM as stored by the SDK by its name. Should only be used when
	 * creating a VM.
	 *
	 * @param name the name of the VM
	 * @return the VirtualMachine object
	 */
	static VirtualMachine getVmVCenter(String name) {

		Folder rootFolder = service.getRootFolder();

		try {
			return (VirtualMachine) new InventoryNavigator(rootFolder).searchManagedEntity(VIRTUAL_MACHINE, name);
		} catch (RemoteException e) {
			return null;
		}
	}

	/**
	 * Return the mks ticket to connect a remote console to the VM.
	 *
	 * @param vm the vm to connect to
	 * @return the ticket value or null on error
	 */
	public static String getRemoteConsoleUrl(Vm vm) {
		checkConnection();
		VirtualMachine vmCenter = getVmVCenter(vm);
		if (vmCenter == null)
			return null;
		try {
			VirtualMachineTicket ticket = acquireTicket(vmCenter, true);
			return ticket == null ? null : String.format("%s:%s", ticket.host, ticket.ticket);
		} catch (RemoteException e) {
			Logger.log(Level.WARNING, "Error when getting remote console ticket : {0}", e.toString());
			return null;
		}
	}

	/**
	 * Return the ticket to open a remote console to a virtualMachine.
	 *
	 * @param vmCenter the vmCenter object in use
	 * @param retry retry once on NotAuthenticated error
	 * @return the VirtualMachineTicket
	 * @throws RemoteException on acquisition error
	 */
	private static VirtualMachineTicket acquireTicket(VirtualMachine vmCenter, boolean retry) throws RemoteException {
		try {
			return vmCenter.acquireTicket("webmks");
		} catch (NotAuthenticated e) {
			if (!retry)
				throw e;
			init();
			Logger.log(Level.INFO, "Re-authenticated");
			try {
				Thread.sleep(500);
			} catch (Exception ignored) {
				Logger.log(Level.WARNING, "Cannot wait for session to be Authenticated");
			}
			return acquireTicket(vmCenter, false);
		}
	}

	/**
	 * Return all virtual machines on VCenter.
	 *
	 * @return all virtual machines
	 */
	static VirtualMachine[] getVmVcenterList() {
		ManagedEntity[] vms = {};
		try {
			vms = new InventoryNavigator(rootFolder).searchManagedEntities(VIRTUAL_MACHINE);
		} catch (RemoteException e) {
			Logger.log(Level.WARNING, "VCenterSDK could not list VMs : {0}", e.toString());
		}
		return Arrays.copyOf(vms, vms.length, VirtualMachine[].class);
	}

	/**
	 * Return the list of snapshots associated with the VM.
	 *
	 * @param vm the VM to return snapshot
	 * @return the list of snaphsots from the VM
	 */
	static VirtualMachineSnapshotTree[] getSnapshotList(Vm vm) {
		VirtualMachine vmVcenter;
		VirtualMachineSnapshotInfo snapInfo;
		if ((vmVcenter = getVmVCenter(vm)) == null || (snapInfo = vmVcenter.getSnapshot()) == null)
			return new VirtualMachineSnapshotTree[0];
		return snapInfo.getRootSnapshotList();
	}

	/**
	 * Return the list of snapshot asssociated with a VM as a JSONArray object.
	 *
	 * @param vm the vm to search snapshot from
	 * @return the list of snapshot
	 */
	static JSONArray getSnapshotListJSON(Vm vm) {
		return snapshotsToJSON(getSnapshotList(vm));
	}

	/**
	 * Return the JSONArray converted from the VM snapshots tree.
	 * <p>
	 * In order to display more efficiently the snapshots we need to flatten the tree
	 * by putting the snapshots in a JSONArray.
	 *
	 * @param trees      the trees representing the VM snapshots
	 * @param mainOutput the JSONArray to write in
	 * @return a JSONArray containing the snaphosts
	 */
	private static JSONArray snapshotsToJSON(VirtualMachineSnapshotTree[] trees, JSONArray mainOutput) {
		JSONArray output = new JSONArray();
		if (trees == null)
			return null;
		for (VirtualMachineSnapshotTree tree : trees) {
			output.put(snapshotToJSON(tree, mainOutput));
		}
		return output;
	}

	/**
	 * Return the snapshot converted into a JSONObject.
	 *
	 * @param tree       the tree containing the snapshots
	 * @param mainOutput the JSONArray for the response
	 * @return the JSONObject containing the converted snapshot
	 */
	static JSONObject snapshotToJSON(VirtualMachineSnapshotTree tree, JSONArray mainOutput) {
		if (tree == null)
			return null;
		JSONObject output = new JSONObject();
		output.put("id", tree.getId());
		output.put("name", tree.getName());
		output.put("description", tree.getDescription());

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		output.put("creationDate", sdf.format(tree.getCreateTime().getTime()));
		output.put("children", snapshotsToJSON(tree.getChildSnapshotList(), mainOutput));

		mainOutput.put(output);

		return output;
	}

	protected static JSONObject snapshotToJSON(VirtualMachineSnapshotTree tree) {
		JSONArray output = new JSONArray();
		return snapshotToJSON(tree, output);
	}

	private static JSONArray snapshotsToJSON(VirtualMachineSnapshotTree[] trees) {
		JSONArray output = new JSONArray();
		return snapshotsToJSON(trees, output);
	}

	/**
	 * Creates a snapshot of the VM.
	 *
	 * @param vm          the VM to create a snapshot from
	 * @param name        the name of the snapshot
	 * @param description the description of the snapshot
	 * @return the id of the created snapshot
	 */
	public static String createSnapshot(Vm vm, String name, String description) {
		checkConnection();
		return createSnapshot(vm, name, description, false);
	}

	/**
	 * Creates a snapshot of the VM.
	 *
	 * @param vm              the VM to create a snapshot from
	 * @param name            the name of the snapshot
	 * @param description     the description of the snapshot
	 * @param saveMemoryState true to save the state of the memory in the snapshot, false otherwise
	 * @return the id of the created snapshot
	 */
	private static String createSnapshot(Vm vm, String name, String description, Boolean saveMemoryState) {
		VirtualMachine vmVcenter = getVmVCenter(vm);
		if (vmVcenter == null)
			return null;
		try {
			Task task = vmVcenter.createSnapshot_Task(name, description, saveMemoryState, false);
			boolean output = task != null && task.waitForTask().equals(Task.SUCCESS);
			if (output)
				return ((ManagedObjectReference) task.getTaskInfo().getResult()).get_value();
		} catch (NotAuthenticated e) {
			reconnect();
		} catch (RemoteException | InterruptedException e) {
			Logger.log(Level.WARNING, "Error in creating snapshot : {0}", e.toString());
		}
		return null;
	}

	/**
	 * Return the snapshot from the VM.
	 *
	 * @param vm         the VM
	 * @param idSnapshot the Id of the snapshot
	 * @return the VM snapshot
	 */
	private static VirtualMachineSnapshot getSnapshotFromTree(Vm vm, int idSnapshot) {
		return searchSnapshot(getSnapshotList(vm), idSnapshot);
	}

	/**
	 * Return the snapshot from the VM (object from vijava).
	 *
	 * @param snapshot the snapshot from vsquare
	 * @return the snapshot of the VM
	 */
	private static VirtualMachineSnapshot getSnapshotFromTree(Snapshot snapshot) {
		return searchSnapshot(getSnapshotList(snapshot.getVm()), snapshot.getIdSnapshotVcenter());
	}


	/**
	 * Return the snapshot with the good id using recursion.
	 *
	 * @param trees      the trees to look in
	 * @param idSnapshot the Id of the snapshot
	 * @return the VM snapshot
	 */
	private static VirtualMachineSnapshot searchSnapshot(VirtualMachineSnapshotTree[] trees, int idSnapshot) {
		VirtualMachineSnapshotTree snapshot = searchSnapshotTree(trees, idSnapshot);
		return (snapshot == null) ? null
				: new VirtualMachineSnapshot(service.getServerConnection(), snapshot.getSnapshot());
	}

	/**
	 * Return the snapshot with the good MOR value using recursion.
	 *
	 * @param trees the snapshot tree
	 * @param idSnapshotMORVcenter the MOR value (snapshot-XYZ)
	 * @return the snapshot object
	 */
	private static VirtualMachineSnapshot searchSnapshot(VirtualMachineSnapshotTree[] trees, String idSnapshotMORVcenter) {
		VirtualMachineSnapshotTree snapshot = searchSnapshotTree(trees, idSnapshotMORVcenter);
		return (snapshot == null) ? null
				: new VirtualMachineSnapshot(service.getServerConnection(), snapshot.getSnapshot());
	}

	static VirtualMachineSnapshotTree searchSnapshotTree(Vm vm, int idSnapshot) {
		return searchSnapshotTree(getSnapshotList(vm), idSnapshot);
	}

	private static VirtualMachineSnapshotTree searchSnapshotTree(VirtualMachineSnapshotTree[] trees, int idSnapshot) {
		for (VirtualMachineSnapshotTree snapshot : trees == null ? new VirtualMachineSnapshotTree[0] : trees) {
			if (snapshot.getId() == idSnapshot) {
				return snapshot;
			} else {
				VirtualMachineSnapshotTree snapsTemp = searchSnapshotTree(snapshot.getChildSnapshotList(), idSnapshot);
				if (snapsTemp != null)
					return snapsTemp;
			}
		}
		return null;
	}

	private static VirtualMachineSnapshotTree searchSnapshotTree(VirtualMachineSnapshotTree[] trees,
			String idSnapshotMORVcenter) {
		for (VirtualMachineSnapshotTree snapshot : trees == null ? new VirtualMachineSnapshotTree[0] : trees) {
			if (snapshot.getSnapshot().get_value().equals(idSnapshotMORVcenter)) {
				return snapshot;
			} else {
				VirtualMachineSnapshotTree snapsTemp = searchSnapshotTree(snapshot.getChildSnapshotList(), idSnapshotMORVcenter);
				if (snapsTemp != null)
					return snapsTemp;
			}
		}
		return null;
	}

	/**
	 * Revert a VM to the snapshot state, return true if success.
	 *
	 * @param vm         the VM to revert the state of
	 * @param idSnapshot the snapshot Id to revert to
	 * @return true if its a sucess, false otherwise
	 */
	public static boolean revertSnapshot(Vm vm, int idSnapshot) {
		checkConnection();
		VirtualMachineSnapshot snapshot = getSnapshotFromTree(vm, idSnapshot);
		return snapshot != null && revertSnapshot(snapshot);
	}

	/**
	 * Revert a VM to the snapshot state, return true if success.
	 *
	 * @param snapshot the vsphere snapshot object
	 * @return true if its a sucess, false otherwise
	 */
	public static boolean revertSnapshot(Snapshot snapshot) {
		checkConnection();
		VirtualMachineSnapshot vmsnapshot = getSnapshotFromTree(snapshot);
		return vmsnapshot != null && revertSnapshot(vmsnapshot);
	}

	private static boolean revertSnapshot(VirtualMachineSnapshot snapshot) {
		try {
			Task task = snapshot.revertToSnapshot_Task(null, true);
			return task.waitForTask().equals(Task.SUCCESS);
		} catch (RemoteException | InterruptedException e) {
			Logger.log(Level.WARNING, "Revert to snapshot not successful : {0}", e.toString());
			return false;
		}
	}

	/**
	 * Edit the snapshot settings.
	 * <p>
	 * If the field name or description are null, then the original
	 * value is set.
	 *
	 * @param vm          the VM at the origin of the snapshot
	 * @param idSnapshot  the Id of the snapshot to edit
	 * @param name        the new name of the snapshot
	 * @param description the new description of the snapshot
	 */
	public static void editSnapshot(Vm vm, int idSnapshot, String name, String description) {
		checkConnection();
		VirtualMachineSnapshotTree snapshotTree = searchSnapshotTree(vm, idSnapshot);
		VirtualMachineSnapshot snapshot = getSnapshotFromTree(vm, idSnapshot);// coz doing it via the class
		if (snapshot == null || snapshotTree == null)
			return;

		editSnapshot(snapshot, Utils.coalesce(name, snapshotTree.getName(), "no name"),
				Utils.coalesce(description, snapshotTree.getDescription(), "no description"));
	}

	private static void editSnapshot(VirtualMachineSnapshot snapshot, String name, String description) {
		try {
			snapshot.renameSnapshot(name, description);
		} catch (RemoteException e) {
			Logger.log(Level.WARNING, "edit snapshot not successful : {0}", e.toString());
		}
	}

	/**
	 * Delete the snapshot associated with the vm.
	 *
	 * @param vm           the VM at the origin of the snapshot
	 * @param idSnapshot   the Id of the snapshot to delete
	 * @param withChildren boolean, true to delete the snapshot children
	 * @return true if deleted, false otherwise
	 */
	public static boolean deleteSnapshot(Vm vm, int idSnapshot, boolean withChildren) {
		checkConnection();
		VirtualMachineSnapshot snapshot = getSnapshotFromTree(vm, idSnapshot);
		return snapshot != null && deleteSnapshot(snapshot, withChildren);
	}

	/**
	 * delete a snapshot
	 * @param snapshot
	 * @return
	 */
	public static boolean deleteSnapshot(Snapshot snapshot) {
		checkConnection();
		VirtualMachineSnapshot vmsnapshot = getSnapshotFromTree(snapshot);
		return vmsnapshot != null && deleteSnapshot(vmsnapshot, false);
	}

	/**
	 * delete a snapshot
	 * @param snapshot
	 * @param withChildren set true to delete all children snapshots of this one
	 * @return
	 */
	private static boolean deleteSnapshot(VirtualMachineSnapshot snapshot, boolean withChildren) {
		try {
			Task task = snapshot.removeSnapshot_Task(withChildren);
			return task.waitForTask().equals(Task.SUCCESS);
		} catch (RemoteException | InterruptedException e) {
			Logger.log(Level.WARNING, "Revert to snapshot not successful : {0}", e.toString());
			return false;
		}
	}


	/**
	 * Return the OVF file contained in the OVA file.
	 *
	 * @param ova the OVA file to seach in
	 * @return the OVF file
	 * @throws IOException on reading error
	 */
	static String findOVFinOVA(File ova) throws IOException {
		String ovf = null;

		try (TarArchiveInputStream myTarFile = new TarArchiveInputStream(new FileInputStream(ova))) {
			TarArchiveEntry entry = null;
			while ((entry = myTarFile.getNextTarEntry()) != null) {
				if ("ovf".equals(Utils.getExtension(entry.getName()))) {
					ovf = entry.getName();
					break;
				}
			}
		}
		return ovf;
	}

	/**
	 * Return the searched file from the OVA file.
	 *
	 * @param ova  the OVA file to search in
	 * @param name the name of the file to search
	 * @return the file searched
	 * @throws IOException on reading error
	 */
	private static String findFileinOVAAsText(File ova, String name) throws IOException {
		try (TarArchiveInputStream myTarFile = new TarArchiveInputStream(new FileInputStream(ova))) {
			TarArchiveEntry entry = null;
			while ((entry = myTarFile.getNextTarEntry()) != null) {
				if (name.equals(entry.getName())) {
					return readContent(new BufferedReader(new InputStreamReader(myTarFile)));
				}
			}
		}
		return null;
	}

	static HostSystem findBestHost() throws RemoteException {
		return findEmptiestHost(0);
	}

	/**
	 * Find the host with the most storage space.
	 * <p>
	 * Note that the storage could be on multiple datastore, so this function should be used carefuly.
	 *
	 * @param minDiskSize the minimal size the host should have
	 * @return the host with the most space available
	 * @throws RemoteException on vsphere error
	 */
	private static HostSystem findEmptiestHost(long minDiskSize) throws RemoteException {
		ManagedEntity[] mes = new InventoryNavigator(rootFolder).searchManagedEntities(HOST_SYSTEM);
		HostSystem output = null;
		double currentFreeSpace = -1;
		for (int i = 0; i < mes.length; i++) {
			HostSystem host = (HostSystem) mes[i];
			double freeSpace = getHostCapacity(host);
			if (freeSpace > currentFreeSpace && freeSpace > minDiskSize) {
				output = host;
				currentFreeSpace = freeSpace;
			}
		}

		return output;
	}

	/**
	 * Return the host storage capacity.
	 *
	 * @param host the host to check
	 * @return the free space
	 * @throws RemoteException on vsphere error
	 */
	private static double getHostCapacity(HostSystem host) throws RemoteException {
		double output = 0;
		for (Datastore ds : host.getDatastores()) {
			output += ds.getInfo().getFreeSpace();
		}
		return output;
	}

	/**
	 * Return the host with the emptiest datastore.
	 *
	 * @return the host with the emptiest datastore
	 * @throws RemoteException on vsphere error
	 */
	static HostSystem findHostWithEmptiestDatastore() throws RemoteException {
		return findHostWithEmptiestDatastore(0);
	}

	/**
	 * Find the host with the datastore having the largest free space.
	 * <p>
	 * If multiple hosts share this datastore, then the host with the most free space will be returned.
	 *
	 * @param minDiskSize the minimal disk size
	 * @return HostSystem or null if no datastore has a free space greater then minDiskSize
	 * @throws RemoteException on vsphere error
	 */
	private static HostSystem findHostWithEmptiestDatastore(long minDiskSize) throws RemoteException {
		Datastore ds = findEmptiestDatastore(minDiskSize);
		if (ds == null) {
			return null;
		}
		HostSystem output = null;
		double size = minDiskSize;
		for (int i = 0; i < ds.getHost().length; i++) {
			HostSystem host = new HostSystem(service.getServerConnection(), ds.getHost()[i].getKey());
			double hostSize = getHostCapacity(host);
			if (hostSize >= size) {
				size = hostSize;
				output = host;
			}
		}

		return output;
	}

	/**
	 * Return all the datastores.
	 * <p>
	 * Return every datastores by finding them using their hosts instead of using an inventoryNavigator.
	 * Used because vijava is buggy and yavijava brings differents bugs while solving this problem.
	 *
	 * @return a list of all datastores
	 * @throws RemoteException on vsphere error
	 */
	private static Datastore[] getAllDatastore() throws RemoteException {
		ManagedEntity[] mes = new InventoryNavigator(rootFolder).searchManagedEntities(HOST_SYSTEM);
		Set<Datastore> output = new HashSet<>();

		for (int i = 0; i < mes.length; i++) {
			HostSystem host = (HostSystem) mes[i];
			for (Datastore ds : host.getDatastores()) {
				if (!output.contains(ds)) {
					output.add(ds);
				}
			}
		}

		return output.toArray(new Datastore[output.size()]);
	}

	/**
	 * Return the emptiest datastore.
	 *
	 * @return the emptiest datastore
	 * @throws RemoteException on vsphere error
	 */
	static Datastore findEmptiestDatastore() throws RemoteException {
		return findEmptiestDatastore(0);
	}

	/**
	 * Find the emptiest dataStore and return null if none of them has a
	 * free space superior to minDiskSIze.
	 *
	 * @param minDiskSize the minimal disk size to be selected
	 * @return the found Object
	 * @throws RemoteException on vsphere error
	 */
	private static Datastore findEmptiestDatastore(long minDiskSize) throws RemoteException {
		Datastore output = null;

		//ManagedEntity[] mes = new InventoryNavigator(rootFolder).searchManagedEntities("Datastore"); //doesn't work with vijava
		Datastore[] dss = getAllDatastore();
		double currentMinSpace = minDiskSize;
		for (int i = 0; i < dss.length; i++) {
			Datastore ds = dss[i];
			double freeSpace = ds.getInfo().getFreeSpace();
			if (freeSpace >= currentMinSpace) {
				output = ds;
				currentMinSpace = freeSpace;
			}
		}

		return output;
	}

	/**
	 * Import a local OVA into VSphere.
	 * <p>
	 * The OVA is imported with the name "newVmName".
	 *
	 * @param ova       the OVA file to import
	 * @param newVmName the new name for the VM
	 * @return the created VM id in String form
	 * @throws IOException on vsphere error
	 */
	public static String importLocalOVA(File ova, String newVmName) throws IOException {
		checkConnection();
		if (!ova.exists()) {
			Logger.log(Level.INFO, "Import OVA Failed - OVA could not be found");
			return null;
		}

		// Default HostSystem
		HostSystem host = findHostWithEmptiestDatastore();

		if (host == null) {
			Logger.log(Level.SEVERE, "no hsots were found to create a VM - unsufficient disk size or connexion error");
			return null;
		}

		String hostIp = host.getName();

		// VM folder
		Folder vmFolder = (Folder) host.getVms()[0].getParent();
		// ResourcePool
		ResourcePool rp = ((ComputeResource) host.getParent()).getResourcePool();

		OvfCreateImportSpecParams importSpecParams = createImportSpecParams(host, newVmName);

		String ovfLocal = findOVFinOVA(ova);
		if (ovfLocal == null) {
			Logger.log(Level.INFO, "Import OVA Failed - OVF could not be found");
			return null;
		}

		String ovfDescriptor = findFileinOVAAsText(ova, ovfLocal);

		if (ovfDescriptor == null) {
			Logger.log(Level.INFO, "Import OVA failed - OVF could not be read");
			return null;
		}

		OvfCreateImportSpecResult ovfImportResult = service.getOvfManager().createImportSpec(ovfDescriptor, rp,
				host.getDatastores()[0], importSpecParams);

		if (ovfImportResult == null) {
			Logger.log(Level.WARNING, "Import OVA failed - failed to create import specs");
			return null;
		}

		ImportSpec importSpecs = ovfImportResult.getImportSpec();
		if (importSpecs == null) {
			Logger.log(Level.WARNING, "Import OVA failed - failed to create import specs (2)");
			return null;
		}

		HttpNfcLease httpNfcLease = rp.importVApp(importSpecs, vmFolder, host);

		// Wait until the HttpNfcLeaseState is ready
		if (!waitForLease(httpNfcLease)) {
			Logger.log(Level.WARNING, "OVA import - HLS connexion failed");
			return null;
		}

		HttpNfcLeaseInfo httpNfcLeaseInfo = httpNfcLease.getInfo();

		LeaseProgressUpdater leaseUpdater = new LeaseProgressUpdater(httpNfcLease, 5000);
		leaseUpdater.start();

		HttpNfcLeaseDeviceUrl[] deviceUrls = httpNfcLeaseInfo.getDeviceUrl();

		long bytesAlreadyWritten = 0;
		long totalBytes = addTotalBytes(ovfImportResult);

		for (HttpNfcLeaseDeviceUrl deviceUrl : deviceUrls) {
			String deviceKey = deviceUrl.getImportKey();
			for (OvfFileItem ovfFileItem : ovfImportResult.getFileItem()) {
				if (deviceKey.equals(ovfFileItem.getDeviceId())) {
					String urlToPost = deviceUrl.getUrl().replace("*", hostIp);
					uploadVmdkFileFromOVA(ovfFileItem.getPath(), urlToPost, bytesAlreadyWritten, totalBytes,
							ova, leaseUpdater);
					bytesAlreadyWritten += ovfFileItem.getSize();
				}
			}
		}

		leaseUpdater.interrupt();
		httpNfcLease.httpNfcLeaseProgress(100);
		httpNfcLease.httpNfcLeaseComplete();

		String vmId = httpNfcLease.getInfo().entity.val;

		if (vmId == null) {
			Logger.log(Level.WARNING, "OVA import fail - the VM could not be found after supposed creation");
			return null;
		}
		Logger.log(Level.INFO, "OVA import succesful");

		return vmId;
	}

	// Methods to import OVA region

	/**
	 * Create the import specs.
	 *
	 * @param host      the host to import into
	 * @param newVmName the name of the VM
	 * @return the OVFCreateImportSpecParam needed to import the OVA
	 * @throws IOException on vsphere error
	 */
	private static OvfCreateImportSpecParams createImportSpecParams(HostSystem host, String newVmName)
			throws IOException {
		OvfCreateImportSpecParams importSpecParams = new OvfCreateImportSpecParams();
		importSpecParams.setHostSystem(host.getMOR());
		importSpecParams.setLocale("US");
		importSpecParams.setEntityName(newVmName);
		importSpecParams.setDeploymentOption("");
		OvfNetworkMapping networkMapping = new OvfNetworkMapping();
		networkMapping.setName("Network 1");
		networkMapping.setNetwork(host.getNetworks()[0].getMOR());
		importSpecParams.setNetworkMapping(new OvfNetworkMapping[]{networkMapping});
		importSpecParams.setPropertyMapping(null);

		return importSpecParams;
	}

	/**
	 * Return the total size of the disks described in the ovfImportResult.
	 *
	 * @param ovfImportResult the result
	 * @return the size of the disks
	 */
	private static long addTotalBytes(OvfCreateImportSpecResult ovfImportResult) {
		OvfFileItem[] fileItemArr = ovfImportResult.getFileItem();

		long totalBytes = 0;
		if (fileItemArr != null) {
			for (OvfFileItem fi : fileItemArr) {
				totalBytes += fi.getSize();
			}
		}
		return totalBytes;
	}

	/**
	 * upload a vmdk file from the OVA to VSphere
	 *
	 * @param filename            the file name in the ova
	 * @param urlStr              the associated upload URL of this file given by vsphere
	 * @param bytesAlreadyWritten the number of bytes already written
	 * @param totalBytes          the total size of bytes to write
	 * @param ova                 the File in which the Vmdk file is
	 * @param leaseUpdater the lease updater
	 * @throws IOException on reading error
	 */
	private static void uploadVmdkFileFromOVA(String filename, String urlStr, long bytesAlreadyWritten,
			long totalBytes, File ova, LeaseProgressUpdater leaseUpdater) throws IOException {

		try (TarArchiveInputStream inputStream = new TarArchiveInputStream(new FileInputStream(ova))) {
			TarArchiveEntry entry;
			while ((entry = inputStream.getNextTarEntry()) != null) {
				if (filename.equals(entry.getName())) {
					HttpsURLConnection conn = createHttpsURLConnection(urlStr,
							inputStream.getCurrentEntry().getSize());
					BufferedOutputStream bos = new BufferedOutputStream(conn.getOutputStream());

					uploadVMDKFileBuffered(inputStream, bos, bytesAlreadyWritten, totalBytes, leaseUpdater);

					bos.flush();
					bos.close();
					conn.disconnect();
				}
			}
		}
	}

	/**
	 * Create the HttpsURLConnection to upload a file.
	 *
	 * @param urlStr the URL to reach
	 * @param fileLength the final file length
	 * @return the connection
	 * @throws IOException on opening error
	 */
	private static HttpsURLConnection createHttpsURLConnection(String urlStr, long fileLength)
			throws IOException {
		HttpsURLConnection.setDefaultHostnameVerifier((String urlHostName, SSLSession session) -> true);

		HttpsURLConnection conn = (HttpsURLConnection) new URL(urlStr).openConnection();
		conn.setDoOutput(true);
		conn.setUseCaches(false);
		conn.setChunkedStreamingMode(64 * 1024);
		conn.setRequestMethod("POST"); // Use a post method to write the file.
		conn.setRequestProperty("Connection", "Keep-Alive");
		conn.setRequestProperty("Content-Type", "application/x-vnd.vmware-streamVmdk");
		conn.setRequestProperty("Content-Length", Long.toString(fileLength));

		return conn;
	}

	/**
	 * Write a file from the bis to the bos while updating the lease.
	 *
	 * @param bisObject           a bufferedInputStream or a TarArchiveInputStream
	 * @param bos output stream
	 * @param bytesAlreadyWritten self explaining
	 * @param totalBytes the total bytes of the file
	 * @param leaseUpdater the lease updater
	 * @throws IOException on reading error
	 */
	private static void uploadVMDKFileBuffered(Object bisObject, BufferedOutputStream bos, long bytesAlreadyWritten,
			long totalBytes, LeaseProgressUpdater leaseUpdater) throws IOException {
		TarArchiveInputStream bisTar = null;
		BufferedInputStream bis = null;
		boolean isTar = false;

		int bytesAvailable;

		if (bisObject.getClass().equals(TarArchiveInputStream.class)) {
			bisTar = (TarArchiveInputStream) bisObject;
			isTar = true;
			bytesAvailable = bisTar.available();
		} else if (bisObject.getClass().equals(BufferedInputStream.class)) {
			bis = (BufferedInputStream) bisObject;
			bytesAvailable = bis.available();
		} else {
			throw new IllegalArgumentException("Bad object class");
		}

		int bufferSize = Math.min(bytesAvailable, 64 * 1024);
		byte[] buffer = new byte[bufferSize];

		long totalBytesWritten = 0;
		int bytesRead;
		while (true) {
			bytesRead = isTar ? bisTar.read(buffer, 0, bufferSize) : bis.read(buffer, 0, bufferSize);

			if (bytesRead == -1) {
				break;
			}

			totalBytesWritten += bytesRead;
			bos.write(buffer, 0, bufferSize);
			bos.flush();
			int progressPercent = (int) (((bytesAlreadyWritten + totalBytesWritten) * 100) / totalBytes);
			leaseUpdater.setPercent(progressPercent);
		}
	}

	/**
	 * Read the content of a file and put it into a String.
	 *
	 * @param ovfFilePath the path of the file
	 * @return the content of the file
	 */
	protected static String readContent(String ovfFilePath) {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(ovfFilePath)))) {
			return readContent(in);
		} catch (IOException e) {
			Logger.log(Level.WARNING, "file could not be opened :" + ovfFilePath);
			return null;
		}
	}

	/**
	 * read the content of the reader.
	 *
	 * @param br the reader
	 * @return String the content
	 */
	private static String readContent(BufferedReader br) {
		StringBuilder strContent = new StringBuilder();
		try {
			String lineStr;
			while ((lineStr = br.readLine()) != null) {
				strContent.append(lineStr.trim());
			}
		} catch (IOException e) {
			Logger.log(Level.WARNING, "BufferedReader could not be read");
			return null;
		}

		return strContent.toString();
	}

	/**
	 * Wait for the lease to be on ready state and return a boolean indicating if
	 * the lease succesfully connected.
	 *
	 * @param lease the lease
	 * @return true if the lease is ready
	 */
	private static boolean waitForLease(HttpNfcLease lease) {
		HttpNfcLeaseState hls;
		do {
			hls = lease.getState();
		} while (hls != HttpNfcLeaseState.ready && hls != HttpNfcLeaseState.error);

		if (hls == HttpNfcLeaseState.error) {
			Logger.log(Level.WARNING, "lease failed : {0}", lease.getError());
		}

		return hls == HttpNfcLeaseState.ready;
	}

	// End of methods to import OVA region

	/**
	 * Return the host associated with the VM.
	 *
	 * @param vm the VM to search
	 * @return the host associated with the VM
	 * @throws RemoteException on vsphere error
	 */
	static HostSystem findHost(Vm vm) throws RemoteException {
		VirtualMachine vmVcenter = getVmVCenter(vm);
		return (vmVcenter == null) ? null : findHost(vmVcenter);
	}

	/**
	 * Return the host associated with the VM.
	 *
	 * @param vm the VM to search
	 * @return the host associated with the VM
	 * @throws RemoteException on vsphere error
	 */
	private static HostSystem findHost(VirtualMachine vm) throws RemoteException {
		// step 1 = get all hosts
		ManagedEntity[] mes = new InventoryNavigator(rootFolder).searchManagedEntities(HOST_SYSTEM);

		for (ManagedEntity me : mes) {
			HostSystem hs = (HostSystem) me;
			VirtualMachine[] listVms = hs.getVms();
			for (VirtualMachine vmTemp : listVms) {
				if (vmTemp.getMOR().val.equals(vm.getMOR().val)) {
					return hs;
				}
			}
		}

		return null;
	}

	/**
	 * Return the IP adress of the host hosting the VM.
	 *
	 * @param vm the VM to search
	 * @return the IP adress of the host
	 */
	public static String findHostIp(VirtualMachine vm) {
		try {
			HostSystem hs = findHost(vm);
			if (hs == null) {
				Logger.log(Level.WARNING, "no host found for VM {0}", vm.getMOR().val);
				return null;
			}
			return findHostIp(hs);
		} catch (RemoteException e) {
			return null;
		}
	}

	/**
	 * Return the IP address of the host
	 * @param host
	 * @return
	 */
	public static String findHostIp(HostSystem host){
		try{
			return host.getConfig().getNetwork().getVnic()[0].getSpec().getIp().getIpAddress();
		}catch(NullPointerException e){
			return null;
		}
	}

	/**
	 * return the host's ip associated to this datastore.
	 * @param ds
	 * @return
	 */
	public static String[] findHostIp(Datastore ds){
		String[] output = new String[ds.getHost().length];
		for (int i =0; i<ds.getHost().length; i++){
			HostSystem host = new HostSystem(service.getServerConnection(), ds.getHost()[i].getKey());
			output[i] = findHostIp(host);
		}

		return output;
	}

	/**
	 * Return the IP adress of the host hosting the VM.
	 *
	 * @param vm the VM to search
	 * @return the IP adress of the host
	 */
	static String findHostIp(Vm vm) {
		VirtualMachine vmVcenter = getVmVCenter(vm);
		return (vmVcenter == null) ? null : findHostIp(vmVcenter);
	}

	/**
	 * Create the name for the export folder.
	 *
	 * @param vm the VM to export
	 * @return the name of the created folder
	 */
	static String getDownloadFolderName(Vm vm) {
		return (vm.getUser().getLogin() + "_" + vm.getName()).replaceAll("[^A-Za-z0-9_]", "");
	}


	//download OVA

	/**
	 * Download all files associated with a VM and put them into an ova file (TAR compression).
	 *
	 * @param vm the VM to look for
	 * @return the OVA file
	 * @throws IOException on writing error
	 */
	public static File downloadAndPackageOVA(Vm vm) throws IOException {
		checkConnection();
		if (!downloadOVFApp(vm)) {
			Logger.log(Level.WARNING, "OVF files download failed");
			return null;
		}

		String name = getDownloadFolderName(vm);

		String targetDir = Utils.getString("temp_dir");
		if (!targetDir.endsWith("/") && !targetDir.endsWith("\\")) {
			targetDir += "/";
		}
		targetDir += name + "/";

		File output = packageOVFIntoOVA(new File(targetDir), name);

		//cleanup the rest
		Utils.deleteFolder(new File(targetDir));

		return output;
	}

	/**
	 * Return a file representing the OVA file.
	 * <p>
	 * Package all the files inside the directory ovfDir and save the created OVA
	 * file inside this directory. Then returns a File representing the OVA file.
	 *
	 * @param ovfDir the directory containing the OVF
	 * @param name   the name to be saved. only [a-zA-Z0-9], without the .ova
	 * @return the OVA file
	 * @throws IOException on reading error
	 */
	private static File packageOVFIntoOVA(File ovfDir, String name) throws IOException {
		if (ovfDir == null || !ovfDir.exists() || !ovfDir.isDirectory()) {
			Logger.log(Level.WARNING, "tried to package a folder that wasn't one", ovfDir);
			return null;
		}
		name = name.replaceAll("/[^A-Za-z0-9_]/", "") + ".ova";
		File output = new File(ovfDir.getParentFile(), name);
		Files.deleteIfExists(output.toPath());

		if (!output.createNewFile() || !output.canWrite()) {
			Logger.log(Level.WARNING, "cannot write to folder : {0}", output);
			return null;
		}

		//remove exsting file before writing it
		if (output.exists())
			Files.delete(output.toPath());

		TARUtils.compress(output.getAbsolutePath(), ovfDir);

		return output;
	}

	/**
	 * Download the VM files into the server.
	 *
	 * @param vm the VM to download from
	 * @return true if it is a success, false otherwise
	 * @throws IOException on reading error
	 */
	private static boolean downloadOVFApp(Vm vm) throws IOException {
		checkConnection();
		String outputFolderName = getDownloadFolderName(vm);
		String targetDir = Utils.getString("temp_dir");

		if (!targetDir.endsWith("/") && !targetDir.endsWith("\\")) {
			targetDir += "/";
		}
		targetDir += outputFolderName + "/";
		File outputDir = new File(targetDir);
		Utils.deleteFolder(outputDir);
		outputDir.mkdirs();

		VirtualMachine myMachine = VCenterManager.getVmVCenter(vm);

		if (myMachine == null) {
			Logger.log(Level.WARNING, "VM VCenter not found");
			return false;
		}

		String hostip = findHostIp(myMachine);

		if (hostip == null) {
			Logger.log(Level.WARNING, "could not find hostIp of VirtualMachine", myMachine);
			return false;
		}

		HttpNfcLease hnLease = myMachine.exportVm();

		ManagedEntity me = myMachine;

		// Wait until the HttpNfcLeaseState is ready
		if (!waitForLease(hnLease)) {
			Logger.log(Level.WARNING, "OVA download - lease failed");
			return false;
		}

		HttpNfcLeaseInfo httpNfcLeaseInfo = hnLease.getInfo();
		httpNfcLeaseInfo.setLeaseTimeout(300 * 1000 * 1000);

		// Note: the diskCapacityInByte could be many time bigger than
		// the total size of VMDK files downloaded.
		// As a result, the progress calculated could be much less than reality.
		long diskCapacityInByte = (httpNfcLeaseInfo.getTotalDiskCapacityInKB()) * 1024;

		LeaseProgressUpdater leaseDownloadUpdater = new LeaseProgressUpdater(hnLease, 5000);
		leaseDownloadUpdater.start();

		long alredyWrittenBytes = 0;
		HttpNfcLeaseDeviceUrl[] deviceUrls = httpNfcLeaseInfo.getDeviceUrl();
		if (deviceUrls != null) {
			OvfFile[] ovfFiles = new OvfFile[deviceUrls.length];
			for (int i = 0; i < deviceUrls.length; i++) {

				String deviceId = deviceUrls[i].getKey();
				String deviceUrlStr = deviceUrls[i].getUrl();

				String diskFileName = deviceUrlStr.substring(deviceUrlStr.lastIndexOf('/') + 1);
				String diskUrlStr = deviceUrlStr.replace("*", hostip);
				String diskLocalPath = targetDir + diskFileName;

				String cookie = service.getServerConnection().getVimService().getWsc().getCookie();
				long lengthOfDiskFile = writeVMDKFile(diskLocalPath, diskUrlStr, cookie, alredyWrittenBytes,
						diskCapacityInByte, leaseDownloadUpdater);
				alredyWrittenBytes += lengthOfDiskFile;
				OvfFile ovfFile = new OvfFile();
				ovfFile.setPath(diskFileName);
				ovfFile.setDeviceId(deviceId);
				ovfFile.setSize(lengthOfDiskFile);
				ovfFiles[i] = ovfFile;
			}

			OvfCreateDescriptorParams ovfDescParams = new OvfCreateDescriptorParams();
			ovfDescParams.setOvfFiles(ovfFiles);
			OvfCreateDescriptorResult ovfCreateDescriptorResult = service.getOvfManager().createDescriptor(me,
					ovfDescParams);

			String ovfPath = targetDir + outputFolderName + ".ovf";
			try (FileWriter out = new FileWriter(ovfPath)) {
				out.write(ovfCreateDescriptorResult.getOvfDescriptor());
			}
		}

		leaseDownloadUpdater.interrupt();
		hnLease.httpNfcLeaseProgress(100);
		hnLease.httpNfcLeaseComplete();

		return true;
	}

	/**
	 * download the VMDK file to the server
	 *
	 * @param localFilePath        the path in which the file shall be saved
	 * @param diskUrl              the download URL given by VSphere
	 * @param cookie               the download cookie
	 * @param bytesAlreadyWritten  the number of bytes already written
	 * @param totalBytes           the total size of the VM's files
	 * @param leaseDownloadUpdater the lease updater
	 * @return the number of bytes written
	 * @throws IOException on writing error
	 */
	private static long writeVMDKFile(String localFilePath, String diskUrl, String cookie, long bytesAlreadyWritten,
			long totalBytes, LeaseProgressUpdater leaseDownloadUpdater) throws IOException {
		HttpsURLConnection conn = getHTTPConnection(diskUrl, cookie);
		long bytesWritten = 0;
		try (OutputStream out = new FileOutputStream(new File(localFilePath));
				InputStream in = conn.getInputStream();) {
			byte[] buf = new byte[102400];
			int len = 0;

			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
				bytesWritten += len;
				int percent = (int) (((bytesAlreadyWritten + bytesWritten) * 100) / totalBytes);
				leaseDownloadUpdater.setPercent(percent);
			}
		}

		return bytesWritten;
	}

	/**
	 * create the connection to download a file
	 *
	 * @param urlStr the URL to reach
	 * @param cookieStr the current cookie
	 * @return the connection
	 * @throws IOException on opening error
	 */
	private static HttpsURLConnection getHTTPConnection(String urlStr, String cookieStr) throws IOException {

		HttpsURLConnection.setDefaultHostnameVerifier((String urlHostName, SSLSession session) -> true);
		URL url = new URL(urlStr);
		HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

		conn.setDoInput(true);
		conn.setDoOutput(true);
		conn.setAllowUserInteraction(true);
		conn.setRequestProperty("Cookie", cookieStr);
		conn.connect();
		return conn;
	}

	//end download OVA

	/**
	 * Return the Folder in which the VM is stored.
	 * <p>
	 * Note that it is different from a datastore.
	 *
	 * @param vm the VM to search
	 * @return the Folder containing the VM
	 */
	static Folder getVmFolder(VirtualMachine vm) {
		Folder output = null;
		ManagedEntity ancestor = vm.getParent();
		while (ancestor != null && ancestor.getClass() != Folder.class) {
			ancestor = ancestor.getParent();
		}
		if (ancestor != null) {
			output = (Folder) ancestor;
		}
		return output;
	}

	/**
	 * Return a name for a VM in Vcenter.
	 * <p>
	 * Creates a unique String using vm.getUser() + _ + vm.getName().
	 *
	 * @param vm the VM for creating the name
	 * @return the name created
	 */
	static String createVCenterVmName(Vm vm) {
		String output = "";
		output += (vm.getUser() != null) ? vm.getUser().getLogin() + "_" : "noUser_";
		output += vm.getName();
		return output;
	}

	/**
	 * Clone the VM or create a template.
	 * <p>
	 * The boolean isTemplate allow to choose between cloning the VM if false or
	 * creating a template if true.
	 *
	 * @param vm the VM to clone or to create a template from
	 * @param name the name of the new vm
	 * @param isTemplate if the new vm is a template
	 * @return the new VM's id
	 * @throws IOException on writing error
	 * @throws InterruptedException on task cancel
	 */
	public static String cloneVm(Vm vm, String name, boolean isTemplate) throws IOException, InterruptedException {
		checkConnection();
		VirtualMachine vmVcenter = getVmVCenter(vm);
		if (vmVcenter == null) {
			Logger.log(Level.WARNING, "cloneVm - getVmVcenter returned null for existing Vm", vm);
			return null;
		}

		Folder vmFolder = getVmFolder(vmVcenter);
		if (vmFolder == null) {
			Logger.log(Level.WARNING, "cloneVm - getVmFolder returned null for existing Vm", vm);
			return null;
		}

		VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
		cloneSpec.setLocation(new VirtualMachineRelocateSpec());
		cloneSpec.setPowerOn(false);
		if (isTemplate)
			cloneSpec.setTemplate(true);
		else
			cloneSpec.setTemplate(false);

		return doVMClone(name, vmVcenter, vmFolder, cloneSpec);
	}

	private static String doVMClone(String name, VirtualMachine vmVcenter, Folder vmFolder, VirtualMachineCloneSpec cloneSpec) throws RemoteException, InterruptedException {
		Task task = vmVcenter.cloneVM_Task(vmFolder, generateUid(name), cloneSpec);

		String status = task.waitForTask();
		if (status.equals(Task.SUCCESS)) {
			VirtualMachine createdVm = (VirtualMachine) MorUtil.createExactManagedEntity(service.getServerConnection(),
					(ManagedObjectReference) task.getTaskInfo().getResult());
			Logger.log(Level.INFO, "cloneVM  - Task success");
			return createdVm.getMOR().get_value();
		} else {
			Logger.log(Level.WARNING, "cloneVM - Task failed");
			return null;
		}
	}

	/**
	 * Delete the VM.
	 *
	 * @param vm the VM to delete
	 * @return true if the deletion is successful, false otherwise
	 * @throws RemoteException on vsphere error
	 * @throws InterruptedException on task cancel
	 */
	public static boolean deleteVm(Vm vm) throws RemoteException, InterruptedException {
		checkConnection();
		VirtualMachine vmVcenter = getVmVCenter(vm);

		if (vmVcenter == null) {
			Logger.log(Level.WARNING, "deleteVm - getVmVcenter returned null for existing Vm", vm);
			return false;
		}

		Task task = vmVcenter.destroy_Task();
		String status = task.waitForTask();
		if (status == Task.SUCCESS) {
			Logger.log(Level.INFO, "deleteVM success : {0}", vm);
			return true;
		} else {
			Logger.log(Level.WARNING, "deleteVM - Task failed : ", vm);
			return false;
		}
	}

	/**
	 * Clone the VM template to an actual VM.
	 *
	 * @param vm the VM template
	 * @param name the name of the template
	 * @return the MOR of the new VM
	 * @throws IOException on writing error
	 * @throws InterruptedException on task cancel
	 */
	public static String cloneTemplate(Vm vm, String name) throws IOException, InterruptedException {
		checkConnection();
		VirtualMachine vmVcenter = getVmVCenter(vm);

		if (vmVcenter == null) {
			Logger.log(Level.WARNING, "cloneTemplate - getVmVcenter returned null for existing Vm", vm);
			return null;
		}

		Folder vmFolder = getVmFolder(vmVcenter);
		if (vmFolder == null) {
			Logger.log(Level.WARNING, "cloneTemplate - getVmFolder returned null for existing template", vm);
			return null;
		}

		ManagedEntity[] resourcePoolEntities = new InventoryNavigator(rootFolder).searchManagedEntities("ResourcePool");

		Datastore[] datastores = vmVcenter.getDatastores();

		if (resourcePoolEntities.length == 0 || datastores.length == 0) {
			Logger.log(Level.SEVERE, "No resource pool or datastore found");
			return null;
		}

		VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
		VirtualMachineRelocateSpec relocateSpec = new VirtualMachineRelocateSpec();
		relocateSpec.setPool(resourcePoolEntities[0].getMOR());
		relocateSpec.setDatastore(datastores[0].getMOR());
		cloneSpec.setLocation(relocateSpec);
		cloneSpec.setPowerOn(false);
		cloneSpec.setTemplate(false);

		return doVMClone(name, vmVcenter, vmFolder, cloneSpec);
	}

	/**
	 * Generate a unique ID.
	 *
	 * @param name base name
	 * @return base name with unique id
	 */
	private static String generateUid(String name) {
		return name + "_" + UUID.randomUUID().toString().substring(0, 7);
	}

	/**
	 * Return the list of templates.
	 *
	 * @return a list containing the templates
	 */
	public static List<VirtualMachine> getTemplate() {
		checkConnection();
		VirtualMachine[] arrayVm = getVmVcenterList();
		List<VirtualMachine> listTemplate = new ArrayList<>();
		for (VirtualMachine virtualMachine : arrayVm) {
			if (virtualMachine.getConfig().isTemplate()) {
				listTemplate.add(virtualMachine);
			}
		}
		return listTemplate;
	}

	/**
	 * Creates a VM from a snapshot.
	 * <p>
	 * Creates a new VM from the snapshot passed in parameters and
	 * return the name of the created VM.
	 *
	 * @param snapshot the snapshot to clone from
	 * @param cloneName  the name of the cloned VM
	 * @return the created VM's id
	 * @throws IOException on writing error
	 * @throws InterruptedException on vsphere error
	 */
	public static String vmFromSnapshot(Snapshot snapshot, String cloneName)
			throws IOException, InterruptedException {
		checkConnection();
		VirtualMachine vmVcenter = getVmVCenter(snapshot.getVm());
		VirtualMachineSnapshot vmsnapshot = getSnapshotFromTree(snapshot);

		if (vmsnapshot == null || vmVcenter == null) {
			return null;
		}

		VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
		cloneSpec.setPowerOn(false);
		cloneSpec.setTemplate(false);
		cloneSpec.setLocation(new VirtualMachineRelocateSpec());
		cloneSpec.setSnapshot(vmsnapshot.getMOR());

		Folder parent = (Folder) vmVcenter.getParent();
		Task task;

		task = vmVcenter.cloneVM_Task(parent, cloneName, cloneSpec);
		task.waitForTask();

		if (task.getTaskInfo().getState() == TaskInfoState.success) {
			VirtualMachine createdVm = (VirtualMachine) MorUtil.createExactManagedEntity(service.getServerConnection(),
					(ManagedObjectReference) task.getTaskInfo().getResult());
			return createdVm.getMOR().get_value();
		}
		Logger.log(Level.WARNING, "Clone snapshot - task failed : {0}", snapshot);
		return null;
	}

	/**
	 * Delete the disk and the vmdk file.
	 *
	 * @param vm    the disk's VM
	 * @param label the label of the disk
	 * @return boolean true if success, else otherwise
	 */
	public static boolean deleteDisk(Vm vm, String label) {
		checkConnection();
		VirtualMachine vmVcenter = getVmVCenter(vm);
		if (vmVcenter == null)
			return false;
		VirtualDevice[] devices = null;
		try {
			VirtualMachineConfigInfo vmci = vmVcenter.getConfig();
			devices = vmci.getHardware().getDevice();
		} catch (Exception e) {
			Logger.log(Level.SEVERE, "SDK failure");
			return false;
		}
		VirtualDisk theDisk = null;
		for (int i = 0; devices != null && i < devices.length; i++) {
			if (devices[i].getDeviceInfo().getLabel().equals(label)) {
				theDisk = (VirtualDisk) devices[i];
			}
		}
		if (theDisk == null) {
			Logger.log(Level.SEVERE, "False - disk not found", vm);
			return false;
		}
		VirtualDeviceConfigSpec vdcs = new VirtualDeviceConfigSpec();
		vdcs.setOperation(VirtualDeviceConfigSpecOperation.remove);
		vdcs.setFileOperation(VirtualDeviceConfigSpecFileOperation.destroy);
		vdcs.setDevice(theDisk);
		return applyDiskModification(vm, vmVcenter, vdcs, "Disk deletion failed", "Disk deletion failed : ");
	}

	/**
	 * Modify the disk's capacity, can only increase it.
	 *
	 * @param vm       the vm's disk
	 * @param label    label of the disk
	 * @param capacity new capacity (in kb)
	 * @return boolean true if success, otherwise false
	 */
	public static boolean modifyDisk(Vm vm, String label, Long capacity) {
		checkConnection();
		VirtualMachine vmVcenter = getVmVCenter(vm);
		if (vmVcenter == null)
			return false;
		VirtualMachineConfigInfo vmci = vmVcenter.getConfig();
		VirtualDevice[] devices = vmci.getHardware().getDevice();
		VirtualDisk theDisk = null;
		for (int i = 0; devices != null && i < devices.length; i++) {
			if (devices[i].getDeviceInfo().getLabel().equals(label)) {
				theDisk = (VirtualDisk) devices[i];
			}
		}
		if (theDisk == null) {
			Logger.log(Level.SEVERE, "False - disk not found", vm);
			return false;
		}
		theDisk.setCapacityInKB(capacity);
		VirtualDeviceConfigSpec vdcs = new VirtualDeviceConfigSpec();
		vdcs.setDevice(theDisk);
		vdcs.setOperation(VirtualDeviceConfigSpecOperation.edit);
		return applyDiskModification(vm, vmVcenter, vdcs, "Disk reconfiguration failed", "Disk reconfiguration failed : ");
	}

	private static boolean applyDiskModification(Vm vm, VirtualMachine vmVcenter, VirtualDeviceConfigSpec vdcs, String s, String s2) {
		VirtualMachineConfigSpec vmcs = new VirtualMachineConfigSpec();
		vmcs.setDeviceChange(new VirtualDeviceConfigSpec[]{vdcs});
		try {
			Task task = vmVcenter.reconfigVM_Task(vmcs);
			String status = waitForTask(task);
			if (status.equals(Task.SUCCESS)) {
				return true;
			} else {
				Logger.log(Level.SEVERE, s, vm);
				return false;
			}
		} catch (RemoteException | InterruptedException e) {
			Logger.log(Level.SEVERE, s2 + e.getMessage(), vm);
			return false;
		}
	}

	/**
	 * Wait for the task to be done, used for tests.
	 *
	 * @param task the task
	 * @return String result of the task
	 * @throws RemoteException on vsphere error
	 * @throws InterruptedException on task cancel
	 */
	static String waitForTask(Task task) throws RemoteException, InterruptedException {
		String status = task.waitForTask();
		if (!status.equals(Task.SUCCESS) && task.getTaskInfo().error != null)
			Logger.log(Level.SEVERE, "VCenter task failed : {0}", task.getTaskInfo().error.getLocalizedMessage());
		return task.waitForTask();
	}

	/**
	 * Create a new network.
	 *
	 * @param dvSwitchName    the name of the dvSwitch
	 * @param dvPortGroupName the name of the port group
	 * @param maxPorts        the maximum number of ports
	 * @param ephemeral if the network is the type ephemeral
	 * @return the key of the port group
	 */
	public static String createNetwork(String dvSwitchName, String dvPortGroupName, int maxPorts, boolean ephemeral) {
		checkConnection();
		DistributedVirtualSwitch dvSwitch = getDVSwitchByName(dvSwitchName);
		if (dvSwitch == null)
			return null;

		DVPortgroupConfigSpec cfgSpec = new DVPortgroupConfigSpec();
		cfgSpec.setName(dvPortGroupName);
		cfgSpec.setNumPorts(maxPorts);
		cfgSpec.setType((ephemeral ? DistributedVirtualPortgroupPortgroupType.ephemeral : DistributedVirtualPortgroupPortgroupType.earlyBinding)
				.toString());
		try {
			Task task = dvSwitch.addDVPortgroup_Task(new DVPortgroupConfigSpec[]{cfgSpec});
			String status = waitForTask(task);
			if (status.equals(Task.SUCCESS)) {
				DistributedVirtualPortgroup portgroup = getPortGroupByName(dvPortGroupName);
				return portgroup != null ? portgroup.getKey() : null;
			} else {

				Logger.log(Level.SEVERE, "PortGroup creation failed : {0}", dvPortGroupName);
			}
		} catch (RemoteException | InterruptedException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
		}
		return null;
	}

	/**
	 * Return the max ports for this network
	 *
	 * @param dvPortGroupId the network's id
	 * @return the number of ports in this network or -1 if not found
	 */
	public static int getNetworkMaxPorts(String dvPortGroupId) {
		checkConnection();
		DistributedVirtualPortgroup portgroup = getPortGroupById(dvPortGroupId);
		return portgroup == null ? -1 : portgroup.getConfig().getNumPorts();
	}

	/**
	 * Edit the network information.
	 *
	 * @param dvPortGroupId the ID of the port group
	 * @param maxPorts      the new maximum of ports
	 * @return true if it is a success, false otherwise
	 */
	public static boolean editNetwork(String dvPortGroupId, int maxPorts) {
		checkConnection();
		DistributedVirtualPortgroup portgroup = getPortGroupById(dvPortGroupId);
		if (portgroup == null)
			return false;

		DVPortgroupConfigSpec cfgSpec = new DVPortgroupConfigSpec();
		cfgSpec.setConfigVersion(portgroup.getConfig().configVersion);
		cfgSpec.setNumPorts(maxPorts);
		try {
			Task task = portgroup.reconfigureDVPortgroup_Task(cfgSpec);
			String status = waitForTask(task);
			if (status.equals(Task.SUCCESS)) {
				return true;
			} else {
				Logger.log(Level.SEVERE, "PortGroup edition failed : {0}", portgroup.getName());
			}
		} catch (RemoteException | InterruptedException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
		}
		return false;
	}

	/**
	 * Delete the network.
	 *
	 * @param dvPortGroupId the network to delete
	 * @return true if the network is deleted, false otherwise
	 */
	public static boolean deleteNetwork(String dvPortGroupId) {
		checkConnection();
		DistributedVirtualPortgroup portgroup = getPortGroupById(dvPortGroupId);
		if (portgroup == null)
			return true;

		try {
			Task task = portgroup.destroy_Task();
			String status = waitForTask(task);
			if (status.equals(Task.SUCCESS)) {
				return true;
			} else {
				Logger.log(Level.SEVERE, "PortGroup deletion failed : {0}", portgroup.getName());
			}
		} catch (RemoteException | InterruptedException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
		}
		return false;
	}

	/**
	 * Return the DVSwitch found by name.
	 *
	 * @param dvSwitchName the name of the DVSwitch to search.
	 * @return the DVSWitch if found, null otherwise
	 */
	private static DistributedVirtualSwitch getDVSwitchByName(String dvSwitchName) {
		try {
			Datacenter dc = getDatacenter();
			ManagedEntity[] childEntity = dc == null ? new ManagedEntity[0] : dc.getNetworkFolder().getChildEntity();
			for (ManagedEntity me : childEntity) {
				if (me instanceof DistributedVirtualSwitch && me.getName().equals(dvSwitchName)) {
					return (DistributedVirtualSwitch) me;
				}
			}
			Logger.log(Level.SEVERE, "Cannot find DistributedVirtualSwitch {0}", dvSwitchName);
		} catch (RemoteException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
		}
		return null;
	}

	/**
	 * Return the DVPortGroup found by name.
	 *
	 * @param dvPortGroupName the name of the DVPortGroup to search
	 * @return the DVPortGroup
	 */
	private static DistributedVirtualPortgroup getPortGroupByName(String dvPortGroupName) {
		try {
			Datacenter dc = getDatacenter();
			ManagedEntity[] childEntity = dc == null ? new ManagedEntity[0] : dc.getNetworkFolder().getChildEntity();
			for (ManagedEntity me : childEntity) {
				if (me instanceof DistributedVirtualPortgroup && me.getName().equals(dvPortGroupName)) {
					return (DistributedVirtualPortgroup) me;
				}
			}
			Logger.log(Level.SEVERE, "Cannot find DistributedVirtualPortgroup {0}", dvPortGroupName);
		} catch (RemoteException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
		}
		return null;
	}

	/**
	 * Return the DVPortGroup found by its Id.
	 *
	 * @param dvPortGroupId the Id of the Port Group to search.
	 * @return the DVPortGroup
	 */
	private static DistributedVirtualPortgroup getPortGroupById(String dvPortGroupId) {
		try {
			Datacenter dc = getDatacenter();
			ManagedEntity[] childEntity = dc == null ? new ManagedEntity[0] : dc.getNetworkFolder().getChildEntity();
			for (ManagedEntity me : childEntity) {
				if (me instanceof DistributedVirtualPortgroup && ((DistributedVirtualPortgroup) me).getKey().equals(dvPortGroupId)) {
					return (DistributedVirtualPortgroup) me;
				}
			}
			Logger.log(Level.SEVERE, "Cannot find DistributedVirtualPortgroup {0}", dvPortGroupId);
		} catch (RemoteException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
		}
		return null;
	}

	/**
	 * get the first datacenter
	 * @return
	 * @throws RemoteException
	 */
	private static Datacenter getDatacenter() throws RemoteException {
		ManagedEntity[] entities = new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter");
		return entities.length == 0 ? null : (Datacenter) entities[0];
	}

	/**
	 * Power off the VM.
	 *
	 * @param vm the VM to shutdown
	 * @return true if it is a success, false otherwise
	 */
	public static boolean shutdownVM(Vm vm) {
		checkConnection();
		VirtualMachine vmCenter = getVmVCenter(vm);
		if (vmCenter == null)
			return false;
		try {
			Task task = vmCenter.powerOffVM_Task();
			return waitForTask(task).equals(Task.SUCCESS);
		} catch (RemoteException | InterruptedException e) {
			Logger.log(Level.WARNING, e.toString(), e);
			return false;
		}
	}

	/**
	 * mount a ISO on a VM
	 * @param vm
	 * @param ds
	 * @param path path/to/vm
	 * @return
	 */
	public static boolean mountISO(Vm vm, Datastore ds, String path){
		return mountISO(vm, "["+ds.getName()+"]"+path);
	}

	//region ISO mounting    
	/**
	 * mount a ISO on a VM.
	 * @param vm
	 * @param iso the path to the iso : [datastore-XYZ]path/to/iso
	 * @return
	 */
	public static boolean mountISO(Vm vm, String iso) {
		try {

			VirtualMachine target = getVmVCenter(vm);
			if (target == null) {
				Logger.log(Level.WARNING, "mountISO - vmVcenter is null - aborting");
				return false;
			}
			if (iso == null) {
				Logger.log(Level.WARNING, "mountISO - iso is null - aborting");
				return false;
			}

			VirtualCdrom cd = null;

			// Check for exiting
			VirtualMachineDeviceManager dm = new VirtualMachineDeviceManager(target);
			VirtualDevice[] devices = dm.getAllVirtualDevices();
			for (int i = 0; i < devices.length; i++) {
				if (devices[i].getBacking() instanceof VirtualCdromIsoBackingInfo) {
					cd = (VirtualCdrom) devices[i];
					break;
				}
			}
			VirtualDeviceConfigSpec[] deviceChange = cd != null ? new VirtualDeviceConfigSpec[2] : new VirtualDeviceConfigSpec[1];

			if (cd != null) {
				VirtualDeviceConfigSpec cdSpec = new VirtualDeviceConfigSpec();
				cdSpec.setOperation(VirtualDeviceConfigSpecOperation.remove);
				cdSpec.setDevice(cd);
				deviceChange[0] = cdSpec;
			}

			ManagedObjectReference hmor = target.getRuntime().getHost();
			HostSystem targetHost = new HostSystem(service.getServerConnection(), hmor);
			Datastore[] ds = targetHost.getDatastores();

			if (ds.length == 0) {
				Logger.log(Level.WARNING, "could not find any datastore");
				return false;
			}

			Datastore isods = ds[0];

			String filename = iso;
			if (isods == null) {
				String dsName = iso.substring(1, iso.indexOf(']')).trim();
				isods = (Datastore) new InventoryNavigator(rootFolder).searchManagedEntity("Datastore", dsName);
			}

			Logger.log(Level.INFO, "Adding image " + filename);
			VirtualDeviceConfigSpec isoSpec = createISO(targetHost, isods, filename);
			if (cd != null) {
				deviceChange[1] = isoSpec;
			} else {
				deviceChange[0] = isoSpec;
			}					

			VirtualMachineConfigSpec configSpec = new VirtualMachineConfigSpec();
			configSpec.setDeviceChange(deviceChange);

			Task task = target.reconfigVM_Task(configSpec);
			return task != null && task.waitForTask().equals(Task.SUCCESS);

		} catch (InterruptedException | IOException e) {
			Logger.log(Level.WARNING, "error when mounting ISO");
			return false;
		}
	}

	/**
	 * create the config spec to import a ISO
	 * @param host
	 * @param ds
	 * @param fileName
	 * @return
	 * @throws RemoteException
	 */
	protected static VirtualDeviceConfigSpec createISO(HostSystem host, Datastore ds, String fileName) throws RemoteException {
		VirtualDeviceConfigSpec cdSpec = new VirtualDeviceConfigSpec();
		cdSpec.setOperation(VirtualDeviceConfigSpecOperation.add);

		VirtualCdrom cdInfo = new VirtualCdrom();
		VirtualCdromIsoBackingInfo iso = new VirtualCdromIsoBackingInfo();
		VirtualDeviceConnectInfo conn = new VirtualDeviceConnectInfo();
		iso.setDatastore(ds.getMOR());
		iso.setFileName(fileName);	
		conn.setStartConnected(true);
		cdInfo.setBacking(iso);
		cdInfo.setConnectable(conn);
		VirtualDevice ideController = getIDEController(host);
		if (ideController == null) {
			return null;
		}
		cdInfo.setControllerKey(ideController.key);
		cdInfo.setUnitNumber(0);
		cdInfo.setKey(- 4);//INDEX_ISO
		cdSpec.setDevice(cdInfo);
		return cdSpec;
	}

	/**
	 * get the IDE controller for a host
	 * @param host
	 * @return
	 * @throws RemoteException
	 */
	protected static VirtualDevice getIDEController(HostSystem host) throws RemoteException {
		VirtualDevice ideCtlr = null;
		VirtualDevice[] defaultDevices = getDefaultDevices(host);
		for (int i = 0; i < defaultDevices.length; i++) {
			if (defaultDevices[i] instanceof VirtualIDEController) {
				ideCtlr = defaultDevices[i];
				break;
			}
		}
		return ideCtlr;
	}

	/**
	 * get the default devices of a VM
	 * @param vm
	 * @return
	 * @throws RemoteException
	 */
	protected static VirtualDevice[] getDefaultDevices(VirtualMachine vm) throws RemoteException {
		VirtualMachineRuntimeInfo vmRuntimeInfo = vm.getRuntime();
		ManagedObjectReference hmor = vmRuntimeInfo.getHost();
		HostSystem host = new HostSystem(vm.getServerConnection(), hmor);		
		return getDefaultDevices(host);
	}

	/**
	 * get the default devices of a host
	 * @param host
	 * @return
	 * @throws RemoteException
	 */
	protected static VirtualDevice[] getDefaultDevices(HostSystem host) throws RemoteException {
		ComputeResource cr = (ComputeResource) host.getParent();
		EnvironmentBrowser envBrowser = cr.getEnvironmentBrowser();
		VirtualMachineConfigOption cfgOpt = envBrowser.queryConfigOption(null, host);
		VirtualDevice[] defaultDevs = null;

		VirtualDevice[] bad = new VirtualDevice[0];

		if (cfgOpt == null) {
			Logger.log(Level.SEVERE, "No VirtualMachineConfigOption found in EnvironmentBrowser");
			return bad;
		} else {
			defaultDevs = cfgOpt.getDefaultDevice();
			if (defaultDevs == null) {
				Logger.log(Level.SEVERE, "No defaultDevs found in VirtualMachineConfigOption");
				return bad;
			}
		}
		return defaultDevs;
	}

	//end region ISO mounting

	/**
	 * import a local ISO and create a VM with this ISO mounted
	 * @param file
	 * @param vmName
	 * @return
	 * @throws IOException
	 */
	public static String importLocalISO(File file, String vmName) throws IOException{
		
		vmName = vmName.replaceAll("[^a-zA-Z0-9 _-]", "");
		
		Datastore ds = findEmptiestDatastore(file.length());
		if (ds == null){
			Logger.log(Level.WARNING, "could not find a suitable datastore for importing an ISO");
			return null;
		}

		//create the VM
		String vmId = VSphereConnector.createVm(ds, vmName);

		if (vmId == null){
			Logger.log(Level.WARNING, "importISO - vm seems created but unreachable");
			return null;
		}
		
		Vm vm = new Vm();
		vm.setIdVmVcenter(vmId);
		
		VirtualMachine vmvc = getVmVCenter(vm);
		
		if (vmvc == null){
			Logger.log(Level.WARNING, "vmvc is null");
			return null;
		}
		
		//upload the ISO
		String fileName = vmName + '/'+ UUID.randomUUID()+".iso";

		if (! sendFileToAPI(file, ds, fileName)){
			Logger.log(Level.WARNING, "file upload error - could not upload file");
			return null;
		}
		
		//mount the ISO
		if (!mountISO(vm, ds, fileName)){
			Logger.log(Level.WARNING, "importISO - vm was created, iso uploaded, but couldn't mount" );
			return null;
		}

		return vmId;
	}

	/**
	 * send a file to VCenter
	 * @param file the local file
	 * @param ds the Datastore on which the file will be stored
	 * @param urlPath the path in the datastore : exemple : testFolder/myVm.iso
	 * @return
	 * @throws RemoteException
	 */
	public static boolean sendFileToAPI(File file, Datastore ds, String urlPath) throws RemoteException {
		if (!file.exists() || !file.canRead()) {
			Logger.log(Level.SEVERE, "could not open the file {0} - sendFileToAPI aborted", file.getAbsolutePath());
			return false;
		}

		String hostIp = VSPHERE_HOST;
		Datacenter dc =  getDatacenter();
		if (dc == null){
			return false;
		}
		String dcName = dc.getName();
		String dsName = ds.getName();

		if (urlPath.charAt(0) == '/'){
			Logger.log(Level.WARNING, "sendFileToAPI - urlPath begin with / : {0}", urlPath);
			urlPath = urlPath.substring(1);
		}
		
		urlPath = urlPath.replace(" ", "%20");

		return HttpUtils.executePutRequest(String.format("https://%s/folder/%s?dcPath=%s&dsName=%s", hostIp, urlPath, dcName, dsName), getRequestHeaders(), file.getAbsolutePath());
	}

	/**
	 * Create the request headers to upload a file to the API
	 * @return
	 */
	private static HashMap<String, String> getRequestHeaders() {
		String cookieValue = service.getServerConnection().getVimService().getWsc().getCookie();
		StringTokenizer tokenizer = new StringTokenizer(cookieValue, ";");
		cookieValue = tokenizer.nextToken();
		String path = "$" + tokenizer.nextToken();
		String cookie = "$Version=\"1\"; " + cookieValue + "; " + path;
		HashMap<String, String> headers = new HashMap<>();
		headers.put("Cookie", cookie);
		return headers;
	}
}