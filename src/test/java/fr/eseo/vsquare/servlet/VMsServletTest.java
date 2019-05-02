package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.*;
import fr.eseo.vsquare.model.EventLog.EventAction;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.*;
import fr.eseo.vsquare.utils.VSphereConnector.VmPowerState;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoException;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/*
 * @author: Pierre P.
 */

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "java.*", "javax.*", "org.*", "fr.eseo.vsquare.model.*", "fr.eseo.vsquare.utils.DatabaseManager" })
@PrepareForTest({ VSphereManager.class, LDAPUtils.class, ServletUtils.class, VCenterManager.class,
		VSphereConnector.class })
public class VMsServletTest {

	@BeforeClass
	public static void setUp() throws Exception {
		//VSphereManager.init();
	}
	
	@Before
	public void beforeEach() throws Exception{
		TestUtils.initTest(true);
	}

	@Test
	public void testServletOptions() {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("OPTIONS", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new VMsServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testServletNoAuth() {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new VMsServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
	}

	@Test
	public void testServletError() {
		PowerMockito.mockStatic(ServletUtils.class);
		when(ServletUtils.verifyToken(Mockito.any(), Mockito.any()))
				.thenThrow(new MockitoException("Test error do not panic"));

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(new StringWriter());
		new VMsServlet().service(request, response);

		assertEquals(1, ErrorLog.getAll().size());
	}

	@Test
	public void testListVms() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONArray());
		vsphereRes.getJSONArray("value").put(TestUtils.getVmMockVcenterJSON(vm));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm", 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/vm", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);

