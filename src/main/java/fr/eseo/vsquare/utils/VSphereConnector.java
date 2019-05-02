package fr.eseo.vsquare.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.vmware.vim25.mo.Datastore;

import fr.eseo.vsquare.model.Network;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.Vm;
import fr.eseo.vsquare.utils.HttpUtils.HttpResult;
import fr.klemek.betterlists.BetterArrayList;

/**
 * This class provides wrapper functions to connect to VSphere.
 */
public final class VSphereConnector {
	
	private static final String VALUE = "value";

	public enum VmPowerState {
		POWERED_OFF, SUSPENDED, POWERED_ON
	}

	public enum VmPowerAction {
		START, STOP, SUSPEND, RESET;

		@Override
		public String toString() {
			return super.toString().toLowerCase();
		}
	}

	public static final JSONObject JSON_NOT_FOUND = new JSONObject("{'not_found':'404'}");
	public static final JSONObject JSON_SERVER_ERROR = new JSONObject("{'server_error':'500'}");

	private static final String NETWORK = "network";
	private static final String BACKING = "backing";

	private VSphereConnector() {

	}

	/**
	 * Check the Id of a VM.
	 *
	 * @param vm the VM to check
	 * @return true if the VM has a good Id
	 */
	public static boolean checkVmId(Vm vm) {
		if (vm == null) {
			Logger.log(Level.SEVERE, "Argument VM is null");
			return false;
		}
		if (vm.getIdVmVcenter() == null) {
			Logger.log(Level.SEVERE, "VM has no id");
			return false;
		}
		return true;
	}

	// VM POWER

	/**
	 * Enact an action on the power status of the VM.
	 *
	 * @param vm     the VM to modify
	 * @param action the action to perform on the VM
	 * @return HttpResult the result of the query or null
	 */
	public static HttpResult setVmPower(Vm vm, String action) {
		List<String> actionPossibles = Arrays.asList("start;reset;stop;suspend".split(";"));
		if (!actionPossibles.contains(action)) {
			Logger.log(Level.WARNING, "use of vmPower with unknown action");
			return null;
		}
		if (!checkVmId(vm)) {
			return null;
		}

		String url = "/vcenter/vm/" + vm.getIdVmVcenter() + "/power/" + action;
		Logger.log(Level.INFO, "POST request to " + url);
		return VSphereManager.requestVSphereAPI("POST", url);
	}

	/**
	 * Enact an action on the power status of all the VM in the list.
	 *
	 * @param user   the user doing the action
	 * @param action the action to perform on the VM
	 * @param vms    the list of VMs to modify
	 * @return the number of vms affected
	 */
	public static int setVmPowerAll(User user, String action, List<Vm> vms) {
		HttpResult result;
		int vmAffected = 0;
		for (Vm vm : vms) {
			if (vm != null) {
				if (!vm.hasAccessWrite(user)) {
					Logger.log(Level.INFO, "User tried to setPower on List<vm> on a Vm without writing rights");
				} else {
					result = VSphereConnector.setVmPower(vm, action);
					if (VSphereConnector.checkVmPowerChange(vm, action, result))
						vmAffected++;
				}
			}
		}
		return vmAffected;
	}

	/**
	 * Set a power action on the VM.
	 *
	 * @param vm     the VM to perform action on
	 * @param action the action to do
	 * @return the result of the query or null
	 */
	public static HttpResult setVmPower(Vm vm, VmPowerAction action) {
		return setVmPower(vm, action.toString());
	}

	/**
	 * Set the power action to start the VM
	 *
	 * @param vm the VM to start
	 * @return the result of the query or null
	 */
	public static HttpResult setVmPowerStart(Vm vm) {
		return setVmPower(vm, VmPowerAction.START.toString());
	}

	/**
	 * Set the power action to reset the VM.
	 *
	 * @param vm the VM to reset
	 * @return the result of the query or null
	 */
	public static HttpResult setVmPowerReset(Vm vm) {
		return setVmPower(vm, VmPowerAction.RESET.toString());
	}

