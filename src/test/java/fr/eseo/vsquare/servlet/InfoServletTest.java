package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.LDAPUtils;
import fr.eseo.vsquare.utils.Utils;
import fr.eseo.vsquare.utils.VSphereManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * 
 * @author Baptiste Beduneau Test admin servlet
 */

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "java.*", "javax.*", "org.*", "fr.eseo.vsquare.model.*", "fr.eseo.vsquare.utils.DatabaseManager" })
@PrepareForTest({ VSphereManager.class, LDAPUtils.class })
public class InfoServletTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
	}

	@Test
	public void testServletOptions() throws ServletException, IOException {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("OPTIONS", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new InfoServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testAPIInfo() throws Exception {
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new InfoServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);

		assertTrue(res.getJSONObject("value").has("code_version"));
		assertTrue(res.getJSONObject("value").has("db_version"));
		assertTrue(res.getJSONObject("value").has("build"));
	}

	@Test
	public void testListHost() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONArray());
		vsphereRes.getJSONArray("value").put(new JSONObject());
		vsphereRes.getJSONArray("value").getJSONObject(0).put("host", "host-12");
		vsphereRes.getJSONArray("value").getJSONObject(0).put("name", "192.168.4.33");
		vsphereRes.getJSONArray("value").getJSONObject(0).put("connection_state", "CONNECTED");
		vsphereRes.getJSONArray("value").getJSONObject(0).put("power_state", "POWERED_ON");
		TestUtils.mockVSphereResponse("GET", "/vcenter/host", 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/hosts", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new InfoServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertTrue(res.has("code"));
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
	public void testListHostNoToken() throws Exception {
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/hosts", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new InfoServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
	}

	@Test
	public void testListHostForbidden() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/hosts", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new InfoServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testListDatastore() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONArray());
		vsphereRes.getJSONArray("value").put(new JSONObject());
		vsphereRes.getJSONArray("value").getJSONObject(0).put("datastore", "datastore-13");
		vsphereRes.getJSONArray("value").getJSONObject(0).put("name", "datastore1-33");
		vsphereRes.getJSONArray("value").getJSONObject(0).put("type", "VMFS");
		vsphereRes.getJSONArray("value").getJSONObject(0).put("free_space", 23429382144L);
		vsphereRes.getJSONArray("value").getJSONObject(0).put("capacity", 34896609280L);
		TestUtils.mockVSphereResponse("GET", "/vcenter/datastore", 200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/datastores", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new InfoServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);

		assertTrue(res.has("code"));
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
	public void testListDatastoreForbidden() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/datastores", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new InfoServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testDetailDatastoreValidArg() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

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

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/datastore/datastore-13", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new InfoServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertTrue(res.has("code"));
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
	public void testDetailDatastoreForbidden() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/datastore/datastore-13", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new InfoServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testDetailDatastoreInvalidArg() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		TestUtils.mockVSphereResponse("GET", "/vcenter/datastore/datastore-13", 404, new JSONObject());

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/datastore/datastore-13", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new InfoServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(404, res.get("code"));
	}

    @Test
    public void testDetailDatastoreInvalidArg2() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        TestUtils.mockVSphereResponse("GET", "/vcenter/datastore/datastore-13", 404, new JSONObject());

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/datastore/https%3A%2F%2Fmonip.org", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new InfoServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(400, res.get("code"));
    }

	@Test
	public void testDetailDatastoreError() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		TestUtils.mockVSphereResponse("GET", "/vcenter/datastore/datastore-13", 400, new JSONObject());

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/datastore/datastore-13", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new InfoServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(502, res.get("code"));
	}

    @Test
    public void testListNetwork() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        JSONObject vsphereRes = new JSONObject();
        vsphereRes.put("value", new JSONArray());
        vsphereRes.getJSONArray("value").put(new JSONObject());
        vsphereRes.getJSONArray("value").getJSONObject(0).put("name", "VM Network");
        vsphereRes.getJSONArray("value").getJSONObject(0).put("type", "STANDARD_PORTGROUP");
        vsphereRes.getJSONArray("value").getJSONObject(0).put("network", "network-12");
        TestUtils.mockVSphereResponse("GET", "/vcenter/network", 200, vsphereRes);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/networks", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new InfoServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertTrue(res.has("code"));
        assertFalse(res.isNull("value"));
        JSONArray value = (JSONArray) res.get("value");
        assertTrue(((JSONObject) (value.get(0))).has("name"));
        assertTrue(((JSONObject) (value.get(0))).has("type"));
        assertTrue(((JSONObject) (value.get(0))).has("network"));
    }

    @Test
    public void testListNetworkNoToken() throws Exception {
        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/networks", null, null);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new InfoServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
    }

    @Test
    public void testListNetworkForbidden() throws Exception {
        String token = TestUtils.login("test", "test", UserType.REFERENT);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/networks", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new InfoServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
    }

}
