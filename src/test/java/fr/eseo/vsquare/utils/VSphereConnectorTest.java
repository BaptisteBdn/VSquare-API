package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.Group;
import fr.eseo.vsquare.model.Network;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.Vm;
import fr.eseo.vsquare.utils.HttpUtils.HttpResult;
import fr.eseo.vsquare.utils.VSphereConnector.VmPowerAction;
import fr.eseo.vsquare.utils.VSphereConnector.VmPowerState;
import fr.klemek.betterlists.BetterArrayList;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.vmware.vim25.VirtualMachineGuestOsIdentifier;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "java.*", "javax.*", "org.*", "fr.eseo.vsquare.model.*", "fr.eseo.vsquare.utils.DatabaseManager", "fr.eseo.vsquare.utils.VCenterManager" })
@PrepareForTest(VSphereManager.class)
public class VSphereConnectorTest {

	private Vm vmTest;

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(false);

		assertTrue(VCenterManager.init());
		vmTest = new Vm(null, "vmTest", "vm test", "Vm used for tests");
	}
	
	@Test
	public void testGetVmPowerFail() {
		vmTest.setIdVmVcenter(null);
		HttpResult res = VSphereConnector.getVmPower(vmTest);
		assertNull(res);
	}

	@Test
	public void testGetVmPowerStateFail() {
		JSONObject result = new JSONObject();
		JSONObject value = new JSONObject();
		value.put("state", "BAD_STATE");
		result.put("value", value);
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vmTest.getIdVmVcenter() + "/power", 200, result);
		assertNull(VSphereConnector.getVmPowerState(vmTest));

		result = new JSONObject();
		value = new JSONObject();
		value.put("bad_link", "start");
		result.put("value", value);
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vmTest.getIdVmVcenter() + "/power", 200, result);
		assertNull(VSphereConnector.getVmPowerState(vmTest));

		vmTest.setIdVmVcenter(null);
		VmPowerState res = VSphereConnector.getVmPowerState(vmTest);
		assertNull(res);
	}

	@Test
	public void testGetVmPowerSuccess() {
		JSONObject result = new JSONObject();
		JSONObject value = new JSONObject();
		value.put("state", "POWERED_ON");
		result.put("value", value);
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vmTest.getIdVmVcenter() + "/power", 200, result);

		HttpResult res = VSphereConnector.getVmPower(vmTest);
		assertNotNull(res);

		assertEquals(res.result, 200, res.code);
		JSONObject json = res.getJSON();
		assertTrue(json.has("value"));
		assertTrue(json.getJSONObject("value").has("state"));
	}

	@Test
	public void testGetVmPowerState() {
		JSONObject result = new JSONObject();
		JSONObject value = new JSONObject();
		value.put("state", "POWERED_ON");
		result.put("value", value);
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vmTest.getIdVmVcenter() + "/power", 200, result);

		VmPowerState res = VSphereConnector.getVmPowerState(vmTest);
		assertEquals(VSphereConnector.VmPowerState.POWERED_ON, res);
	}

	@Test
	public void testSetVmPowerWithVmPowerAction() {
		VmPowerAction action = VmPowerAction.START;
		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vmTest.getIdVmVcenter() + "/power/start", 200,
				emptyResult);

		HttpResult result = VSphereConnector.setVmPower(vmTest, action);
		assertEquals(200, result.code);
	}

	@Test
	public void testSetVmPowerBadMethod() {
		HttpResult result = VSphereConnector.setVmPower(vmTest, "bad method");
		assertNull(result);
	}

	@Test
	public void testSetVmPowerBadVM() {
		HttpResult result = VSphereConnector.setVmPower(null, "suspend");
		assertNull(result);
	}

	@Test
	public void testSetVmPowerStart() {
		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vmTest.getIdVmVcenter() + "/power/start", 200,
				emptyResult);
		HttpResult res = VSphereConnector.setVmPowerStart(vmTest);

		assertNotNull(res);
		assertEquals(200, res.code);
	}

	@Test
	public void testSetVmPowerReset() {
		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vmTest.getIdVmVcenter() + "/power/reset", 200,
				emptyResult);
		HttpResult res = VSphereConnector.setVmPowerReset(vmTest);

		assertNotNull(res);
		assertEquals(200, res.code);
	}

	@Test
	public void testSetVmPowerStop() {
		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vmTest.getIdVmVcenter() + "/power/stop", 200,
				emptyResult);
		HttpResult res = VSphereConnector.setVmPowerStop(vmTest);

		assertNotNull(res);
		assertEquals(200, res.code);
	}

	@Test
	public void testSetVmPowerSuspend() {
		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vmTest.getIdVmVcenter() + "/power/suspend", 200,
				emptyResult);
		HttpResult res = VSphereConnector.setVmPowerSuspend(vmTest);

		assertNotNull(res);
		assertEquals(200, res.code);
	}

	@Test
	public void testCheckVmIdError() {
		assertFalse(VSphereConnector.checkVmId(null));
		Vm vm = new Vm();
		assertFalse(VSphereConnector.checkVmId(vm));
	}

	@Test
	public void testCheckVmIdSuccess() {
		Vm vm = new Vm();
		vm.setIdVmVcenter("vm459-test");
		assertTrue(VSphereConnector.checkVmId(vm));
	}

	@Test
	public void testCheckVmPowerChangeWithoutRecall() {
		HttpResult result = new HttpResult(200, "", null);
		assertFalse(VSphereConnector.checkVmPowerChange(vmTest, "start", null));
		assertTrue(VSphereConnector.checkVmPowerChange(vmTest, "start", result));
		result = new HttpResult(406, "", null);
		assertFalse(VSphereConnector.checkVmPowerChange(vmTest, "start", result));
	}

	@Test
	public void testCheckVmPowerChangeWithRecall() {
		JSONObject result = new JSONObject();
		JSONObject value = new JSONObject();
		value.put("state", "POWERED_ON");
		result.put("value", value);
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vmTest.getIdVmVcenter() + "/power", 200, result);

		HttpResult httpResult = new HttpResult(400, "", null);
		assertTrue(VSphereConnector.checkVmPowerChange(vmTest, "start", httpResult));

		result = new JSONObject();
		value = new JSONObject();
		value.put("state", "BAD_STATE");
		result.put("value", value);
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vmTest.getIdVmVcenter() + "/power", 200, result);

		assertFalse(VSphereConnector.checkVmPowerChange(vmTest, "start", httpResult));
	}

	@Test
	public void testCheckVmPowerChangeVMSuccess() {
		assertTrue(VSphereConnector.checkVmPowerChange("start", VmPowerState.POWERED_ON));
		assertTrue(VSphereConnector.checkVmPowerChange("reset", VmPowerState.POWERED_ON));
		assertTrue(VSphereConnector.checkVmPowerChange("stop", VmPowerState.POWERED_OFF));
		assertTrue(VSphereConnector.checkVmPowerChange("suspend", VmPowerState.SUSPENDED));
	}

	@Test
	public void testCheckVmPowerChangeVMFail() {
		assertFalse(VSphereConnector.checkVmPowerChange("stop", VmPowerState.POWERED_ON));
		assertFalse(VSphereConnector.checkVmPowerChange("reset", VmPowerState.POWERED_OFF));
		assertFalse(VSphereConnector.checkVmPowerChange("start", VmPowerState.SUSPENDED));
		assertFalse(VSphereConnector.checkVmPowerChange("start", null));
	}

	@Test
	public void testSetVmPowerPossibleSuccess() {
		assertTrue(VSphereConnector.setVmPowerPossible("start", VmPowerState.POWERED_OFF));
		assertTrue(VSphereConnector.setVmPowerPossible("start", VmPowerState.SUSPENDED));

		assertTrue(VSphereConnector.setVmPowerPossible("stop", VmPowerState.POWERED_ON));
		assertTrue(VSphereConnector.setVmPowerPossible("stop", VmPowerState.SUSPENDED));

		assertTrue(VSphereConnector.setVmPowerPossible("reset", VmPowerState.POWERED_ON));

		assertTrue(VSphereConnector.setVmPowerPossible("suspend", VmPowerState.POWERED_ON));
	}

	@Test
	public void testSetVmPowerPossibleFail() {
		assertFalse(VSphereConnector.setVmPowerPossible("start", VmPowerState.POWERED_ON));

		assertFalse(VSphereConnector.setVmPowerPossible("stop", VmPowerState.POWERED_OFF));

		assertFalse(VSphereConnector.setVmPowerPossible("reset", VmPowerState.POWERED_OFF));
		assertFalse(VSphereConnector.setVmPowerPossible("reset", VmPowerState.SUSPENDED));

		assertFalse(VSphereConnector.setVmPowerPossible("suspend", VmPowerState.POWERED_OFF));
		assertFalse(VSphereConnector.setVmPowerPossible("suspend", VmPowerState.SUSPENDED));
	}

	@Test
	public void testListDatastore() {
		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONArray());
		vsphereRes.getJSONArray("value").put(new JSONObject());
		vsphereRes.getJSONArray("value").getJSONObject(0).put("datastore", "datastore-13");
		vsphereRes.getJSONArray("value").getJSONObject(0).put("name", "datastore1-33");
		vsphereRes.getJSONArray("value").getJSONObject(0).put("type", "VMFS");
		vsphereRes.getJSONArray("value").getJSONObject(0).put("free_space", 23429382144L);
		vsphereRes.getJSONArray("value").getJSONObject(0).put("capacity", 34896609280L);
		TestUtils.mockVSphereResponse("GET", "/vcenter/datastore", 200, vsphereRes);

		JSONObject res = VSphereConnector.getDatastoreList();

		assertFalse(res.isNull("value"));
		JSONArray value = (JSONArray) res.get("value");
		if (value.length() > 0) {
			assertTrue(((JSONObject) (value.get(0))).has("datastore"));
			assertTrue(((JSONObject) (value.get(0))).has("name"));
			assertTrue(((JSONObject) (value.get(0))).has("type"));
			assertTrue(((JSONObject) (value.get(0))).has("free_space"));
			assertTrue(((JSONObject) (value.get(0))).has("capacity"));
		}
	}

	@Test
	public void testListDatastoreError() {
		TestUtils.mockVSphereResponse("GET", "/vcenter/datastore", 500, new JSONObject());

		JSONObject res = VSphereConnector.getDatastoreList();

		assertNull(res);
	}

	@Test
	public void testDetailDatastore() {
		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONObject());
		vsphereRes.getJSONObject("value").put("name", "datastore1-33");
		vsphereRes.getJSONObject("value").put("type", "VMFS");
		vsphereRes.getJSONObject("value").put("free_space", 23429382144L);
		vsphereRes.getJSONObject("value").put("capacity", 34896609280L);
		vsphereRes.getJSONObject("value").put("accessible", true);
		vsphereRes.getJSONObject("value").put("multiple_host_access", true);
		vsphereRes.getJSONObject("value").put("thin_provisioning_supported", true);
		TestUtils.mockVSphereResponse("GET", "/vcenter/datastore/datastore-13", 200, vsphereRes);

		JSONObject res = VSphereConnector.getDatastoreDetails("datastore-13");

		assertFalse(res.isNull("value"));
		JSONObject value = (JSONObject) res.get("value");
		assertTrue(value.has("accessible"));
		assertTrue(value.has("multiple_host_access"));
		assertTrue(value.has("name"));
		assertTrue(value.has("type"));
		assertTrue(value.has("free_space"));
		assertTrue(value.has("thin_provisioning_supported"));
	}

	@Test
	public void testDetailDatastoreNotFound() {
		TestUtils.mockVSphereResponse("GET", "/vcenter/datastore/datastore-13", 404, new JSONObject());

		JSONObject res = VSphereConnector.getDatastoreDetails("datastore-13");

		assertEquals(VSphereConnector.JSON_NOT_FOUND, res);
	}

	@Test
	public void testDetailDatastoreError() {
		TestUtils.mockVSphereResponse("GET", "/vcenter/datastore/datastore-13", 500, new JSONObject());

		JSONObject res = VSphereConnector.getDatastoreDetails("datastore-13");

		assertNull(res);
	}

	@Test
	public void testListHost() {
		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONArray());
		vsphereRes.getJSONArray("value").put(new JSONObject());
		vsphereRes.getJSONArray("value").getJSONObject(0).put("host", "host-12");
		vsphereRes.getJSONArray("value").getJSONObject(0).put("name", "192.168.4.33");
		vsphereRes.getJSONArray("value").getJSONObject(0).put("connection_state", "CONNECTED");
		vsphereRes.getJSONArray("value").getJSONObject(0).put("power_state", "POWERED_ON");
		TestUtils.mockVSphereResponse("GET", "/vcenter/host", 200, vsphereRes);

		JSONObject res = VSphereConnector.getHostList();

		assertFalse(res.isNull("value"));
		JSONArray value = (JSONArray) res.get("value");
		if (value.length() > 0) {
			assertTrue(((JSONObject) (value.get(0))).has("host"));
			assertTrue(((JSONObject) (value.get(0))).has("name"));
			assertTrue(((JSONObject) (value.get(0))).has("connection_state"));
			assertTrue(((JSONObject) (value.get(0))).has("power_state"));
		}
	}

	@Test
	public void testListHostError() {
		TestUtils.mockVSphereResponse("GET", "/vcenter/host", 500, new JSONObject());

		JSONObject res = VSphereConnector.getHostList();

		assertNull(res);
	}

	@Test
	public void testListVmError() {
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm", 500, new JSONObject());

		Vm vm = new Vm();
		vm.setIdVmVcenter("vm-123");

		JSONObject res = VSphereConnector.getVmList(BetterArrayList.fromList(Arrays.asList(vm)));

		assertNull(res);
	}

	@Test
	public void testListVm() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONArray());
		vsphereRes.getJSONArray("value").put(TestUtils.getVmMockVcenterJSON(vm));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm", 200, vsphereRes);

		JSONObject res = VSphereConnector.getVmList(BetterArrayList.fromList(Arrays.asList(vm)));

		JSONArray value = res.getJSONArray("value");

		assertEquals(1, value.length());

		assertEquals(vm.getIdVmVcenter(), value.getJSONObject(0).getString("vm"));
		assertEquals(vm.getName(), value.getJSONObject(0).getString("name"));
		assertEquals(vm.getDescription(), value.getJSONObject(0).getString("desc"));
		assertTrue(value.getJSONObject(0).has("memory_size_MiB"));
		assertTrue(value.getJSONObject(0).has("power_state"));
		assertTrue(value.getJSONObject(0).has("cpu_count"));
	}

	@Test
	public void testListVmEmpty() {
		JSONObject res = VSphereConnector.getVmList(new BetterArrayList<Vm>());

		JSONArray value = res.getJSONArray("value");

		assertEquals(0, value.length());
	}

	@Test
	public void testDetailVmError() {
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/vm-123", 500, new JSONObject());

		Vm vm = new Vm();
		vm.setIdVmVcenter("vm-123");

		JSONObject res = VSphereConnector.getVmDetails(vm);

		assertNull(res);
	}

	@Test
	public void testDetailVmNotFound() {
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/vm-123", 404, new JSONObject());

		Vm vm = new Vm();
		vm.setIdVmVcenter("vm-123");

		JSONObject res = VSphereConnector.getVmDetails(vm);

		assertEquals(VSphereConnector.JSON_NOT_FOUND, res);
	}

	@Test
	public void testDetailVm() {
        try {
            TestUtils.emptyDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
        }

		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

        Group g = new Group("test", "test");
        g.saveOrUpdate();
		Network n = new Network("network", "network-33");
		n.addGroup(g);
        n.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, vsphereRes);

		JSONObject res = VSphereConnector.getVmDetails(vm);

		JSONObject value = res.getJSONObject("value");

		assertEquals(vm.getName(), value.getString("name"));
		assertTrue(value.getJSONObject("cpu").has("cores_per_socket"));
		assertTrue(value.has("power_state"));
		assertTrue(value.getJSONObject("hardware").has("version"));
		assertEquals("DOS", value.getString("guest_OS_name"));
		assertEquals("os_dos", value.getString("guest_OS_icon"));
        assertEquals("network", value.getJSONArray("nics").getJSONObject(0).getJSONObject("value").getJSONObject("backing").getString("network_name"));
	}

	@Test
	public void testDeleteVmNotFound() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("DELETE", "/vcenter/vm/" + vm.getIdVmVcenter(), 404, vsphereRes);

		JSONObject res = VSphereConnector.deleteVm(vm);

		assertEquals("404", res.getString("not_found"));
	}

	@Test
	public void testDeleteVmNull() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("DELETE", "/vcenter/vm/" + vm.getIdVmVcenter(), 500, vsphereRes);

		JSONObject res = VSphereConnector.deleteVm(vm);

		assertNull(res);
	}

	@Test
	public void testUpdateRam() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/memory", 200,
				vsphereRes);

		JSONObject json = new JSONObject();
		json.put("size_MiB", 1024);
		int res = VSphereConnector.updateRam(vm, new JSONObject().put("spec", json));
		assertEquals(200, res);
	}

	@Test
	public void testUpdateRamNotFound() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/memory", 404,
				vsphereRes);

		JSONObject json = new JSONObject();
		json.put("size_MiB", 1024);
		int res = VSphereConnector.updateRam(vm, new JSONObject().put("spec", json));

		assertEquals(404, res);
	}

	@Test
	public void testUpdateRamVmNull() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/memory", 500,
				vsphereRes);

		JSONObject json = new JSONObject();
		json.put("size_MiB", 1024);
		int res = VSphereConnector.updateRam(vm, new JSONObject().put("spec", json));

		assertEquals(500, res);
	}

	@Test
	public void testUpdateCpu() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/cpu", 200, vsphereRes);

		JSONObject json = new JSONObject();
		json.put("count", 1);
		int res = VSphereConnector.updateCpu(vm, new JSONObject().put("spec", json));
		assertEquals(200, res);
	}

	@Test
	public void testUpdateCpuNotFound() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/cpu", 404, vsphereRes);

		JSONObject json = new JSONObject();
		json.put("count", 1);
		int res = VSphereConnector.updateCpu(vm, new JSONObject().put("spec", json));

		assertEquals(404, res);
	}

	@Test
	public void testUpdateCpuVmNull() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/cpu", 500, vsphereRes);

		JSONObject json = new JSONObject();
		json.put("count", 1);
		int res = VSphereConnector.updateCpu(vm, new JSONObject().put("spec", json));

		assertEquals(500, res);
	}

	@Test
	public void testGetSataAdapter() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 200,
				vsphereRes);

		JSONObject res = VSphereConnector.getSataAdapter(vm);

		assertEquals("{}", res.toString());
	}

	@Test
	public void testGetSataAdapterNotFound() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 404,
				vsphereRes);

		JSONObject res = VSphereConnector.getSataAdapter(vm);

		assertEquals("404", res.getString("not_found"));
	}

	@Test
	public void testGetSataAdapterNull() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 500,
				vsphereRes);

		JSONObject res = VSphereConnector.getSataAdapter(vm);

		assertNull(res);
	}

	@Test
	public void testCreateSata() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 200,
				vsphereRes);

		JSONObject json = new JSONObject();
		json.put("type", "AHCI");
		int res = VSphereConnector.createSataAdapter(vm, new JSONObject().put("spec", json));

		assertEquals(200, res);
	}

	@Test
	public void testCreateSataNotFound() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 404,
				vsphereRes);

		JSONObject json = new JSONObject();
		json.put("type", "AHCI");
		int res = VSphereConnector.createSataAdapter(vm, new JSONObject().put("spec", json));
		assertEquals(404, res);
	}

	@Test
	public void testCreateSataNull() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 500,
				vsphereRes);

		JSONObject json = new JSONObject();
		json.put("type", "AHCI");
		int res = VSphereConnector.createSataAdapter(vm, new JSONObject().put("spec", json));

		assertEquals(500, res);
	}

	@Test
	public void testCreateDisk() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 200, vsphereRes);

		JSONObject json = new JSONObject();
		json.put("type", "SATA");
		int res = VSphereConnector.createSataDisk(vm, new JSONObject().put("spec", json));

		assertEquals(200, res);
	}

	@Test
	public void testCreateDiskNotFound() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 404, vsphereRes);

		JSONObject json = new JSONObject();
		json.put("type", "SATA");
		int res = VSphereConnector.createSataDisk(vm, new JSONObject().put("spec", json));
		assertEquals(404, res);
	}

	@Test
	public void testCreateDiskNull() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 500, vsphereRes);

		JSONObject json = new JSONObject();
		json.put("type", "SATA");
		int res = VSphereConnector.createSataDisk(vm, new JSONObject().put("spec", json));

		assertEquals(500, res);
	}

	@Test
	public void testDeleteDisk() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("DELETE", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk/12", 200,
				vsphereRes);

		int res = VSphereConnector.deleteDisk(vm, 12);

		assertEquals(200, res);
	}

	@Test
	public void testDeleteDiskNotFound() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("DELETE", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk/12", 404,
				vsphereRes);

		int res = VSphereConnector.deleteDisk(vm, 12);

		assertEquals(404, res);
	}

	@Test
	public void testDeleteDiskNull() {
		User u = new User("test", "test");
		Vm vm = new Vm(u, "vm-123", "name", "desc");

		JSONObject vsphereRes = new JSONObject();
		TestUtils.mockVSphereResponse("DELETE", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk/12", 500,
				vsphereRes);

		int res = VSphereConnector.deleteDisk(vm, 12);

		assertEquals(500, res);
	}

    @Test
    public void testCreateEthernet() {
        User u = new User("test", "test");
        Vm vm = new Vm(u, "vm-123", "name", "desc");

        JSONObject vsphereRes = new JSONObject();
        vsphereRes.put("value", 12345);
        TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet", 200,
                vsphereRes);

		int[] res = VSphereConnector.createEthernet(vm, new Network("test", "test"));

        assertEquals(2, res.length);
        assertEquals(200, res[0]);
        assertEquals(12345, res[1]);
    }

    @Test
    public void testCreateEthernet2() {
        User u = new User("test", "test");
        Vm vm = new Vm(u, "vm-123", "name", "desc");

        JSONObject vsphereRes = new JSONObject();
        TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet", 500,
                vsphereRes);

		int[] res = VSphereConnector.createEthernet(vm, new Network("test", "test"));

        assertEquals(2, res.length);
        assertEquals(500, res[0]);
        assertEquals(-1, res[1]);
    }

    @Test
    public void testChangeEthernetNetwork() {
        User u = new User("test", "test");
        Vm vm = new Vm(u, "vm-123", "name", "desc");

        JSONObject vsphereRes = new JSONObject();
        TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345", 200,
                vsphereRes);

		int res = VSphereConnector.changeEthernetNetwork(vm, 12345, new Network("test", "test"));

        assertEquals(200, res);
    }

    @Test
    public void testConnectEthernet() {
        User u = new User("test", "test");
        Vm vm = new Vm(u, "vm-123", "name", "desc");

        JSONObject vsphereRes = new JSONObject();
        TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345/connect", 200,
                vsphereRes);

        int res = VSphereConnector.connectEthernet(vm, 12345);

        assertEquals(200, res);
    }

    @Test
    public void testDisconnectEthernet() {
        User u = new User("test", "test");
        Vm vm = new Vm(u, "vm-123", "name", "desc");

        JSONObject vsphereRes = new JSONObject();
        TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345/disconnect", 200,
                vsphereRes);

        int res = VSphereConnector.disconnectEthernet(vm, 12345);

        assertEquals(200, res);
    }

    @Test
    public void testDeleteEthernet() {
        User u = new User("test", "test");
        Vm vm = new Vm(u, "vm-123", "name", "desc");

        JSONObject vsphereRes = new JSONObject();
        TestUtils.mockVSphereResponse("DELETE", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345", 200,
                vsphereRes);

        int res = VSphereConnector.deleteEthernet(vm, 12345);

        assertEquals(200, res);
    }

}