	/**
	 * Set the power action to stop the VM.
	 *
	 * @param vm the VM to stop
	 * @return the result of the query or null
	 */
	public static HttpResult setVmPowerStop(Vm vm) {
		return setVmPower(vm, VmPowerAction.STOP.toString());
	}

	/**
	 * Set the power action to suspend the VM.
	 *
	 * @param vm the VM to suspend
	 * @return the result of the query or null
	 */
	public static HttpResult setVmPowerSuspend(Vm vm) {
		return setVmPower(vm, VmPowerAction.SUSPEND.toString());
	}

	/**
	 * Return query checking the VM power state.
	 *
	 * @param vm the VM to check
	 * @return the HttpResult of the query
	 */
	public static HttpResult getVmPower(Vm vm) {
		if (!checkVmId(vm)) {
			return null;
		}

		String url = "/vcenter/vm/" + vm.getIdVmVcenter() + "/power";
		return VSphereManager.requestVSphereAPI("GET", url);
	}

	/**
	 * Return the VM power state.
	 * <p>
	 * Extract the power statev from the http result.
	 *
	 * @param vm the VM to get power state from
	 * @return the VmPowerState of the VM
	 */
	public static VmPowerState getVmPowerState(Vm vm) {
		HttpResult res = getVmPower(vm);
		if (res == null || res.code != 200) {
			return null;
		}
		try {
			return VmPowerState.valueOf(res.getJSON().getJSONObject(ServletUtils.VALUE_KEY).getString("state"));
		} catch (IllegalArgumentException | JSONException e) {
			return null;
		}
	}

	/**
	 * Chech the valid change of power state.
	 * <p>
	 * Check if the action done by the setPower has the desired state
	 * because the server send an error if you try to start an active machine.
	 *
	 * @param vm     the VM to change
	 * @param action the action performed
	 * @param result the result returned by vsphere
	 * @return true if the power changed
	 */
	public static boolean checkVmPowerChange(Vm vm, String action, HttpResult result) {
		if (result == null) {
			return false;
		}
		if (result.code == 200) {
			return true;
		}
		if (result.code != 400) {
			return false;
		}

		VmPowerState currentState = getVmPowerState(vm);
		if (currentState == null) {
			return false;
		}
		return checkVmPowerChange(action, currentState);
	}

	/**
	 * Chech the valid change of power state.
	 * <p>
	 * Check if the action done by the setPower has the desired state
	 * because the server send an error if you try to start an active machine.
	 *
	 * @param action       the action performed
	 * @param currentState the current power state of the VM
	 * @return true if the power changed
	 */
	public static boolean checkVmPowerChange(String action, VmPowerState currentState) {
		if (currentState == null) {
			return false;
		}
		switch (currentState) {
		case POWERED_OFF:
			return action.equals(VmPowerAction.STOP.toString());
		case POWERED_ON:
			return action.equals(VmPowerAction.START.toString()) || action.equals(VmPowerAction.RESET.toString());
		case SUSPENDED:
			return action.equals(VmPowerAction.SUSPEND.toString());
		default:
			return false;
		}
	}


	/**
	 * Check if the action is possible on the VM.
	 * <p>
	 * Because some actions cannot be performed depending on the power state of the VM
	 * we check the compatibility of the actions.
	 *
	 * @param action       the action to check
	 * @param currentState the current state of the vm
	 * @return true if the action is possible, false otherwise
	 */
	public static boolean setVmPowerPossible(String action, VmPowerState currentState) {
		if (currentState == null) {
			return false;
		}
		switch (currentState) {
		case POWERED_OFF:
			return action.equals(VmPowerAction.START.toString());
		case POWERED_ON:
			return action.equals(VmPowerAction.RESET.toString()) || action.equals(VmPowerAction.SUSPEND.toString()) || action.equals(VmPowerAction.STOP.toString());
		case SUSPENDED:
			return action.equals(VmPowerAction.START.toString()) || action.equals(VmPowerAction.STOP.toString());
		default:
			return false;
		}
	}

