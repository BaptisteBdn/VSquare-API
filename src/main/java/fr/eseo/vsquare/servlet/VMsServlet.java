package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.model.*;
import fr.eseo.vsquare.model.EventLog.EventAction;
import fr.eseo.vsquare.utils.HttpUtils.HttpResult;
import fr.eseo.vsquare.utils.*;
import fr.eseo.vsquare.utils.VSphereConnector.VmPowerState;
import fr.klemek.betterlists.BetterArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Servlet implementation class VMsServlet.
 * 
 * This servlet manages every request made to /api/vm and process them.
 * 
 * @author Pierre P.
 */
@WebServlet("/vm/*")
public class VMsServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

    public VMsServlet() {
		super();
	}

	/**
	 * Service at /api/vm/*.
	 * 
	 * Redirect every http request to the right function.
	 */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) {
		try {
			final User user = ServletUtils.verifyToken(request, response);
			if (user == null)
				return;
			LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
			map.put("GET /api/vm", () -> getListVm(user, response));
            map.put("GET /api/vm/templates", () -> getTemplates(user, response));
			map.put("DELETE /api/vm/{}", () -> deleteVm(user, request, response));
			map.put("GET /api/vm/{}", () -> getDetailsVm(user, request, response));
			map.put("POST /api/vm/{}", () -> editVm(user, request, response));
			map.put("POST /api/vm/{}/disk", () -> createDisk(user, request, response));
			map.put("POST /api/vm/{}/disk/{}", () -> modifyDisk(user, request, response));
			map.put("DELETE /api/vm/{}/disk/{}", () -> deleteDisk(user, request, response));
			map.put("POST /api/vm/all/power/{}", () -> setPowerAll(user, request, response));
			map.put("POST /api/vm/{}/power/{}", () -> setPower(user, request, response));
			map.put("GET /api/vm/{}/snapshots", () -> getSnapshots(user, request, response));
			map.put("POST /api/vm/{}/snapshot", () -> createSnapshot(user, request, response));
			map.put("DELETE /api/vm/{}/snapshot/{}", () -> deleteSnapshot(user, request, response));
			map.put("POST /api/vm/{}/snapshot/{}/revert", () -> revertSnapshot(user, request, response));
			map.put("GET /api/vm/{}/console", () -> getConsoleURL(user, request, response));
			map.put("POST /api/vm/{}/clone", () -> cloneVm(user, request, response));
			map.put("POST /api/vm/{}/snapshot/{}/clone", () -> cloneSnapshot(user, request, response));
			map.put("GET /api/vm/{}/export/ova", () -> downloadOVA(user, request, response));
			map.put("POST /api/vm/{}/snapshot/{}", () -> editSnapshot(user, request, response));
			map.put("PUT /api/vm/{}/template", () -> createTemplate(user, request, response));
			map.put("PUT /api/vm/template/{}", () -> createVmFromTemp(user, request, response));
			map.put("DELETE /api/vm/template/{}", () -> deleteTemplate(user, request, response));
			map.put("PUT /api/vm/{}/ethernet", () -> createEthernet(user, request, response));
			map.put("POST /api/vm/{}/ethernet/{}", () -> changeEthernet(user, request, response));
			map.put("DELETE /api/vm/{}/ethernet/{}", () -> deleteEthernet(user, request, response));
			ServletUtils.mapRequest(request, response, map);
		} catch (Exception e) {
			Logger.log(Level.SEVERE, e.toString(), e);
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Returns a list of all the VM of the user associated with the current session (token).
	 * 
	 * See doc GET /api/vm.
	 * 
	 * @param user the user who made the request
	 * @param response the http response
	 */
	private void getListVm(User user, HttpServletResponse response) {
		BetterArrayList<Vm> vmList = BetterArrayList.fromList(user.getVms());

		JSONObject json = VSphereConnector.getVmList(vmList);

		ServletUtils.sendVSphereResponse(response, json);
	}

	/**
	 * Get all the details of the VM .
	 * 
	 * See doc GET /api/vm/{id}.
	 *
	 * @param user the user who made the request
	 * @param request the servlet request
	 * @param response the servlet response
	 */
	private void getDetailsVm(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, false);
		if (vm == null)
			return;

		JSONObject json = VSphereConnector.getVmDetails(vm);

		ServletUtils.sendVSphereResponse(response, json);
	}

	/**
	 * Delete the VM from the db and VSphere.
	 * 
	 * See doc DELETE /api/vm/{id}.
	 *
	 * @param user the user who made the request
	 * @param request the servlet request
	 * @param response the servlet response
	 */
	private void deleteVm(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);
		if (vm == null)
			return;

        EventLog.log(user, EventLog.EventAction.DELETE, vm);

		JSONObject json = VSphereConnector.deleteVm(vm);

		ServletUtils.sendVSphereResponse(response, json);
	}

	/**
	 * Edit the VM information.
	 * 
	 * Edit the name, description, number of cpus and memory size of the VM.
	 * Values for each field have a minimum set in the configuration files.
	 *
	 * @param user the user who made the request
	 * @param request the servlet request
	 * @param response the servlet response
	 */
	private void editVm(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);

		if (vm == null) {
			return;
		}

		String name = request.getParameter("name");
		String description = request.getParameter("desc");
		Integer cpu = Utils.stringToInteger(request.getParameter("cpu_count"));
		Integer ram = Utils.stringToInteger(request.getParameter("memory_size"));

		if (name != null) {
			if (!vm.valid(name)) {
				ServletUtils.sendError(response, 400, "Name length incorrect, must be [1,64]");
				return;
			}
			vm.setName(name);
		}

		if (description != null) {
			vm.setDescription(description);
		}
		
		if(!vm.isTemplate() && (cpu != null || ram != null)) {
			Permission perm = user.getEffectivePermission();
			Permission permMini = Permission.getMinimalPermission();

			if (updateVm(response, vm, new String[] { "size_MiB", "RAM" }, ram, permMini.getMemorySize(),
					perm.getMemorySize()))
				return;
			if (updateVm(response, vm, new String[] { "count", "CPU" }, cpu, permMini.getCpuCount(), perm.getCpuCount()))
				return;
		}

		if (vm.saveOrUpdate()) {
			EventLog.log(user, EventAction.EDIT, vm);
			ServletUtils.sendJSONResponse(response, vm.toJSON());
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Edition failed - server side problem");
			ErrorLog.log(user, "Vm '" + vm.getName() + "' : edition failed");
		}
	}

	/**
	 * Update the VM information in VSphere.
	 * 
	 * Uses the VSphereConnector to update the RAM or the CPU of the VM.
	 * 
	 * @param response the servlet http response
	 * @param vm the vm to update
	 * @param key ram or cpu update
	 * @param value value to update
	 * @param min value min to update
	 * @param max value max to update
	 * @return true if the update is successful, false otherwise
	 */
	private boolean updateVm(HttpServletResponse response, Vm vm, String[] key, Integer value, int min, int max) {
		if (value != null) {
			if (!checkVmState(response, vm)) {
				return true;
			}
			if (inThresholds(value, max, min)) {
				JSONObject json = new JSONObject();
				json.put(key[0], value);
				int result = 0;
				if (key[1].equals("RAM")) {
					result = VSphereConnector.updateRam(vm, new JSONObject().put("spec", json));
				} else if (key[1].equals("CPU")) {
					result = VSphereConnector.updateCpu(vm, new JSONObject().put("spec", json));
				}
				if (result != 200) {
					ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, "Server side error ");
					return true;
				}
			} else {
				ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
						String.format("%s value out of bounds (%s,%s)", key[1], min, max));
				return true;
			}
		}
		return false;
	}

	/**
	 * Return true if the VM is powered off, false otherwise.
	 * 
	 * @param response the servlet http response
	 * @param vm the vm to check the state of
	 * @return true if the VM is off, false otherwise.
	 */
    public boolean checkVmState(HttpServletResponse response, Vm vm) {
		VmPowerState currentState = VSphereConnector.getVmPowerState(vm);
		if (currentState != VmPowerState.POWERED_OFF) {
			ServletUtils.sendError(response, HttpServletResponse.SC_CONFLICT, "VM must be powered off");
			return false;
		}
		return true;
	}

	/**
	 * Return true if the value is in bounds, false otherwise.
	 * 
	 * @param i the Integer value to check
	 * @param max the upper bound
	 * @param min the lower bound
	 * @return true if the value is in bounds, false otherwise
	 */
	private boolean inThresholds(Integer i, Integer max, Integer min) {
		return i <= max && i >= min;
	}

	/**
	 * Create a new virtual disk on the VM.
	 * 
	 * See POST /api/vm/{id}/disk.
	 *
	 * @param user the user who made the request
	 * @param request the servlet request
	 * @param response the servlet response
	 */
	private void createDisk(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);

		if (vm == null)
			return;

		if (!checkVmState(response, vm)) {
			return;
		}

		if (!checkSataAdapter(response, vm)) {
			return;
		}

		Long diskCapacityTaken = getDisksCapacityTaken(response, vm);

		if (diskCapacityTaken == null) {
			return;
		}

		String name = Utils.coalesce(request.getParameter("name"), vm.getName() + "_template", "noName");
		Long capacity = Utils.stringToLong(request.getParameter(ServletUtils.CAPACITY_KEY));

		Permission perm = user.getEffectivePermission();
		Permission permMini = Permission.getMinimalPermission();

		JSONObject bodyDisk = new JSONObject();
		JSONObject newVdmk = new JSONObject();

		if (name != null) {
			if (!vm.valid(name)) {
				ServletUtils.sendError(response, 400, "Name length incorrect, must be [1,64]");
				return;
			}
			newVdmk.put("name", name);
		}

		if (capacity == null) {
			newVdmk.put(ServletUtils.CAPACITY_KEY, Utils.mibTob(permMini.getDiskStorage()));
		} else {
			if (capacity >= Utils.mibTob(permMini.getDiskStorage())
					&& capacity + diskCapacityTaken <= Utils.mibTob(perm.getDiskStorage()))
				newVdmk.put(ServletUtils.CAPACITY_KEY, capacity);
			else {
				ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
						String.format("Disk value out of bounds, value should be within (%s,%s) MiB", permMini.getDiskStorage(),
								perm.getDiskStorage() - Utils.bToMib(diskCapacityTaken)));
				return;
			}
		}

		bodyDisk.put("new_vmdk", newVdmk);

		int result = VSphereConnector.createSataDisk(vm, new JSONObject().put("spec", bodyDisk));
		if (result != 200) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, "Create sata disk failed, server error");
			return;
		}

        EventLog.log(user, EventLog.EventAction.EDIT, vm);

		ServletUtils.sendOK(response);
	}

	/**
	 * Get the disk storage used, null if there are already two disks.
	 * 
	 * @param response the servlet response
	 * @param vm the VM to check capacity
	 * @return the value of the storage used in bytes, else null
	 */
    public Long getDisksCapacityTaken(HttpServletResponse response, Vm vm) {
		JSONObject detailsVm = VSphereConnector.getVmDetails(vm);

		if (detailsVm == null || detailsVm.equals(VSphereConnector.JSON_NOT_FOUND)) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, "Get vm detail failed, server side error");
			return null;
		}

		JSONArray disks = (JSONArray) ((JSONObject) detailsVm.get(ServletUtils.VALUE_KEY)).get(ServletUtils.DISKS_KEY);

		if (disks.length() > Utils.getInt("default_disk_count")) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
					"Disks number limited to " + Utils.getInt("default_disk_count"));
			return null;
		}

		Long diskCapacityTaken = (long) 0;

		for (Object disk : disks) {
			diskCapacityTaken += ((JSONObject) ((JSONObject) disk).get(ServletUtils.VALUE_KEY))
					.getLong(ServletUtils.CAPACITY_KEY);
		}

		return diskCapacityTaken;
	}

	/**
	 * Compare the disks value and check if the new capacity is correct, in KiB.
	 * 
	 * @param response the servlet response
	 * @param vm the vm to change the value
	 * @param newCapacity the new capacity to set 
	 * @param labelDisk the name of the disk 
	 * @param maxCapacity the maximum capacity possible
	 * @return true if the new capacity is correct, else false
	 */
    public boolean compareDisksValue(HttpServletResponse response, Vm vm, Long newCapacity, String labelDisk,
                                     Long maxCapacity) {
		JSONObject detailsVm = VSphereConnector.getVmDetails(vm);
		JSONArray disks = (JSONArray) ((JSONObject) detailsVm.get(ServletUtils.VALUE_KEY)).get(ServletUtils.DISKS_KEY);

		Long diskCapacityTaken = (long) 0;
		Long oldDiskCapacity = (long) 0;

		for (Object disk : disks) {
			JSONObject value = ((JSONObject) ((JSONObject) disk).get(ServletUtils.VALUE_KEY));
			if (value.get("label").equals(labelDisk)) {
				oldDiskCapacity = value.getLong(ServletUtils.CAPACITY_KEY);
			}
			diskCapacityTaken += value.getLong(ServletUtils.CAPACITY_KEY);
		}

		if (oldDiskCapacity == 0) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Disk do not exist");
			return false;
		}

		if (diskCapacityTaken == 0) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, "Disks empty");
			return false;
		}

		Long finalCapacity = diskCapacityTaken - oldDiskCapacity + newCapacity;

		if (finalCapacity <= maxCapacity && finalCapacity >= oldDiskCapacity) {
			return true;
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
					String.format("Disk value out of bounds, value should be within (%s,%s) MiB", Utils.bToMib(oldDiskCapacity),
							Utils.bToMib(maxCapacity - diskCapacityTaken + oldDiskCapacity)));
			return false;
		}
	}

	/**
	 * Check if there is a sata adapter, create one if not.
	 * 
	 * @param response the servlet response
	 * @param vm the VM of the user
	 * @return true if there is a sata adapter, false if not
	 */
    public boolean checkSataAdapter(HttpServletResponse response, Vm vm) {
		JSONObject json = VSphereConnector.getSataAdapter(vm);
		if (json == null || json.equals(VSphereConnector.JSON_NOT_FOUND)) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, "Get sata adapter failed, server error");
			return false;
		}
		JSONArray value = json.getJSONArray(ServletUtils.VALUE_KEY);
		if (value.length() == 0) {
			JSONObject bodyAdapter = new JSONObject();
			bodyAdapter.put("type", "AHCI");
			int resultCreateAdapter = VSphereConnector.createSataAdapter(vm,
					new JSONObject().put("spec", bodyAdapter));
			if (resultCreateAdapter != 200) {
				ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, "Create sata adapter failed, server error");
				return false;
			}
		}
		return true;
	}

	/**
	 * Delete the disk from the VM.
	 * 
	 * See doc DELETE /api/vm/{id}/disk/{{disk-id}.
	 *
	 * @param user the user who made the request
	 * @param request the servlet request
	 * @param response the servlet http response
	 */
	private void deleteDisk(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);
		if (vm == null) {
			return;
		}

		if (!checkVmState(response, vm)) {
			return;
		}

		String[] path = request.getRequestURI().split("/");
		Integer diskId = Utils.stringToInteger(path[5]);
		String label = getDiskLabel(vm,diskId);

		if (diskId == null || label == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		if (VCenterManager.deleteDisk(vm, label)) {
            EventLog.log(user, EventLog.EventAction.EDIT, vm);
			ServletUtils.sendOK(response);
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Delete disk failed, server error");
		}
	}

	/**
	 * Return the label of the disk of the VM.
	 *
	 * @param vm the vm connected to the disk
	 * @param diskId the Id of the disk in VSphere
	 * @return the label of the disk
	 */
    public String getDiskLabel(Vm vm, Integer diskId) {
		JSONObject detailsVm = VSphereConnector.getVmDetails(vm);
		JSONArray disks = (JSONArray) ((JSONObject) detailsVm.get(ServletUtils.VALUE_KEY)).get(ServletUtils.DISKS_KEY);

		for (Object disk : disks) {
			JSONObject value = ((JSONObject) ((JSONObject) disk).get(ServletUtils.VALUE_KEY));
			Integer key = ((JSONObject) disk).getInt("key");
			if (key.equals(diskId)) {
				return value.getString("label");
			}
		}
		return null;
	}

	/**
	 * Edit the disk capacity.
	 * See doc POST /api/vm/{id}/disk/{disk-id}.
	 * 
	 * @param user the user who made the request 
	 * @param request the servlet http request
	 * @param response the servlet http response
	 */
	private void modifyDisk(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);
		if (vm == null) {
			return;
		}

		if (!checkVmState(response, vm)) {
			return;
		}

		String[] path = request.getRequestURI().split("/");
		Integer diskId = Utils.stringToInteger(path[5]);
		Long capacity = Utils.stringToLong(request.getParameter("capacity"));
		String label = getDiskLabel(vm,diskId);

		if (diskId == null || label == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		if (capacity == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Bad parameters");
			return;
		}

		Permission perm = user.getEffectivePermission();

		if (!compareDisksValue(response, vm, capacity, label, Utils.mibTob(perm.getDiskStorage()))) {
			return;
		}

		if (VCenterManager.modifyDisk(vm, label, Utils.bToKib(capacity))) {
            EventLog.log(user, EventLog.EventAction.EDIT, vm);
			ServletUtils.sendOK(response);
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Modify disk failed, server error");
		}
	}

	/**
	 * Set the power of the VM depending of the request.
	 * 
	 * Takes in parametre the http request and set the power for the VM.
	 *
	 * @param user the user who made the request
	 * @param request the servlet request
	 * @param response the servlet response
	 */
	private void setPower(User user, HttpServletRequest request, HttpServletResponse response) {
		String[] path = request.getRequestURI().split("/");
		String action = path[5];

		if (path[3].equals("all")) {
			setPowerAll(user, request, response);
			return;
		}

		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);

		if (vm == null)
			return;

		if (!Arrays.asList("start;reset;stop;suspend".split(";")).contains(action)) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Bad action");
			return;
		}

		if (setPower(user, response, action, vm)) {
			ServletUtils.sendOK(response);
		}

	}

	/**
	 * Set the power state of the VM.
	 * 
	 * Set the power state of the VM among OFF, ON and SUSPENDED.
	 * 
	 * @param user the user who made the request
	 * @param response the http response servlet
	 * @param action the power action to execute
	 * @param vm the VM to change the power of
	 * @return true if the power have been modified, false otherwise
	 */
	private boolean setPower(User user, HttpServletResponse response, String action, Vm vm) {
		HttpResult result = VSphereConnector.setVmPower(vm, action);

		if (result == null) {
			Logger.log(Level.SEVERE, "SetVmPower returned null");
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return false;
		}

		if (result.code == 200) {
			EventLog.log(user, EventLog.getActionFromVmPower(action), vm);
			return true;
		}
		if (result.code != 400) {
			Logger.log(Level.WARNING, "VSphere returned an error code : " + result.code);
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY,
					"VSphere returned code " + result.code);
			return false;
		}
		VmPowerState currentState = VSphereConnector.getVmPowerState(vm);
		if (VSphereConnector.checkVmPowerChange(action, currentState)) {
			return true;
		} else {
			if (VSphereConnector.setVmPowerPossible(action, currentState)) {
				ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
						"Action not possible on the current state of the vm");
				return false;
			} else {
				ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY,
						"VSphere returned code " + result.code);
				return false;
			}

		}
	}

	/**
	 * Set the power of all the VMs the admin has access to.
	 *
	 * @param user the user who made the request
	 * @param request the servlet request
	 * @param response the servlet response
	 */
	private void setPowerAll(User user, HttpServletRequest request, HttpServletResponse response) {
		if (!user.isAdmin()) {
			Logger.log(Level.INFO, "User tried to access setPowerAll while not being admin : " + user.getCommonName());
			ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		String[] path = request.getRequestURI().split("/");
		String action = path[5];

		if (!Arrays.asList("start;reset;stop;suspend".split(";")).contains(action)) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Bad action");
			return;
		}

		List<Vm> vms = Vm.getAll();
        int vmAffected = VSphereConnector.setVmPowerAll(user, action, vms);

		JSONObject output = new JSONObject();
		output.put("total_vms", vms.size());
		output.put("total_success", vmAffected);

		EventLog.log(user, EventLog.getActionFromVmPower(action), user); // using self to indicate all vms

		ServletUtils.sendJSONResponse(response, output);
	}


    /**
	 * Return the snapshots associated with the VM.
	 * 
	 * @param user the user who made the request
	 * @param request the servlet http request
	 * @param response the servlet http response
	 * 
	 */
	private void getSnapshots(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, false);
		if (vm == null)
			return;

		List<Snapshot> snaps = Snapshot.getSnapshotsForVm(vm);
		JSONArray output = new JSONArray();
		for (Snapshot snap:snaps){
			output.put(snap.toJSON(true));
		}

		ServletUtils.sendJSONResponse(response, output);
	}

	/**
	 * Create a snapshot of the VM.
	 * 
	 * Create a snapshot for the VM passed in arguments in the htt request, 
	 * using the name and description passed in the request.
	 * 
	 * @param user the user who made the request
	 * @param request the servlet http request
	 * @param response the servlet http response
	 */
	private void createSnapshot(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);
		if (vm == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		String name = request.getParameter("name");
		String description = request.getParameter("description");

		if (name == null || description == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Bad parameters");
			return;
		}

		VCenterManager.checkConnection();

		String idSnapshot = VCenterManager.createSnapshot(vm, name, description);
		if (idSnapshot != null) {
			Snapshot snap = new Snapshot(vm, idSnapshot, name, description);
			snap.saveOrUpdate();
			JSONObject output = new JSONObject();
			output.put(ServletUtils.VALUE_KEY, snap.toJSON(true));
            EventLog.log(user, EventLog.EventAction.EDIT, vm);
			ServletUtils.sendJSONResponse(response, output);
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Edit the snapshot information.
	 * 
	 * @param user the user who made the request
	 * @param request the http servlet request
	 * @param response the http servlet response
	 */
	private void editSnapshot(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);
		String[] path = request.getRequestURI().split("/");
		Integer idSnapshot = Utils.stringToInteger(path[5]);

		if (idSnapshot==null){
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Snapshot not f0und");
			return;
		}

		Snapshot snapshot = Snapshot.findById(idSnapshot);


		if (vm == null || snapshot == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		if (! snapshot.getVm().equals(vm)){
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "snapshot not corresponding to vm");
			return;
		}

		String name = request.getParameter("name");
		String description = request.getParameter("description");

		if (name == null && description == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "missing parameter : name or description");
			return;
		}

		if (name != null){
			snapshot.setName(name);
		}
		if (description != null){
			snapshot.setDescription(description);
		}

		if (snapshot.saveOrUpdate()){
			JSONObject output = new JSONObject();
			output.put(ServletUtils.VALUE_KEY, snapshot.toJSON(true));
            EventLog.log(user, EventLog.EventAction.EDIT, vm);
			ServletUtils.sendJSONResponse(response, output);
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Delete a snaphsot from VSphere.
	 * 
	 * @param user the user who made the request
	 * @param request the servlet http servlet request 
	 * @param response the servlet http response
	 */
	private void deleteSnapshot(User user, HttpServletRequest request, HttpServletResponse response) {
		String[] path = request.getRequestURI().split("/");
		Integer idSnapshot = Utils.stringToInteger(path[5]);


		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);
		if (vm == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		if (idSnapshot==null){
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Snapshot not found");
			return;
		}

		Snapshot snapshot = Snapshot.findById(idSnapshot);

		if (snapshot == null ||! snapshot.getVm().equals(vm)) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Snapshot not on base / snapshot not from this VM");
			return;
		}

		VCenterManager.checkConnection();

		if (VCenterManager.deleteSnapshot(snapshot) && snapshot.delete()) {
            EventLog.log(user, EventLog.EventAction.EDIT, vm);
			ServletUtils.sendOK(response);
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Revert the VM to the state of the snapshot.
	 * 
	 * @param user the user who made the request
	 * @param request the servlet http request
	 * @param response the servlet http response
	 */
	private void revertSnapshot(User user, HttpServletRequest request, HttpServletResponse response) {
		String[] path = request.getRequestURI().split("/");
		Integer idSnapshot = Utils.stringToInteger(path[5]);

		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);

		if (idSnapshot==null){
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Snapshot not found");
			return;
		}

		Snapshot snapshot = Snapshot.findById(idSnapshot);

		if (snapshot == null || !snapshot.getVm().equals(vm)) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Snapshot not found /  not from this VM");
			return;
		}

		VCenterManager.checkConnection();

		if (VCenterManager.revertSnapshot(snapshot)) {
            EventLog.log(user, EventLog.EventAction.EDIT, vm);
			ServletUtils.sendOK(response);
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Return the URL for the web console.
	 * 
	 * Return the url of the remote console created, 
	 * in order for the client to connect to it.
	 * 
	 * @param user the user who made the request
	 * @param request the servlet http request
	 * @param response the servlet http response
	 */
	private void getConsoleURL(User user, HttpServletRequest request, HttpServletResponse response) {
		String[] path = request.getRequestURI().split("/");
		String vmIdVCenter = path[3];

		Vm vm = Vm.findByIdVmVcenter(vmIdVCenter);
		if (vm == null || vm.getIdVmVcenter() == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		if (!vm.hasAccessRead(user)) {
			ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN,
					"You do not possess reading rights on this VM");
			return;
		}

		VCenterManager.checkConnection();

		String url = VCenterManager.getRemoteConsoleUrl(vm);

		if (url == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"You cannot get access to this vm's remote console (perhaps it isn't powered on)");
			return;
		}

		JSONObject res = new JSONObject();
		res.put(ServletUtils.VALUE_KEY, url);

		ServletUtils.sendJSONResponse(response, res);
	}

	/**
	 * Clone the VM in VSphere.
	 * 
	 * Make a copy of the VM in VSphere with the name and description 
	 * from the request.
	 * 
	 * @param user the user who made the request
	 * @param request the servlet http request
	 * @param response the servlet http response
	 */
	private void cloneVm(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);

		if (vm == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		if (!vm.hasAccessWrite(user)) {
			ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		VCenterManager.checkConnection();

		String name = Utils.coalesce(request.getParameter("name"), vm.getName(), "no Name");
		String description = Utils.coalesce(request.getParameter("desc"), vm.getDescription(), "");

		String newVmId;
		try {
			newVmId = VCenterManager.cloneVm(vm, name, false);
			if (newVmId == null) {
				ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				return;
			}
		} catch (IOException | InterruptedException e) {
			Logger.log(Level.WARNING, "clone VM failed");
			return;
		}
		
		Vm newVm = new Vm(user, newVmId, name, description);

		if (newVm.saveOrUpdate()) {
            EventLog.log(user, EventAction.CLONE, vm);
			ServletUtils.sendJSONResponse(response, VSphereConnector.getVmDetails(newVm));
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Clone failed - server side problem");
			ErrorLog.log(user, "Vm '" + vm.getName() + "' : clone failed");
		}
	}

	/**
	 * Download an OVA file to the server.
	 * 
	 * @param user the user who made the request
	 * @param request the servlet http request
	 * @param response the servlet http response
	 */
	private void downloadOVA(User user, HttpServletRequest request, HttpServletResponse response){
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);

		if (vm == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_NOT_FOUND, "VM not found");
			return;
		}

		if (!vm.hasAccessWrite(user)) {
			ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN, "no write access");
			return;
		}

		VCenterManager.checkConnection();

		if (! VSphereConnector.getVmPowerState(vm).equals(VmPowerState.POWERED_OFF)){
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "you must turn off the vm");
			return;
		}

		//verify if an export has already been done on this VM and if so delete all previous config
		DownloadLink link = DownloadLink.findByVm(vm);
		if (link != null){//delete previous link, who shouldn't work anymore
			try {
				Files.deleteIfExists(link.getInternalLinkAsFile().toPath());
			} catch (IOException e) {
				Logger.log(Level.WARNING, "could not delete file : {0}", link.getInternalLink());//don't stop the program for this error
			}
			link.delete();
		}

		//do the export (vsquare -> vsphere)
		File file = null;
		try{
			file = VCenterManager.downloadAndPackageOVA(vm);
		}catch(IOException e){
			Logger.log(Level.WARNING, "error when downloading OVA package");
		}
		if (file==null){
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "file saving error - file was not saved");
			return;
		}

		link = new DownloadLink(file.getAbsolutePath(), vm);
		if (! link.saveOrUpdate()){
			Logger.log(Level.WARNING, "could not create new DownloadLink");
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "DownloadLink error - could not create the download link");
			return;
		}
		
		String url = Utils.getString("base_url_download");
		if (url == null){
			Logger.log(Level.WARNING, "Utils configuraton fetch error - could not fetch the base_url_download value");
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"could not create download url");
			return;
		}

		url+=link.getExternalLink();

		JSONObject output = new JSONObject();
		JSONObject value = new JSONObject();
		value.put("url", url);
		value.put("external_link", link.getExternalLink());
		output.put(ServletUtils.VALUE_KEY, value);

        EventLog.log(user, EventLog.EventAction.EXPORT, vm);

		ServletUtils.sendJSONResponse(response, output);
	}

	/**
	 * Clone the snapshot of the VM.
	 * 
	 * @param user the user who made the request
	 * @param request the servlet http request
	 * @param response the servlet http response
	 */
	private void cloneSnapshot(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);
		String[] path = request.getRequestURI().split("/");
		Integer idSnapshot = Utils.stringToInteger(path[5]);
		if (vm == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		if (!vm.hasAccessWrite(user)) {
			ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		if (idSnapshot == null){
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		Snapshot snapshot = Snapshot.findById(idSnapshot);

		String name = Utils.coalesce(request.getParameter("name"), vm.getName()+"- snapshot clone", "noName");
		String description = Utils.coalesce(request.getParameter("desc"), vm.getDescription(), "");

		if (snapshot == null || snapshot.getIdSnapshotVcenter() == null){
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "no snapshot specified / snapshot not on server");
			return;
		}

		VCenterManager.checkConnection();

		String newVmId;
		try {
			newVmId = VCenterManager.vmFromSnapshot(snapshot, name);
		} catch (IOException | InterruptedException e) {
			Logger.log(Level.WARNING, "clone snapshot raised an error : {0}",e);
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Clone snapshot failed - server side problem");
			return;
		}

		Vm newVm = new Vm(user, newVmId, name, description);

		if (newVm.saveOrUpdate()) {
            EventLog.log(user, EventAction.CLONE, vm);
			ServletUtils.sendJSONResponse(response, VSphereConnector.getVmDetails(newVm));
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Clone snpashot succesfull - db error - server side problem");
			ErrorLog.log(user, "Vm '" + vm.getName() + "' : clone created but could not be saved");
		}
	}

	/**
	 * Create a template.
	 * 
	 * Create a new template that will be used to create new VMs based on it.
	 * 
	 * @param user
	 *            the user who made the request
	 * @param request
	 *            the servlet http request
	 * @param response
	 *            the servlet http response
	 */
	private void createTemplate(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);

		if (!user.isAdmin()) {
			ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN, "Only an admin can create templates");
			return;
		}

		VCenterManager.checkConnection();
		
		Map<String, String> params = ServletUtils.readParameters(request);
		String name = Utils.coalesce(params.get("name"), vm.getName() + "_template", "noName");
		String description = Utils.coalesce(params.get("desc"), vm.getDescription(), "");

		String newVmId;
		try {
			newVmId = VCenterManager.cloneVm(vm, name, true);
		} catch (IOException | InterruptedException e) {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Template failed");
			return;
		}
		
		if(newVmId == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Template failed - server side problem");
			return;
		}

		Vm newVm = new Vm(user, newVmId, name, description);
		newVm.setTemplate(true);

		if (newVm.saveOrUpdate()) {
            EventLog.log(user, EventAction.CREATE, newVm);
			ServletUtils.sendJSONResponse(response, VSphereConnector.getVmDetails(newVm));
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Template failed - server side problem");
			ErrorLog.log(user, "Vm '" + vm.getName() + "' : template failed");
		}
	}

	/**
	 * Delete the template.
	 *
	 * @param user
	 *            the user who made the request
	 * @param request
	 *            the servlet http request
	 * @param response
	 *            the servlet http response
	 */
    private void deleteTemplate(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 4, user, true);

		if (!user.isAdmin()) {
			ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN, "Only an admin can delete templates");
			return;
		}

		VCenterManager.checkConnection();

        EventLog.log(user, EventLog.EventAction.DELETE, vm);

		if (!vm.delete()) {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Delete vm from db failed, server error");
			return;
		}
		
		try {
			if (VCenterManager.deleteVm(vm)) {
				ServletUtils.sendOK(response);
			} else {
				ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						"Delete vm failed, server error");
			}
		} catch (RemoteException | InterruptedException e) {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Template failed - server side problem");
			ErrorLog.log(user, "Vm '" + vm.getName() + "' : template failed", e.getMessage());
		}
	}

	/**
	 * Get the templates.
	 *
	 * @param user
	 *            the user who made the request
	 * @param response
	 *            the servlet http response
	 */
    private void getTemplates(User user, HttpServletResponse response) {
		List<Vm> templateList;
		
		if(user.isAdmin()) {
			templateList = Vm.getAllTemplate();
		} else {
			templateList = BetterArrayList.fromList(user.getAvailableTemplates());
		}
		
		JSONObject json = VSphereConnector.getTemplateList(templateList);
		
		if(json == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Template list failed, minimal detail failed - server side problem");
			return;
		}
				
		ServletUtils.sendVSphereResponse(response, json);
	}
	
	/**
	 * Create a vm from a template.
	 *
	 * @param user
	 *            the user who made the request
	 * @param request
	 *            the servlet http request
	 * @param response
	 *            the servlet http response
	 */
    private void createVmFromTemp(User user, HttpServletRequest request, HttpServletResponse response) {
        String[] path = request.getRequestURI().split("/");
        String vmIdVCenter = path[4];
        Vm vm = Vm.findByIdVmVcenter(vmIdVCenter);
        
		if(!BetterArrayList.fromList(user.getAvailableTemplates()).contains(vm) && !user.isAdmin()) {
			ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN, "You don't have the access to this template");
			return;
		}

		VCenterManager.checkConnection();

		Map<String, String> params = ServletUtils.readParameters(request);
		String name = Utils.coalesce(params.get("name"), vm.getName() + "_CloneTemplate", "noName");
		String description = Utils.coalesce(params.get("desc"), vm.getDescription(), "");

		String newVmId;
		try {
			newVmId = VCenterManager.cloneTemplate(vm, name);
		} catch (IOException | InterruptedException e) {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Template failed - server side problem");
			Logger.log(Level.SEVERE, "Vm from template failed");
			return;
		}
		
		if(newVmId == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Template failed - server side problem");
			return;
		}


		Vm newVm = new Vm(user, newVmId, name, description);

		if (newVm.saveOrUpdate()) {
            EventLog.log(user, EventAction.CREATE, newVm);
			ServletUtils.sendJSONResponse(response, VSphereConnector.getVmDetails(newVm));
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"vm from template  failed - server side problem");
			ErrorLog.log(user, "Vm '" + vm.getName() + "' : vm from template failed");
		}
	}

	/**
	 * PUT /api/vm/{}/ethernet
	 *
	 * @param user     the user who made the request
	 * @param request  the servlet http request
	 * @param response the servlet http response
	 */
	private void createEthernet(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);
		if (vm == null)
			return;

		Map<String, String> params = ServletUtils.readParameters(request);
		String sNetwork = params.getOrDefault("network", "");
		Network network;

		if (sNetwork.equals("local")) {
			network = getUserPrivateNetwork(user);
			if (network.getIdNetworkVcenter() == null) {
				ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No network were found");
				return;
			}
		} else {
			network = ServletUtils.getVSquareObject(response, Utils.stringToInteger(sNetwork), true, Network.class);
		}

		if (network == null)
			return;

		int[] res = VSphereConnector.createEthernet(vm, network);

		if (res[0] == 200) {
            EventLog.log(user, EventLog.EventAction.EDIT, vm);
			ServletUtils.sendOK(response);
		} else {
			Logger.log(Level.WARNING, "VSphere returned code {0} when creating ethernet interface", res[0]);
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, "Cannot create ethernet interface");
		}
	}

	/**
	 * POST /api/vm/{}/ethernet/{}
	 *
	 * @param user     the user who made the request
	 * @param request  the servlet http request
	 * @param response the servlet http response
	 */
	private void changeEthernet(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);
		if (vm == null)
			return;

		String[] path = request.getRequestURI().split("/");
		Integer nic = Utils.stringToInteger(path[5]);

		if (nic == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid nic");
			return;
		}

		Network network;
		String sNetwork = request.getParameter("network");

		if (sNetwork != null && sNetwork.equals("local")) {
			network = getUserPrivateNetwork(user);
			if (network.getIdNetworkVcenter() == null) {
				ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No network were found");
				return;
			}
		} else {
			network = ServletUtils.getObjectFromRequest(request, response, "network", false, Network.class);
		}

		if (network != null) {
			int res = VSphereConnector.changeEthernetNetwork(vm, nic, network);
			if (res != 200) {
				Logger.log(Level.WARNING, "VSphere returned code {0} when changing ethernet interface", res);
				ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, "Cannot change ethernet interface");
				return;
			}
		}

		String connected = request.getParameter("connected");
		if (connected != null) {
			int res = connected.equalsIgnoreCase("true") ?
					VSphereConnector.connectEthernet(vm, nic) :
					VSphereConnector.disconnectEthernet(vm, nic);
            if (res % 200 != 0) { // 200 and 400
				Logger.log(Level.WARNING, "VSphere returned code {0} when changing connected state of ethernet interface", res);
				ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, "Cannot change connection of ethernet interface");
				return;
			}
		}

        EventLog.log(user, EventLog.EventAction.EDIT, vm);

        ServletUtils.sendOK(response);
	}

	/**
	 * DELETE /api/vm/{}/ethernet/{}
	 *
	 * @param user     the user who made the request
	 * @param request  the servlet http request
	 * @param response the servlet http response
	 */
	private void deleteEthernet(User user, HttpServletRequest request, HttpServletResponse response) {
		Vm vm = ServletUtils.getVmFromRequest(request, response, 3, user, true);
		if (vm == null)
			return;

		String[] path = request.getRequestURI().split("/");
		Integer nic = Utils.stringToInteger(path[5]);

		if (nic == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid nic");
			return;
		}

		int res = VSphereConnector.deleteEthernet(vm, nic);

		if (res == 200) {
            EventLog.log(user, EventLog.EventAction.EDIT, vm);
			ServletUtils.sendOK(response);
		} else {
			Logger.log(Level.WARNING, "VSphere returned code {0} when deleting ethernet interface", res);
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, "Cannot delete ethernet interface");
		}
	}

	/**
	 * Get or create the user private network
	 *
	 * @param user the current user
	 * @return a network object containing the vcenter network id
	 */
	private Network getUserPrivateNetwork(User user) {
		if (user.getPrivateNetwork() == null) {
			String netId = VCenterManager.createNetwork(Utils.getString("vsphere_private_dswitch"),
					"Private network of " + user.getCommonName(),
					user.getEffectivePermission().getVmCount(),
					true);
			user.setPrivateNetwork(netId);
			user.saveOrUpdate();
		}
		return new Network(null, user.getPrivateNetwork());
	}

}