		assertEquals(200, res.getInt("code"));

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
	public void testListVmsError() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONArray());
		vsphereRes.getJSONArray("value").put(TestUtils.getVmMockVcenterJSON(vm));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm", 123, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/vm", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);

		assertEquals(502, res.getInt("code"));
	}

	@Test
	public void testListVmsNoVm() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/vm", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));

		JSONArray value = res.getJSONArray("value");

		assertEquals(0, value.length());
	}

	@Test
	public void testDetailsVms() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));

		JSONObject value = res.getJSONObject("value");

		assertEquals(vm.getName(), value.getString("name"));
		assertTrue(value.getJSONObject("cpu").has("cores_per_socket"));
		assertTrue(value.has("power_state"));
		assertTrue(value.getJSONObject("hardware").has("version"));
		assertEquals("DOS", value.getString("guest_OS_name"));
		assertEquals("os_dos", value.getString("guest_OS_icon"));
	}

	@Test
	public void testDetailsVmsError() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 123, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(502, res.getInt("code"));
	}

	@Test
	public void testDetailsVmsStudentValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));

		JSONObject value = res.getJSONObject("value");

		assertEquals(vm.getName(), value.getString("name"));
		assertTrue(value.getJSONObject("cpu").has("cores_per_socket"));
		assertTrue(value.has("power_state"));
		assertTrue(value.getJSONObject("hardware").has("version"));
	}

	@Test
	public void testDetailsVmsStudentNotValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		User u2 = new User("test2", UserType.STUDENT, "test2");
		u2.saveOrUpdate();

		Vm vm = new Vm(u2, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(403, res.getInt("code"));
	}

	@Test
	public void testDetailsVmsStudentNotValid2() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		User u2 = new User("test2", UserType.REFERENT, "test2");
		u2.saveOrUpdate();

		Vm vm = new Vm(u2, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(403, res.getInt("code"));
	}

	@Test
	public void testDetailsVmsStudentNotValid3() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		User u2 = new User("test2", UserType.ADMIN, "test2");
		u2.saveOrUpdate();

		Vm vm = new Vm(u2, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(403, res.getInt("code"));
	}

	@Test
	public void testDetailsVmsReferentValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testDetailsVmsReferentValid2() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);

		User u2 = new User("test2", UserType.STUDENT, "test2");
		u2.saveOrUpdate();

		Vm vm = new Vm(u2, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testDetailsVmsReferentNotValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);

		User u2 = new User("test2", UserType.REFERENT, "test2");
		u2.saveOrUpdate();

		Vm vm = new Vm(u2, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(403, res.getInt("code"));
	}

	@Test
	public void testDetailsVmsReferentNotValid2() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);

		User u2 = new User("test2", UserType.ADMIN, "test2");
		u2.saveOrUpdate();

		Vm vm = new Vm(u2, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(403, res.getInt("code"));
	}

	@Test
	public void testDetailsVmsAdminValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		User u2 = new User("test2", UserType.STUDENT, "test2");
		u2.saveOrUpdate();

		Vm vm = new Vm(u2, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testDetailsVmsAdminValid2() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		User u2 = new User("test2", UserType.REFERENT, "test2");
		u2.saveOrUpdate();

		Vm vm = new Vm(u2, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testDetailsNoVms() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/vm/vm-10", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}

	@Test
	public void testDeleteVmValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("DELETE", "/vcenter/vm/" + vm.getIdVmVcenter(), 200, emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/vm/" + vm.getIdVmVcenter(), null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testDeleteVmDeletionError() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/vm/10", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(404, res.getInt("code"));
	}

	@Test
	public void testEditVm() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter(), null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testEditVmNoAccess() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		User simpleUser = new User("adminTest123456", UserType.ADMIN, "adminTest in VMsServletsTest");
		simpleUser.saveOrUpdate();

		Vm vm = new Vm(simpleUser, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter(), null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject result = TestUtils.getResponseAsJSON(writer);
		assertEquals(403, result.getInt("code"));
	}

	@Test
	public void testEditVmNoVM() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/badvm", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject result = TestUtils.getResponseAsJSON(writer);
		assertEquals(404, result.getInt("code"));
	}

	@Test
	public void testEditVmDesc() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("desc", "testDesc");

		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter(), params,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testEditVmName() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "testName");

		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter(), params,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testEditVmNameFail() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "");

		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter(), params,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}

	@Test
	public void testEditVmNameFail2() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "0123456789012345678901234567890123456789012345678901234567890123456789");

		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter(), params,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}

	@Test
	public void testEditCheckVmStateOff() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		PowerMockito.mockStatic(VSphereConnector.class);
		Mockito.when(VSphereConnector.getVmPowerState(Mockito.any()))
				.thenReturn(VSphereConnector.VmPowerState.POWERED_OFF);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		boolean state = new VMsServlet().checkVmState(response, vm);

		assertTrue(state);
	}

	@Test
	public void testEditCheckVmStateOn() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/memory", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("memory_size", "1024");

		PowerMockito.mockStatic(VSphereConnector.class);
		Mockito.when(VSphereConnector.getVmPowerState(Mockito.any()))
				.thenReturn(VSphereConnector.VmPowerState.POWERED_ON);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter(), params,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(409, res.getInt("code"));
	}

	@Test
	public void testEditVmRam() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/memory", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("memory_size", "1024");

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter(), params,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testEditVmRamFail2() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/memory", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("memory_size", "1024");

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());

		PowerMockito.mockStatic(VSphereConnector.class);
		Mockito.when(VSphereConnector.updateRam(Mockito.any(), Mockito.any()))
				.thenReturn(404);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter(), params,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(502, res.getInt("code"));
	}

	@Test
	public void testEditVmRamFail() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/memory", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("memory_size", "-1");

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter(), params,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}

	@Test
	public void testEditVmCpu() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/cpu", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("cpu_count", "1");

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter(), params,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testEditVmCpuFail() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/cpu", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("cpu_count", "-1");

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter(), params,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}

	@Test
	public void testCreateDisk() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
		doReturn(true).when(mockVMsServlet).checkSataAdapter(Mockito.any(), Mockito.any());
		doReturn(1073741824L).when(mockVMsServlet).getDisksCapacityTaken(Mockito.any(), Mockito.any());

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/disk",
				null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}
	
	@Test
	public void testCreateDiskOutBound() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("capacity", "8192");

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
		doReturn(true).when(mockVMsServlet).checkSataAdapter(Mockito.any(), Mockito.any());
		doReturn(12608077824L).when(mockVMsServlet).getDisksCapacityTaken(Mockito.any(), Mockito.any());

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/disk",
				params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}
	

	@Test
	public void testCreateDiskNotFound() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/12222/disk", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(404, res.getInt("code"));
	}

	@Test
	public void testCreateDiskStateFail() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		PowerMockito.mockStatic(VSphereConnector.class);
		Mockito.when(VSphereConnector.getVmPowerState(Mockito.any()))
				.thenReturn(VSphereConnector.VmPowerState.POWERED_ON);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/disk",
				null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(409, res.getInt("code"));
	}

	@Test
	public void testCreateDiskFail() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
		doReturn(true).when(mockVMsServlet).checkSataAdapter(Mockito.any(), Mockito.any());
		doReturn(1073741824L).when(mockVMsServlet).getDisksCapacityTaken(Mockito.any(), Mockito.any());

		PowerMockito.mockStatic(VSphereConnector.class);
		Mockito.when(VSphereConnector.createSataDisk(Mockito.any(), Mockito.any())).thenReturn(500);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/disk",
				null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(502, res.getInt("code"));
	}

	@Test
	public void testCreateDiskFail2() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
		doReturn(true).when(mockVMsServlet).checkSataAdapter(Mockito.any(), Mockito.any());
		doReturn(1073741824L).when(mockVMsServlet).getDisksCapacityTaken(Mockito.any(), Mockito.any());

		PowerMockito.mockStatic(VSphereConnector.class);
		Mockito.when(VSphereConnector.createSataDisk(Mockito.any(), Mockito.any()))
				.thenReturn(404);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/disk",
				null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(502, res.getInt("code"));
	}

	@Test
	public void testCreateDiskNameValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "testName");

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
		doReturn(true).when(mockVMsServlet).checkSataAdapter(Mockito.any(), Mockito.any());
		doReturn(1073741824L).when(mockVMsServlet).getDisksCapacityTaken(Mockito.any(), Mockito.any());

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/disk",
				params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testCreateDiskNameNotValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "");

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
		doReturn(true).when(mockVMsServlet).checkSataAdapter(Mockito.any(), Mockito.any());
		doReturn(1073741824L).when(mockVMsServlet).getDisksCapacityTaken(Mockito.any(), Mockito.any());

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/disk",
				params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}

	@Test
	public void testCreateDiskCapacityValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("capacity", "2147483648");

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
		doReturn(true).when(mockVMsServlet).checkSataAdapter(Mockito.any(), Mockito.any());
		doReturn(1073741824L).when(mockVMsServlet).getDisksCapacityTaken(Mockito.any(), Mockito.any());

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/disk",
				params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testCreateDiskCapacityNotValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("capacity", "16000");

		VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
		doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
		doReturn(true).when(mockVMsServlet).checkSataAdapter(Mockito.any(), Mockito.any());
		doReturn(1073741824L).when(mockVMsServlet).getDisksCapacityTaken(Mockito.any(), Mockito.any());

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/disk",
				params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		mockVMsServlet.service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}

	@Test
	public void testGetDisksCapacityTaken() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject jsonSataResponse = new JSONObject();
		JSONObject value = new JSONObject();
		JSONArray diskArray = new JSONArray();
		JSONObject valueInArray = new JSONObject();
		JSONObject value1 = new JSONObject();
		value1.put("label", "Hard disk 1");
		value1.put("capacity",  7516192768L);
		valueInArray.put("value", value1);
		diskArray.put(valueInArray);
		value.put("disks", diskArray);
		jsonSataResponse.put("value", value);

		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
				jsonSataResponse);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		Long check = new VMsServlet().getDisksCapacityTaken(response, vm);

		assertEquals(7516192768L, check.longValue());
	}
	
	@Test
	public void testGetDisksCapacityTakenTooManyDisks() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject jsonSataResponse = new JSONObject();
		JSONObject value = new JSONObject();
		JSONArray diskArray = new JSONArray();
		JSONObject valueInArray = new JSONObject();
		JSONObject value1 = new JSONObject();
		value1.put("label", "Hard disk 1");
		value1.put("capacity",  7516192768L);
		valueInArray.put("value", value1);
		diskArray.put(valueInArray);
		JSONObject valueInArray2 = new JSONObject();
		JSONObject value2 = new JSONObject();
		value2.put("label", "Hard disk 2");
		value2.put("capacity",  7516192768L);
		valueInArray2.put("value", value2);
		diskArray.put(valueInArray2);
		value.put("disks", diskArray);
		jsonSataResponse.put("value", value);

		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
				jsonSataResponse);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		Long check = new VMsServlet().getDisksCapacityTaken(response, vm);

		assertNull(check);
	}
	
	@Test
	public void testGetDisksCapacityTakenNull() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 500,
				null);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		Long check = new VMsServlet().getDisksCapacityTaken(response, vm);

		assertNull(check);
	}
	
	@Test
	public void testGetDisksCapacityTakenNoDisk() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		JSONObject jsonSataResponse = new JSONObject();
		JSONObject value = new JSONObject();
		JSONArray diskArray = new JSONArray();
		value.put("disks", diskArray);
		jsonSataResponse.put("value", value);

		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
				jsonSataResponse);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		Long check = new VMsServlet().getDisksCapacityTaken(response, vm);

		assertEquals(0,check.longValue());
	}
	
	@Test
	public void testCompareDisksValue() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		JSONObject jsonSataResponse = new JSONObject();
		JSONObject value = new JSONObject();
		JSONArray diskArray = new JSONArray();
		JSONObject valueInArray = new JSONObject();
		JSONObject value1 = new JSONObject();
		value1.put("label", "Hard disk 1");
		value1.put("capacity",  7516192768L);
		valueInArray.put("value", value1);
		diskArray.put(valueInArray);
		JSONObject valueInArray2 = new JSONObject();
		JSONObject value2 = new JSONObject();
		value2.put("label", "Hard disk 2");
		value2.put("capacity",  7516192768L);
		valueInArray2.put("value", value2);
		diskArray.put(valueInArray2);
		value.put("disks", diskArray);
		jsonSataResponse.put("value", value);

		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
				jsonSataResponse);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		boolean check = new VMsServlet().compareDisksValue(response, vm, 7516192768L, "Hard disk 2", 15516192768L);

		assertTrue(check);
	}
	
	@Test
	public void testCompareDisksNoDisk() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		JSONObject jsonSataResponse = new JSONObject();
		JSONObject value = new JSONObject();
		JSONArray diskArray = new JSONArray();
		JSONObject valueInArray = new JSONObject();
		JSONObject value1 = new JSONObject();
		value1.put("label", "Hard disk 1");
		value1.put("capacity",  7516192768L);
		valueInArray.put("value", value1);
		diskArray.put(valueInArray);
		value.put("disks", diskArray);
		jsonSataResponse.put("value", value);

		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
				jsonSataResponse);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		boolean check = new VMsServlet().compareDisksValue(response, vm, 7516192768L, "Hard disk 2", 15516192768L);

		assertTrue(!check);
	}
	
	@Test
	public void testCompareDisksNoDisks() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		JSONObject jsonSataResponse = new JSONObject();
		JSONObject value = new JSONObject();
		JSONArray diskArray = new JSONArray();
		value.put("disks", diskArray);
		jsonSataResponse.put("value", value);

		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
				jsonSataResponse);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		boolean check = new VMsServlet().compareDisksValue(response, vm, 7516192768L, "Hard disk 2", 15516192768L);

		assertTrue(!check);
	}
	
	@Test
	public void testCompareDisksValueSup() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		JSONObject jsonSataResponse = new JSONObject();
		JSONObject value = new JSONObject();
		JSONArray diskArray = new JSONArray();
		JSONObject valueInArray = new JSONObject();
		JSONObject value1 = new JSONObject();
		value1.put("label", "Hard disk 1");
		value1.put("capacity",  7516192768L);
		valueInArray.put("value", value1);
		diskArray.put(valueInArray);
		JSONObject valueInArray2 = new JSONObject();
		JSONObject value2 = new JSONObject();
		value2.put("label", "Hard disk 2");
		value2.put("capacity",  7516192768L);
		valueInArray2.put("value", value2);
		diskArray.put(valueInArray2);
		value.put("disks", diskArray);
		jsonSataResponse.put("value", value);

		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
				jsonSataResponse);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		boolean check = new VMsServlet().compareDisksValue(response, vm, 9516192768L, "Hard disk 2", 15516192768L);

		assertTrue(!check);
	}
	
	@Test
	public void testCompareDisksValueInf() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		JSONObject jsonSataResponse = new JSONObject();
		JSONObject value = new JSONObject();
		JSONArray diskArray = new JSONArray();
		JSONObject valueInArray = new JSONObject();
		JSONObject value1 = new JSONObject();
		value1.put("label", "Hard disk 1");
		value1.put("capacity",  536870912L);
		valueInArray.put("value", value1);
		diskArray.put(valueInArray);
		JSONObject valueInArray2 = new JSONObject();
		JSONObject value2 = new JSONObject();
		value2.put("label", "Hard disk 2");
		value2.put("capacity",  7516192768L);
		valueInArray2.put("value", value2);
		diskArray.put(valueInArray2);
		value.put("disks", diskArray);
		jsonSataResponse.put("value", value);

		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
				jsonSataResponse);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		boolean check = new VMsServlet().compareDisksValue(response, vm, 68L, "Hard disk 2", 15516192768L);

		assertTrue(!check);
	}


    @Test
    public void testModifyDisk() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HashMap<String, String> params = new HashMap<>();
        params.put("capacity", "4294967296");

        VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
        doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
        doReturn(true).when(mockVMsServlet).compareDisksValue(Mockito.any(), Mockito.any(),Mockito.any(),Mockito.any(),Mockito.any());
        doReturn("test").when(mockVMsServlet).getDiskLabel(Mockito.any(), Mockito.any());

        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.modifyDisk(Mockito.any(),Mockito.any(),Mockito.any())).thenReturn(true);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/disk/12",
                params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        mockVMsServlet.service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(200, res.getInt("code"));
    }

    @Test
    public void testModifyDiskFail() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HashMap<String, String> params = new HashMap<>();
        params.put("capacity", "4294967296");

        VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
        doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
        doReturn(true).when(mockVMsServlet).compareDisksValue(Mockito.any(), Mockito.any(),Mockito.any(),Mockito.any(),Mockito.any());
        doReturn("test").when(mockVMsServlet).getDiskLabel(Mockito.any(), Mockito.any());

        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.modifyDisk(Mockito.any(),Mockito.any(),Mockito.any())).thenReturn(false);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/disk/12",
                params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        mockVMsServlet.service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(500, res.getInt("code"));
    }

    @Test
    public void testModifyDiskNull() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
                new JSONObject());

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/2222/disk/12",
                null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(404, res.getInt("code"));
    }

    @Test
    public void testModifyDiskIdNull() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
                new JSONObject());

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
        doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
        doReturn(null).when(mockVMsServlet).getDiskLabel(Mockito.any(), Mockito.any());

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/"+ vm.getIdVmVcenter() +"/disk/12",
                null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        mockVMsServlet.service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(400, res.getInt("code"));
    }

    @Test
    public void testModifyDiskCapacityNull() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
                new JSONObject());

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
        doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
        doReturn("test").when(mockVMsServlet).getDiskLabel(Mockito.any(), Mockito.any());

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/"+ vm.getIdVmVcenter() +"/disk/12",
                null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        mockVMsServlet.service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(400, res.getInt("code"));
    }

    @Test
    public void testGetDiskLabel() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        JSONObject jsonSataResponse = new JSONObject();
        JSONObject value = new JSONObject();
        JSONArray diskArray = new JSONArray();
        JSONObject valueInArray = new JSONObject();
        JSONObject value1 = new JSONObject();
        value1.put("label", "Hard disk 1");
        value1.put("capacity",  536870912L);
        valueInArray.put("value", value1);
        valueInArray.put("key",12);
        diskArray.put(valueInArray);
        value.put("disks", diskArray);
        jsonSataResponse.put("value", value);

        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
                jsonSataResponse);

        String s = new VMsServlet().getDiskLabel(vm, 12);

        assertEquals("Hard disk 1", s);
    }

    @Test
    public void testGetDiskLabelNull() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        JSONObject jsonSataResponse = new JSONObject();
        JSONObject value = new JSONObject();
        JSONArray diskArray = new JSONArray();
        JSONObject valueInArray = new JSONObject();
        JSONObject value1 = new JSONObject();
        value1.put("label", "Hard disk 1");
        value1.put("capacity",  536870912L);
        valueInArray.put("value", value1);
        valueInArray.put("key",12);
        diskArray.put(valueInArray);
        value.put("disks", diskArray);
        jsonSataResponse.put("value", value);

        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
                jsonSataResponse);

        String s = new VMsServlet().getDiskLabel(vm, 13);

        assertNull(s);
    }

	@Test
	public void testCheckSataAdapter() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject jsonSataResponse = new JSONObject();
		JSONArray value = new JSONArray();
		value.put(new JSONObject().put("adapter", "15000"));
		jsonSataResponse.put("value", value);

		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 200,
				jsonSataResponse);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		boolean check = new VMsServlet().checkSataAdapter(response, vm);

		assertTrue(check);
	}

	@Test
	public void testCheckSataAdapterNull() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject jsonSataResponse = new JSONObject();
		JSONArray value = new JSONArray();
		value.put(new JSONObject().put("adapter", "15000"));
		jsonSataResponse.put("value", value);

		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 500,
				jsonSataResponse);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		boolean check = new VMsServlet().checkSataAdapter(response, vm);

		assertTrue(!check);
	}

	@Test
	public void testCheckSataAdapterNotFound() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject jsonSataResponse = new JSONObject();
		JSONArray value = new JSONArray();
		value.put(new JSONObject().put("adapter", "15000"));
		jsonSataResponse.put("value", value);

		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 404,
				jsonSataResponse);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		boolean check = new VMsServlet().checkSataAdapter(response, vm);

		assertTrue(!check);
	}

	@Test
	public void testCheckSataNoAdapter() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject jsonSataResponse = new JSONObject();
		JSONArray value = new JSONArray();
		jsonSataResponse.put("value", value);
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 200,
				jsonSataResponse);

		JSONObject stateResult = new JSONObject();
		JSONObject value2 = new JSONObject();
		stateResult.put("value", value2);
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 200,
				stateResult, false);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		boolean check = new VMsServlet().checkSataAdapter(response, vm);

		assertTrue(check);
	}

	@Test
	public void testCheckSataNoAdapterNotFound() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject jsonSataResponse = new JSONObject();
		JSONArray value = new JSONArray();
		jsonSataResponse.put("value", value);
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 200,
				jsonSataResponse);

		JSONObject stateResult = new JSONObject();
		JSONObject value2 = new JSONObject();
		stateResult.put("value", value2);
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 404,
				stateResult, false);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		boolean check = new VMsServlet().checkSataAdapter(response, vm);

		assertTrue(!check);
	}

	@Test
	public void testCheckSataNoAdapterNull() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject jsonSataResponse = new JSONObject();
		JSONArray value = new JSONArray();
		jsonSataResponse.put("value", value);
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 200,
				jsonSataResponse);

		JSONObject stateResult = new JSONObject();
		JSONObject value2 = new JSONObject();
		stateResult.put("value", value2);
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/adapter/sata", 500,
				stateResult, false);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		boolean check = new VMsServlet().checkSataAdapter(response, vm);

		assertTrue(!check);
	}

	@Test
	public void testDeleteDisk() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
                new JSONObject());

        VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
        doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
        doReturn("test").when(mockVMsServlet).getDiskLabel(Mockito.any(), Mockito.any());
		
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.modifyDisk(Mockito.any(),Mockito.any(),Mockito.any())).thenReturn(true);

        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.deleteDisk(Mockito.any(),Mockito.any())).thenReturn(true);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE",
                "/api/vm/" + vm.getIdVmVcenter() + "/disk/42", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        mockVMsServlet.service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(200, res.getInt("code"));
	}
	
	@Test
	public void testDeleteDiskBad() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
                new JSONObject());

        VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
        doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
        doReturn("test").when(mockVMsServlet).getDiskLabel(Mockito.any(), Mockito.any());

        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.deleteDisk(Mockito.any(),Mockito.any())).thenReturn(true);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE",
                "/api/vm/" + vm.getIdVmVcenter() + "/disk/ooo", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        mockVMsServlet.service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(400, res.getInt("code"));
	}

	@Test
	public void testDeleteDiskNotFound() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
                new JSONObject());

        VMsServlet mockVMsServlet = Mockito.spy(VMsServlet.class);
        doReturn(true).when(mockVMsServlet).checkVmState(Mockito.any(), Mockito.any());
        doReturn("test").when(mockVMsServlet).getDiskLabel(Mockito.any(), Mockito.any());

        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.deleteDisk(Mockito.any(),Mockito.any())).thenReturn(true);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE",
                "/api/vm/2222/disk/42", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        mockVMsServlet.service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(404, res.getInt("code"));
	}

	@Test
	public void testDeleteDiskStateFail() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("DELETE", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/disk/42", 200,
				emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		PowerMockito.mockStatic(VSphereConnector.class);
		Mockito.when(VSphereConnector.getVmPowerState(Mockito.any()))
				.thenReturn(VSphereConnector.VmPowerState.POWERED_ON);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("DELETE",
				"/api/vm/" + vm.getIdVmVcenter() + "/disk/42", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(409, res.getInt("code"));
	}
	
	@Test
	public void testCreateTemplate() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/vm-10", 200,
                new JSONObject().put("value", new JSONObject()));
		
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.cloneVm(eq(vm),eq("vmName_template"), eq(true))).thenReturn("vm-10");
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT",
                "/api/vm/" + vm.getIdVmVcenter() + "/template", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(200, res.getInt("code"));
	}
	
	@Test
	public void testCreateTemplateNoAccess() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/vm-10", 200,
                new JSONObject().put("value", new JSONObject()));
		
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.cloneVm(eq(vm),eq("vmName_template"), eq(true))).thenReturn("vm-10");
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT",
                "/api/vm/" + vm.getIdVmVcenter() + "/template", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(403, res.getInt("code"));
	}
	
	@Test
	public void testCreateTemplateNull() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/vm-10", 200,
                new JSONObject().put("value", new JSONObject()));
		
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.cloneVm(eq(vm),eq("vmName_template"), eq(true))).thenReturn(null);
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT",
                "/api/vm/" + vm.getIdVmVcenter() + "/template", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(500, res.getInt("code"));
	}
	
	@Test
	public void testCreateTemplateEx() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/vm-10", 200,
                new JSONObject().put("value", new JSONObject()));
		
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.cloneVm(eq(vm),eq("vmName_template"), eq(true))).thenThrow(IOException.class);
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT",
                "/api/vm/" + vm.getIdVmVcenter() + "/template", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(500, res.getInt("code"));
	}	
	
	@Test
	public void testCreateVmFromTemplate() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.setIdVmVcenter("vm-12");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/vm-10", 200,
                new JSONObject().put("value", new JSONObject()));
		
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.cloneTemplate(eq(vm),eq("vmName_CloneTemplate"))).thenReturn("vm-10");
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT",
                "/api/vm/template/vm-12", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(200, res.getInt("code"));
	}
	
	@Test
	public void testCreateVmFromTemplateNoAccess() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.setIdVmVcenter("vm-12");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/vm-10", 200,
                new JSONObject().put("value", new JSONObject()));
		
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.cloneTemplate(eq(vm),eq("vmName_CloneTemplate"))).thenReturn("vm-10");
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT",
                "/api/vm/template/vm-12", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(403, res.getInt("code"));
	}
	
	@Test
	public void testCreateVmFromTemplateNull() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.setIdVmVcenter("vm-12");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/vm-10", 200,
                new JSONObject().put("value", new JSONObject()));
		
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.cloneTemplate(eq(vm),eq("vmName_CloneTemplate"))).thenReturn(null);
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT",
                "/api/vm/template/vm-12", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(500, res.getInt("code"));
	}
	
	@Test
	public void testCreateVmFromTemplateEx() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.setIdVmVcenter("vm-12");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/vm-10", 200,
                new JSONObject().put("value", new JSONObject()));
        
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.cloneVm(eq(vm),eq("vmName_template"), eq(true))).thenThrow(IOException.class);
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT",
                "/api/vm/template/vm-12", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(500, res.getInt("code"));
	}	
	
	@Test
	public void testGetTemplate() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("GET","/api/vm/templates", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(200, res.getInt("code"));
	}
	
	@Test
	public void testGetTemplate1() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Group g1 = Group.findById(3);
        g1.saveOrUpdate();
        
        t.getUser().addGroup(g1);
        t.saveOrUpdate();

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.setTemplate(true);
        vm.saveOrUpdate();
        
        g1.addTemplate(vm);
        g1.saveOrUpdate();

        JSONObject jsonSataResponse = new JSONObject();
        JSONObject value = new JSONObject();
        value.put("memory", new JSONObject().put("size_MiB", 1536));
        value.put("cpu", new JSONObject().put("count", 1));
        value.put("power_state", "POWERED_OFF");
        jsonSataResponse.put("value", value);

        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
                jsonSataResponse);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("GET","/api/vm/templates", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(200, res.getInt("code"));
        JSONArray resu = res.getJSONArray("value");
        assertEquals(1, resu.length());
	}
	
	@Test
	public void testGetTemplate2() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);
        
        Group g1 = Group.findById(3);
        g1.saveOrUpdate();
        
        User u = new User("aaa","aaa");
        u.saveOrUpdate();

        Vm vm = new Vm(u, "id_vm", "vmName", "no link to vsphere");
        vm.setTemplate(true);
        vm.saveOrUpdate();
        
        g1.saveOrUpdate();
        
        JSONObject jsonSataResponse = new JSONObject();
        JSONObject value = new JSONObject();
        value.put("memory", new JSONObject().put("size_MiB", 1536));
        value.put("cpu", new JSONObject().put("count", 1));
        value.put("power_state", "POWERED_OFF");
        jsonSataResponse.put("value", value);

        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
                jsonSataResponse);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("GET","/api/vm/templates", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(200, res.getInt("code"));
	}
	
	@Test
	public void testDeleteTemplate() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.setIdVmVcenter("vm-12");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
		
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.deleteVm(Mockito.any())).thenReturn(true);
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE","/api/vm/template/vm-12", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(200, res.getInt("code"));
	}
	
	@Test
	public void testDeleteTemplateNoAccess() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.setIdVmVcenter("vm-12");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
		
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.deleteVm(Mockito.any())).thenReturn(true);
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE","/api/vm/template/vm-12", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(403, res.getInt("code"));
	}
	
	@Test
	public void testDeleteTemplateFail() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.setIdVmVcenter("vm-12");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
		
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.deleteVm(Mockito.any())).thenReturn(false);
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE","/api/vm/template/vm-12", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(500, res.getInt("code"));
	}
	
	@Test
	public void testDeleteTemplateEx() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.setIdVmVcenter("vm-12");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);
        
		
        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.deleteVm(Mockito.any())).thenThrow(RemoteException.class);
        
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE","/api/vm/template/vm-12", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(500, res.getInt("code"));
	}

	@Test
	public void testSetPowerSuccess() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/power/start", 200, emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST",
				"/api/vm/" + vm.getIdVmVcenter() + "/power/start", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));

		List<EventLog> events = EventLog.getAll();
		assertEquals(1, events.size());
		assertEquals(Token.findByValue(token).getUser(), events.get(0).getUser());
		assertEquals(EventAction.POWER_ON, events.get(0).getAction());
		assertEquals(vm, events.get(0).getObject());
	}

	@Test
	public void testSetPowerAlreadyStartedSuccess() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/power/start", 400, emptyResult);
		JSONObject stateResult = new JSONObject();
		JSONObject value = new JSONObject();
		value.put("state", "POWERED_ON");
		stateResult.put("value", value);
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter() + "/power", 200, stateResult, false);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST",
				"/api/vm/" + vm.getIdVmVcenter() + "/power/start", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject result = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, result.getInt("code"));
	}

	@Test
	public void testSetPowerBadAction() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST",
				"/api/vm/" + vm.getIdVmVcenter() + "/power/badaction", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject result = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, result.getInt("code"));
	}

	@Test
	public void testSetPowerBadVM() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/badvm/power/start", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject result = TestUtils.getResponseAsJSON(writer);
		assertEquals(404, result.getInt("code"));
	}

	@Test
	public void testSetPowerNoWritingRights() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		User simpleUser = new User("adminTest123456", UserType.ADMIN, "adminTest in VMsServletsTest");
		simpleUser.saveOrUpdate();

		Vm vm = new Vm(simpleUser, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST",
				"/api/vm/" + vm.getIdVmVcenter() + "/power/start", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject result = TestUtils.getResponseAsJSON(writer);
		assertEquals(403, result.getInt("code"));
	}

	@Test
	public void testSetPowerWithWritingRights() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		User simpleUser = new User("studentTest", UserType.STUDENT, "studentTest in VMsServletsTest");
		simpleUser.saveOrUpdate();

		Vm vm = new Vm(simpleUser, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/power/start", 200, emptyResult);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST",
				"/api/vm/" + vm.getIdVmVcenter() + "/power/start", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject result = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, result.getInt("code"));
	}

	@Test
	public void testSetPowerAlreadyStartedFail() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/power/start", 400, emptyResult);
		JSONObject stateResult = new JSONObject();
		JSONObject value = new JSONObject();
		value.put("state", "POWERED_OFF");
		stateResult.put("value", value);
		TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter() + "/power", 200, stateResult, false);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST",
				"/api/vm/" + vm.getIdVmVcenter() + "/power/start", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject result = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, result.getInt("code"));
	}

	@Test
	public void testSetPowerAllSuccess() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);
		Token t = Token.findByValue(token);

		User student = new User("student", UserType.STUDENT, "student");
		student.saveOrUpdate();

		Vm vm1 = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm1.saveOrUpdate();

		Vm vm2 = new Vm(student, "id_vm2", "vmName2", "no link to vsphere2");
		vm2.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm1.getIdVmVcenter() + "/power/start", 200, emptyResult);
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm2.getIdVmVcenter() + "/power/start", 200, emptyResult,
				false);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/all/power/start", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
		assertTrue(res.has("value"));
		assertTrue(res.getJSONObject("value").has("total_vms"));
		assertTrue(res.getJSONObject("value").has("total_success"));
		assertEquals(2, res.getJSONObject("value").getInt("total_vms"));
		assertEquals(2, res.getJSONObject("value").getInt("total_success"));

		User u = Token.findByValue(token).getUser();
		List<EventLog> events = EventLog.getAll();
		assertEquals(1, events.size());
		assertEquals(u, events.get(0).getUser());
		assertEquals(EventAction.POWER_ON, events.get(0).getAction());
		assertEquals(u, events.get(0).getObject());
	}

	@Test
	public void testSetPowerAllNotAdmin() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);
		Token t = Token.findByValue(token);

		User student = new User("student", UserType.STUDENT, "student");
		student.saveOrUpdate();

		Vm vm1 = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm1.saveOrUpdate();

		Vm vm2 = new Vm(student, "id_vm2", "vmName2", "no link to vsphere2");
		vm2.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm1.getIdVmVcenter() + "/power/start", 200, emptyResult);
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm2.getIdVmVcenter() + "/power/start", 200, emptyResult,
				false);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/all/power/start", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(403, res.getInt("code"));
	}

	@Test
	public void testSetPowerAllBadAction() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);
		Token t = Token.findByValue(token);

		User student = new User("student", UserType.STUDENT, "student");
		student.saveOrUpdate();

		Vm vm1 = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm1.saveOrUpdate();

		Vm vm2 = new Vm(student, "id_vm2", "vmName2", "no link to vsphere2");
		vm2.saveOrUpdate();

		JSONObject emptyResult = new JSONObject();
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm1.getIdVmVcenter() + "/power/start", 200, emptyResult);
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm2.getIdVmVcenter() + "/power/start", 200, emptyResult,
				false);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/all/power/badAction", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}

	@Test
	public void testConsoleValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		String expected = "wss://192.168.4.33:443/ticket/f9f1db761e7006b7";

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.getRemoteConsoleUrl(eq(vm))).thenReturn(expected);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/console", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));

		String value = res.getString("value");
		assertEquals(expected, value);
	}

	@Test
	public void testConsoleInvalid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		User admin = new User("admin", UserType.ADMIN, "admin");
		admin.saveOrUpdate();

		Vm vm = new Vm(admin, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/console", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testConsoleError() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.getRemoteConsoleUrl(eq(vm))).thenReturn(null);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/console", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, res.getInt("code"));
	}

	@Test
	public void testConsoleNoVms() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/vm/vm-10/console", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}

	@Test
	public void testGetSnapshotsValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		Snapshot snap = new Snapshot(vm, "snapshot-456","name","desc");
		assertTrue(snap.saveOrUpdate());

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshots", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));

		assertEquals(1, res.getJSONArray("value").length());
		assertEquals("name", res.getJSONArray("value").getJSONObject(0).getString("name"));
	}

	@Test
	public void testGetSnapshotsInvalid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		User admin = new User("admin", UserType.ADMIN, "admin");
		admin.saveOrUpdate();

		Vm vm = new Vm(admin, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshots", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testGetSnapshotsNotFound() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/vm/qzfbzqfqzf/snapshots", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}

	@Test
	public void testCreateSnapshotValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.createSnapshot(eq(vm), eq("name"), eq("description"))).thenReturn("snapshot-000");

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "name");
		params.put("description", "description");

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testCreateSnapshotNotFound() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/161351/snapshot", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}

	@Test
	public void testCreateSnapshotInvalid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);

		User admin = new User("test2", UserType.STUDENT, "test2");
		admin.saveOrUpdate();

		Vm vm = new Vm(admin, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		HashMap<String, String> params = new HashMap<>();
		params.put("name", "name");
		params.put("description", "description");

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testCreateSnapshotBad() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
	}

	@Test
	public void testCreateSnapshotError() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.createSnapshot(eq(vm), eq("name"), eq("description"))).thenReturn(null);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "name");
		params.put("description", "description");

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, res.getInt("code"));
	}

	@Test
	public void testCreateSnapshotError2() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.createSnapshot(eq(vm), eq("name"), eq("description"))).thenThrow(Exception.class);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "name");
		params.put("description", "description");

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, res.getInt("code"));
	}

	@Test
	public void testDeleteSnapshotValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		Snapshot snap = new Snapshot(vm, "snapshot-123", "snapName", "snapDesc");
		snap.saveOrUpdate();

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.deleteSnapshot(eq(snap))).thenReturn(true);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("with_children", "false");

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/%s", vm.getIdVmVcenter(), snap.getId());
		HttpServletRequest request = TestUtils.createMockRequest("DELETE", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testDeleteSnapshotError() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		Snapshot snap = new Snapshot(vm, "snapshot-123", "snapName", "snapDesc");
		snap.saveOrUpdate();

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.deleteSnapshot(eq(vm), eq(111), eq(false))).thenReturn(false);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("with_children", "false");

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/%s", vm.getIdVmVcenter(), snap.getId());
		HttpServletRequest request = TestUtils.createMockRequest("DELETE", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, res.getInt("code"));
	}

	@Test
	public void testDeleteSnapshotError2() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		Snapshot snap = new Snapshot(vm, "snapshot-123", "snapName", "snapDesc");
		snap.saveOrUpdate();

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.deleteSnapshot(eq(vm), eq(111), eq(false))).thenThrow(Exception.class);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("with_children", "false");

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/%s", vm.getIdVmVcenter(), snap.getId());
		HttpServletRequest request = TestUtils.createMockRequest("DELETE", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, res.getInt("code"));
	}

	@Test
	public void testDeleteSnapshotBad() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		Snapshot snap = new Snapshot(vm, "snapshot-123", "snapName", "snapDesc");
		snap.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/abcde", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("DELETE", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
	}

	@Test
	public void testDeleteSnapshotInvalid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		User admin = new User("test2", UserType.STUDENT, "test2");
		admin.saveOrUpdate();

		Vm vm = new Vm(admin, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		Snapshot snap = new Snapshot(vm, "snapshot-123", "snapName", "snapDesc");
		snap.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("with_children", "false");

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/snapshot-123", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("DELETE", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testDeleteSnapshotNotFound() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/vm/dqgqzgqzgfq/snapshot/111", null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}

	@Test
	public void testRevertSnapshotValid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		Snapshot snap = new Snapshot(vm, "snapshot-123", "snapName", "snapDesc");
		snap.saveOrUpdate();

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.revertSnapshot(snap)).thenReturn(true);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/%s/revert", vm.getIdVmVcenter(), snap.getId());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testReverSnapshotError() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		Snapshot snap = new Snapshot(vm, "snapshot-123", "snapName", "snapDesc");
		snap.saveOrUpdate();

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.revertSnapshot(eq(vm), eq(111))).thenReturn(false);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/%s/revert", vm.getIdVmVcenter(), snap.getId());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, res.getInt("code"));
	}

	@Test
	public void testRevertSnapshotError2() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		Snapshot snap = new Snapshot(vm, "snapshot-123", "snapName", "snapDesc");
		snap.saveOrUpdate();

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.revertSnapshot(eq(vm), eq(111))).thenThrow(Exception.class);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/%s", vm.getIdVmVcenter(), snap.getId());
		HttpServletRequest request = TestUtils.createMockRequest("DELETE", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, res.getInt("code"));
	}

	@Test
	public void testRevertSnapshotBad() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		Snapshot snap = new Snapshot(vm, "snapshot-123", "snapName", "snapDesc");
		snap.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/snapshot-123", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
	}

	@Test
	public void testRevertSnapshotInvalid() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		User admin = new User("test2", UserType.STUDENT, "test2");
		admin.saveOrUpdate();

		Vm vm = new Vm(admin, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		Snapshot snap = new Snapshot(vm, "snapshot-123", "snapName", "snapDesc");
		snap.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("with_children", "false");

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/snapshot-123/revert", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testRevertSnapshotNotFound() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/dqgqzgqzgfq/snapshot/111/revert", null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}

	@Test
	public void testCloneVmSuccessSameUserNoParameters() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		Vm clone = new Vm(t.getUser(), "id_vm_clone", "vmName", "no link to vsphere");
		
        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/"+clone.getIdVmVcenter(), 200,
                new JSONObject().put("value", new JSONObject()));

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.cloneVm(eq(vm), eq("vmName"),eq(false))).thenReturn(clone.getIdVmVcenter());

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/clone", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testCloneVmSuccessSameUserWithParameters() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		Vm clone = new Vm(t.getUser(), "id_vm_clone", "clonedVM", "new descirption - not the same");
		
        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/"+clone.getIdVmVcenter(), 200,
                new JSONObject().put("value", new JSONObject()));

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.cloneVm(eq(vm),eq("clonedVM"),eq(false))).thenReturn(clone.getIdVmVcenter());

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", clone.getName());
		params.put("desc", clone.getDescription());

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/clone", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testCloneVmFailBadVm() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();

		StringWriter writer = new StringWriter();
		String requestFormatted = "/api/vm/0/clone";
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(404, res.getInt("code"));
	}

	/**
	 * class used to mock a ServletOutputStream
	 * @author Kalioz
	 *
	 */
	public class StubServletOutputStream extends ServletOutputStream {
		public ByteArrayOutputStream baos = new ByteArrayOutputStream();
		public void write(int i) throws IOException {
			baos.write(i);
		}
		@Override
		public boolean isReady() {return false;}
		@Override
		public void setWriteListener(WriteListener writeListener) {}
		
		public boolean hasBeenWritten(){
			return baos.size() != 0;
		}
	}
	
	@Test
	public void testDownloadOVASuccess() throws Exception{
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		String pathToOVAtest = "vmware_compatible_light.ova";
		ClassLoader classLoader = getClass().getClassLoader();
		File savedFile = new File(classLoader.getResource(pathToOVAtest).getFile());
		
		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.downloadAndPackageOVA(vm)).thenReturn(savedFile);
		
		PowerMockito.mockStatic(VSphereConnector.class);
		when(VSphereConnector.getVmPowerState(vm)).thenReturn(VmPowerState.POWERED_OFF);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();

		String requestFormatted = String.format("/api/vm/%s/export/ova", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, params, headers);
		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		
		assertEquals(200, res.getInt("code"));
		assertNotNull(res.getJSONObject("value").getString("url"));
		System.out.println(res.getJSONObject("value").getString("url"));
		//get the DownloadLink for this VM
		DownloadLink link = DownloadLink.findByExternalLink(res.getJSONObject("value").getString("external_link"));
		assertNotNull(link);
		System.out.println(link.toString());
		
	}
	
	@Test
	public void testDownloadOVAFailVmOn() throws Exception{
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		String pathToOVAtest = "vmware_compatible_light.ova";
		ClassLoader classLoader = getClass().getClassLoader();
		File savedFile = new File(classLoader.getResource(pathToOVAtest).getFile());
		
		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.downloadAndPackageOVA(vm)).thenReturn(savedFile);
		
		PowerMockito.mockStatic(VSphereConnector.class);
		when(VSphereConnector.getVmPowerState(vm)).thenReturn(VmPowerState.POWERED_ON);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();

		
		String requestFormatted = String.format("/api/vm/%s/export/ova", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, params, headers);
		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new VMsServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}
	
	@Test
	public void testCloneSnapshotSuccess() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		Snapshot snapshot = new Snapshot(vm, "snapshot-123", "snapounette", "desc");
		snapshot.saveOrUpdate();
		
		final String newName = "clonedd";
		final String newDescription="clonedd desc";

		Vm clone = new Vm(t.getUser(), "id_vm_clone", newName, newDescription);

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", TestUtils.getVmMockVcenterJSON(vm, true));
		TestUtils.mockVSphereAllResponse(200, vsphereRes);

		PowerMockito.mockStatic(VCenterManager.class);
		when(VCenterManager.vmFromSnapshot(eq(snapshot), eq(newName))).thenReturn(clone.getIdVmVcenter());

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", clone.getName());
		params.put("desc", clone.getDescription());

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/%d/clone", vm.getIdVmVcenter(), snapshot.getId());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}
	
	@Test
	public void testCloneSnapshotBadArgs_VmNull() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);
		
		PowerMockito.mockStatic(VCenterManager.class);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/no_vm/snapshot/no_snapsot/clone", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(404, res.getInt("code"));
	}
	
	@Test
	public void testCloneSnapshotBadArgs_NoRightsVm() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);
		
		User student = new User("student", UserType.STUDENT, "student");
		student.saveOrUpdate();

		Vm vm = new Vm(student, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		PowerMockito.mockStatic(VCenterManager.class);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/no_snapshot/clone", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(403, res.getInt("code"));
	}
	
	@Test
	public void testCloneSnapshotBadArgs_noSnapshot() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();
		
		PowerMockito.mockStatic(VCenterManager.class);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();

		StringWriter writer = new StringWriter();
		String requestFormatted = String.format("/api/vm/%s/snapshot/no_snapshot/clone", vm.getIdVmVcenter());
		HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}
	
	@Test
    public void testCloneSnapshot_clone_failed() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();


        Snapshot snapshot = new Snapshot(vm, "snapshot-123", "na", "de");
        snapshot.saveOrUpdate();

        final String newName = "clonedd";
        final String newDescription = "clonedd desc";

        Vm clone = new Vm(t.getUser(), "id_vm_clone", newName, newDescription);

        PowerMockito.mockStatic(VCenterManager.class);
        when(VCenterManager.vmFromSnapshot(eq(snapshot), eq(newName))).thenThrow(new IOException());

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HashMap<String, String> params = new HashMap<>();
        params.put("name", clone.getName());
        params.put("desc", clone.getDescription());

        StringWriter writer = new StringWriter();
        String requestFormatted = String.format("/api/vm/%s/snapshot/%d/clone", vm.getIdVmVcenter(), snapshot.getId());
        HttpServletRequest request = TestUtils.createMockRequest("POST", requestFormatted, params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(500, res.getInt("code"));
    }

    @Test
    public void testCreateEthernet() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Group group = new Group("test", "test");
        group.saveOrUpdate();
        Network network = new Network("test", "test");
        network.addGroup(group);
        network.saveOrUpdate();

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        JSONObject vsphereRes = new JSONObject();
        vsphereRes.put("value", 12345);
        TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet", 200,
                vsphereRes);

        HashMap<String, String> params = new HashMap<>();
        params.put("network", "" + network.getId());

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet", params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(200, res.getInt("code"));
    }

	@Test
	public void testCreateEthernetLocal() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", 12345);
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet", 200,
				vsphereRes);

		HashMap<String, String> params = new HashMap<>();
		params.put("network", "local");

		PowerMockito.mockStatic(VCenterManager.class);
		PowerMockito.doReturn("net-123").when(VCenterManager.class);
		VCenterManager.createNetwork(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyBoolean());

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);

		assertEquals(200, res.getInt("code"));

		User user = User.findById(t.getUser().getId());
		assertEquals("net-123", user.getPrivateNetwork());
	}

	@Test
	public void testCreateEthernetLocalError() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", 12345);
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet", 200,
				vsphereRes);

		HashMap<String, String> params = new HashMap<>();
		params.put("network", "local");

		PowerMockito.mockStatic(VCenterManager.class);
		PowerMockito.doReturn(null).when(VCenterManager.class);
		VCenterManager.createNetwork(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyBoolean());

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);

		assertEquals(500, res.getInt("code"));
	}

    @Test
    public void testCreateEthernetBadRequest() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(400, res.getInt("code"));
    }

    @Test
    public void testCreateEthernetNotFound() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/vm/14345/ethernet", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(404, res.getInt("code"));
    }

    @Test
    public void testCreateEthernetError() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Group group = new Group("test", "test");
        group.saveOrUpdate();
        Network network = new Network("test", "test");
        network.addGroup(group);
        network.saveOrUpdate();

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        JSONObject vsphereRes = new JSONObject();
        vsphereRes.put("value", 12345);
        TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet", 404,
                new JSONObject());

        HashMap<String, String> params = new HashMap<>();
        params.put("network", "" + network.getId());

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet", params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(502, res.getInt("code"));
    }

    @Test
    public void testChangeEthernet() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Group group = new Group("test", "test");
        group.saveOrUpdate();
        Network network = new Network("test", "test");
        network.addGroup(group);
        network.saveOrUpdate();

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345", 200,
                new JSONObject());
        TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345/connect", 200,
                new JSONObject(), false);

        HashMap<String, String> params = new HashMap<>();
        params.put("network", "" + network.getId());
        params.put("connected", "true");

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet/12345", params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(200, res.getInt("code"));
    }

	@Test
	public void testChangeEthernetLocal() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		User u = t.getUser();
		u.setPrivateNetwork("net-123");
		u.saveOrUpdate();

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345", 200,
				new JSONObject());
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345/connect", 200,
				new JSONObject(), false);

		HashMap<String, String> params = new HashMap<>();
		params.put("network", "local");
		params.put("connected", "true");

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet/12345", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);

		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testChangeEthernetLocalError() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345", 200,
				new JSONObject());
		TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345/connect", 200,
				new JSONObject(), false);

		PowerMockito.mockStatic(VCenterManager.class);
		PowerMockito.doReturn(null).when(VCenterManager.class);
		VCenterManager.createNetwork(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyBoolean());

		HashMap<String, String> params = new HashMap<>();
		params.put("network", "local");
		params.put("connected", "true");

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet/12345", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new VMsServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);

		assertEquals(500, res.getInt("code"));
	}

    @Test
    public void testChangeEthernetBadRequest() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet/bla", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(400, res.getInt("code"));
    }

    @Test
    public void testChangeEthernetError() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Group group = new Group("test", "test");
        group.saveOrUpdate();
        Network network = new Network("test", "test");
        network.addGroup(group);
        network.saveOrUpdate();

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345", 500,
                new JSONObject());

        HashMap<String, String> params = new HashMap<>();
        params.put("network", "" + network.getId());

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet/12345", params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(502, res.getInt("code"));
    }

    @Test
    public void testChangeEthernetError2() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        TestUtils.mockVSphereResponse("POST", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345/disconnect", 500,
                new JSONObject());

        HashMap<String, String> params = new HashMap<>();
        params.put("connected", "false");

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet/12345", params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(502, res.getInt("code"));
    }

    @Test
    public void testChangeEthernetNotFound() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/vm/12345/ethernet/12345", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(404, res.getInt("code"));
    }

    @Test
    public void testDeleteEthernet() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        TestUtils.mockVSphereResponse("DELETE", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345", 200,
                new JSONObject());

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet/12345", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(200, res.getInt("code"));
    }

    @Test
    public void testDeleteEthernetError() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        TestUtils.mockVSphereResponse("DELETE", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/ethernet/12345", 500,
                new JSONObject());

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet/12345", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(502, res.getInt("code"));
    }

    @Test
    public void testDeleteEthernetBadRequest() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Token t = Token.findByValue(token);

        Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
        vm.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/vm/" + vm.getIdVmVcenter() + "/ethernet/bla", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(400, res.getInt("code"));
    }

    @Test
    public void testDeleteEthernetNotFound() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/vm/12345/ethernet/12345", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new VMsServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);

        assertEquals(404, res.getInt("code"));
    }
}