	/**
	 * Return the host list.
	 *
	 * @return a json list or null on error
	 */
	public static JSONObject getHostList() {
		HttpResult hr = VSphereManager.requestVSphereAPI("GET", "/vcenter/host");
		return computeResult(hr);
	}

	/**
	 * Return the network list.
	 *
	 * @return a json list or null on error
	 */
	public static JSONObject getNetworkList() {
		HttpResult hr = VSphereManager.requestVSphereAPI("GET", "/vcenter/network");
		return computeResult(hr);
	}

	/**
	 * Return the datastore list.
	 *
	 * @return a json list or null on error
	 */
	public static JSONObject getDatastoreList() {
		HttpResult hr = VSphereManager.requestVSphereAPI("GET", "/vcenter/datastore");
		return computeResult(hr);
	}

	/**
	 * Return detailed information on a datastore.
	 *
	 * @param datastore the datastore
	 * @return a json object or null on error
	 */
	public static JSONObject getDatastoreDetails(String datastore) {
		String req = String.format("/vcenter/datastore/%s", datastore);

		HttpResult hr = VSphereManager.requestVSphereAPI("GET", req);
		return computeResult(hr);
	}

	/**
	 * Return the JSONObject obtained from the httpResult.
	 *
	 * @param hr the http result
	 * @return the JSONObject
	 */
	private static JSONObject computeResult(HttpResult hr) {
		switch (hr.code) {
		case 200:
			return hr.getJSON();
		case 404:
			return JSON_NOT_FOUND;
		default:
			Logger.log(Level.SEVERE, "VSphere returned code {0}", hr.code);
			return null;
		}
	}

	/**
	 * Return the VM list from VSphere.
	 *
	 * @param vmList the list of VM to load
	 * @return the VM list in json
	 */
	public static JSONObject getVmList(BetterArrayList<Vm> vmList) {
		if (vmList.isEmpty()) {
			JSONObject resp = new JSONObject();
			resp.put(ServletUtils.VALUE_KEY, new JSONArray());
			return resp;
		}

		List<String> ids = vmList.select(Vm::getIdVmVcenter);

		HashMap<String, String[]> params = new HashMap<>();
		params.put("filter.vms", ids.toArray(new String[0]));

		HttpResult hr = VSphereManager.requestVSphereAPI("GET", "/vcenter/vm", params);

		if (hr.code != 200) {
			return null;
		}

		JSONObject res = hr.getJSON();

		for (JSONObject obj : Utils.jArrayToJObjectList(res.getJSONArray(ServletUtils.VALUE_KEY))) {
			Vm vm = vmList.firstOrDefault(v -> v.getIdVmVcenter().equals(obj.getString("vm")), null);
			if (vm != null) {
				obj.put("name", vm.getName());
				obj.put("desc", vm.getDescription());
				obj.put("user", vm.getUser().toJSON(false));
			} else {
				Logger.log(Level.SEVERE, "VCenter returned a VM not asked : {0}", obj.getString("vm"));
			}
		}

		return res;
	}

	/**
	 * Get list of the template in JSON
	 *
	 * @param vmList the vm list
	 * @return the JSON value of the list
	 */
	public static JSONObject getTemplateList(List<Vm> vmList) {
		JSONObject json = new JSONObject();
		JSONArray array = new JSONArray();

		for (Vm vm : vmList) {
			JSONObject detail = VSphereConnector.getVmMiniDetails(vm);
			if (detail == null || detail.equals(VSphereConnector.JSON_NOT_FOUND)) {
				return null;
			}
			array.put(detail);
		}

		json.put(VALUE, array);
		return json;
	}


	/**
	 * Return the minimal information of the VM from VSphere.
	 * Use to list templates with minimal info from the API Rest.
	 *
	 * @param vm the VM to get details
	 * @return the VM details in json
	 */
	public static JSONObject getVmMiniDetails(Vm vm) {
		String req = String.format("/vcenter/vm/%s", vm.getIdVmVcenter());

		HttpResult hr = VSphereManager.requestVSphereAPI("GET", req);

		switch (hr.code) {
		case 200:
			JSONObject res = hr.getJSON();
			JSONObject value = res.getJSONObject(ServletUtils.VALUE_KEY);
			value.put("name", vm.getName());
			value.put("desc", vm.getDescription());

			JSONObject valueMini = new JSONObject();
			valueMini.put("memory_size_MiB", value.getJSONObject("memory").get("size_MiB"));
			valueMini.put("vm", vm.getIdVmVcenter());
			valueMini.put("name", vm.getName());
			valueMini.put("desc", vm.getDescription());
			valueMini.put("power_state", value.get("power_state"));
			valueMini.put("cpu_count", value.getJSONObject("cpu").get("count"));
			valueMini.put("user", vm.getUser().toJSON(false));
			return valueMini;
		case 404:
			return JSON_NOT_FOUND;
		default:
			return null;
		}
	}


	/**
	 * Return the detailed information of the VM from VSphere.
	 *
	 * @param vm the VM to get details
	 * @return the VM details in json
	 */
	public static JSONObject getVmDetails(Vm vm) {
		String req = String.format("/vcenter/vm/%s", vm.getIdVmVcenter());

		HttpResult hr = VSphereManager.requestVSphereAPI("GET", req);

		switch (hr.code) {
		case 200:
			JSONObject res = hr.getJSON();
			JSONObject value = res.getJSONObject(ServletUtils.VALUE_KEY);
			value.put("name", vm.getName());
			value.put("desc", vm.getDescription());

			if (value.has("guest_OS")) {
				String osCode = value.getString("guest_OS");
				String[] osInfo = Utils.getOsInfo(osCode);
				value.put("guest_OS_name", osInfo[0]);
				value.put("guest_OS_icon", osInfo[1]);
			}

			updateNetworkNames(value);
			return res;
		case 404:
			return JSON_NOT_FOUND;
		default:
			return null;
		}
	}

	/**
	 * Change network names for the ones in database
	 *
	 * @param value the JSON vm details
	 */
	private static void updateNetworkNames(JSONObject value) {
		if (value.has("nics")) {
			for (JSONObject nic : Utils.jArrayToJObjectList(value.getJSONArray("nics"))) {
				JSONObject backing = Utils.navigateJSON(nic, VALUE, BACKING);
				if (backing != null && backing.has(NETWORK)) {
					String network = backing.getString(NETWORK);
					Network net = Network.findByIdNetworkVcenter(network);
					if (net != null)
						backing.put("network_name", net.getName());
					else if (!backing.has("network_name"))
						backing.put("network_name", Utils.getString("vsphere_default_network_name"));
				}
			}
		}
	}

	/**
	 * Delete the VM from VSphere and the database.
	 *
	 * @param vm the VM to delete
	 * @return the VSphere response in JSON
	 */
	public static JSONObject deleteVm(Vm vm) {
		String req = String.format("/vcenter/vm/%s", vm.getIdVmVcenter());

		HttpResult hr = VSphereManager.requestVSphereAPI("DELETE", req);

		switch (hr.code) {
		case 200:
			if (!vm.delete()) {
				return JSON_SERVER_ERROR;
			}
			return hr.getJSON();
		case 404:
			return JSON_NOT_FOUND;
		default:
			return null;
		}
	}

	/**
	 * Update the RAM capacity of the VM.
	 *
	 * @param vm         the VM to update
	 * @param memorySize the RAM capacity
	 * @return the VSphere response in JSON
	 */
	public static int updateRam(Vm vm, int memorySize) {
		JSONObject json = new JSONObject();
		json.put("size_MiB", memorySize);
		return updateRam(vm, json);
	}

	/**
	 * Update the RAM capacity of the VM.
	 *
	 * @param vm   the VM to update
	 * @param json body
	 * @return the VSphere response in JSON
	 */
	public static int updateRam(Vm vm, JSONObject json) {
		String req = String.format("/vcenter/vm/%s/hardware/memory", vm.getIdVmVcenter());

		HttpResult hr = VSphereManager.requestVSphereAPI("PATCH", req, json);

		return hr.code;
	}

	private static JSONObject getJsonObjectCode(HttpResult hr) {
		switch (hr.code) {
		case 200:
			return hr.getJSON();
		case 404:
			return JSON_NOT_FOUND;
		default:
			return null;
		}
	}

	/**
	 * Update the number of CPU of the VM.
	 *
	 * @param vm       the VM to update.
	 * @param cpuCount the number of CPU
	 * @return the VSphere response code.
	 */
	public static int updateCpu(Vm vm, int cpuCount) {
		JSONObject json = new JSONObject();
		json.put("count", cpuCount);
		return updateCpu(vm, json);
	}

	/**
	 * Update the number of CPU of the VM.
	 *
	 * @param vm   the VM to update.
	 * @param json body
	 * @return the VSphere response code.
	 */
	public static int updateCpu(Vm vm, JSONObject json) {
		String req = String.format("/vcenter/vm/%s/hardware/cpu", vm.getIdVmVcenter());

		HttpResult hr = VSphereManager.requestVSphereAPI("PATCH", req, json);

		return hr.code;
	}


	/**
	 * Return the number of sata adapters of the VM.
	 *
	 * @param vm the VM to check
	 * @return the VSphere response code in JSON
	 */
	public static JSONObject getSataAdapter(Vm vm) {
		String req = String.format("/vcenter/vm/%s/hardware/adapter/sata", vm.getIdVmVcenter());

		HttpResult hr = VSphereManager.requestVSphereAPI("GET", req);

		return getJsonObjectCode(hr);
	}

	/**
	 * Create a new sata adapter for the VM.
	 *
	 * @param vm   the VM having a new sata adapter
	 * @param json body
	 * @return the VSphere response code
	 */
	public static int createSataAdapter(Vm vm, JSONObject json) {
		String req = String.format("/vcenter/vm/%s/hardware/adapter/sata", vm.getIdVmVcenter());

		HttpResult hr = VSphereManager.requestVSphereAPI("POST", req, json);

		return hr.code;
	}

	/**
	 * Create a new sata disk for the VM.
	 *
	 * @param vm   the VM having a new sata disk
	 * @param json body
	 * @return the Vsphere response code
	 */
	public static int createSataDisk(Vm vm, JSONObject json) {
		String req = String.format("/vcenter/vm/%s/hardware/disk", vm.getIdVmVcenter());

		HttpResult hr = VSphereManager.requestVSphereAPI("POST", req, json);

		return hr.code;
	}


	/**
	 * Delete the sata disk of the VM.
	 *
	 * @param vm the VM to delete the sata disk from.
	 * @param id the disk's id
	 * @return the VSphere response code
	 */
	public static int deleteDisk(Vm vm, Integer id) {
		String req = String.format("/vcenter/vm/%s/hardware/disk/%s", vm.getIdVmVcenter(), id);

		HttpResult hr = VSphereManager.requestVSphereAPI("DELETE", req);

		return hr.code;
	}

	/**
	 * Create a new ethernet interface on the virtual machine
	 *
	 * @param vm      the virtual machine to edit
	 * @param network the network to connect it to
	 * @return a table containing 0:the response code 1:the id of the created ethernet interface id
	 */
	public static int[] createEthernet(Vm vm, Network network) {
		String req = String.format("/vcenter/vm/%s/hardware/ethernet", vm.getIdVmVcenter());

		String sNetwork = network.getIdNetworkVcenter();

		JSONObject backing = new JSONObject();
		if (sNetwork.startsWith("dvportgroup"))
			backing.put("type", "DISTRIBUTED_PORTGROUP");
		else
			backing.put("type", "STANDARD_PORTGROUP");
		backing.put(NETWORK, sNetwork);
		JSONObject spec = new JSONObject();
		spec.put("start_connected", true);
		spec.put("type", "E1000");
		spec.put(BACKING, backing);
		JSONObject data = new JSONObject();
		data.put("spec", spec);

		HttpResult hr = VSphereManager.requestVSphereAPI("POST", req, data);
		return new int[]{hr.code, hr.code == 200 ? hr.getJSON().getInt(VALUE) : -1};
	}

	/**
	 * Change the ethernet interface
	 *
	 * @param vm      the virtual machine to edit
	 * @param nic     the interface id
	 * @param network the network to connect it to
	 * @return the VSphere response code
	 */
	public static int changeEthernetNetwork(Vm vm, int nic, Network network) {
		String req = String.format("/vcenter/vm/%s/hardware/ethernet/%s", vm.getIdVmVcenter(), nic);

		String sNetwork = network.getIdNetworkVcenter();

		JSONObject backing = new JSONObject();
		if (sNetwork.startsWith("dvportgroup"))
			backing.put("type", "DISTRIBUTED_PORTGROUP");
		else
			backing.put("type", "STANDARD_PORTGROUP");
		backing.put(NETWORK, sNetwork);
		JSONObject spec = new JSONObject();
		spec.put(BACKING, backing);
		JSONObject data = new JSONObject();
		data.put("spec", spec);

		HttpResult hr = VSphereManager.requestVSphereAPI("PATCH", req, data);
		return hr.code;
	}

	/**
	 * Delete the ethernet interface
	 *
	 * @param vm  the virtual machine to edit
	 * @param nic the interface id
	 * @return the VSphere response code
	 */
	public static int deleteEthernet(Vm vm, int nic) {
		String req = String.format("/vcenter/vm/%s/hardware/ethernet/%s", vm.getIdVmVcenter(), nic);

		HttpResult hr = VSphereManager.requestVSphereAPI("DELETE", req);
		return hr.code;
	}

	/**
	 * Connect the ethernet interface
	 *
	 * @param vm  the virtual machine to edit
	 * @param nic the interface id
	 * @return the VSphere response code
	 */
	public static int connectEthernet(Vm vm, int nic) {
		String req = String.format("/vcenter/vm/%s/hardware/ethernet/%s/connect", vm.getIdVmVcenter(), nic);

		HttpResult hr = VSphereManager.requestVSphereAPI("POST", req);
		return hr.code;
	}

	/**
	 * Disconnect the ethernet interface
	 *
	 * @param vm  the virtual machine to edit
	 * @param nic the interface id
	 * @return the VSphere response code
	 */
	public static int disconnectEthernet(Vm vm, int nic) {
		String req = String.format("/vcenter/vm/%s/hardware/ethernet/%s/disconnect", vm.getIdVmVcenter(), nic);

		HttpResult hr = VSphereManager.requestVSphereAPI("POST", req);
		return hr.code;
	}

	
	/**
	 * create a VM using the REST API
	 * @param datastore
	 * @param name
	 * @return
	 */
	public static String createVm(Datastore datastore, String name){
		return createVm("OTHER_64", datastore.getMOR().val, "group-v22", "resgroup-27", name );
	}
	
	/**
	 * create a VM using the REST API
	 * @return
	 */
	public static String createVm(String guestOS, String datastore, String folder, String resourcePool, String name) {
		String req = "/vcenter/vm";
		
		JSONObject data = new JSONObject();
		JSONObject spec = new JSONObject();
		JSONObject placement = new JSONObject();
		
		data.put("spec", spec);
		
		spec.put("placement", placement);
		spec.put("guest_OS", guestOS);
		spec.put("name", name);
		
		placement.put("datastore", datastore);
		placement.put("folder", folder);
		placement.put("resource_pool", resourcePool);
		
		String disksStr = "[{\"new_vmdk\":{\"capacity\":1,\"name\":\"disk0\"}}]";
		JSONArray disks = new JSONArray(disksStr);
		JSONObject vmdk = disks.getJSONObject(0).getJSONObject("new_vmdk");
		vmdk.put("capacity", (long) 1024*1024*1024*2);
		
		spec.put("disks", disks);
		
		HttpResult hr = VSphereManager.requestVSphereAPI("POST", req, data);
		
		if (hr.code != 200){
			Logger.log(Level.WARNING, "could not create VM");
			return null;
		}
		
		return hr.getJSON().getString(VALUE);
	}
}
