package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.*;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.*;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoException;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.HashMap;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"java.*", "javax.*", "org.*", "fr.eseo.vsquare.model.*", "fr.eseo.vsquare.utils.DatabaseManager"})
@PrepareForTest({LDAPUtils.class, ServletUtils.class, VCenterManager.class, VSphereConnector.class})
public class NetworkServletTest {

    @Before
    public void setUp() throws Exception {
        TestUtils.initTest(true);
    }

    @Test
    public void testServletOptions() throws ServletException, IOException {
        StringWriter writer = new StringWriter();

        HttpServletRequest request = TestUtils.createMockRequest("OPTIONS", "", null, null);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new NetworkServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(200, res.getInt("code"));
    }

    @Test
    public void testServletNoAuth() throws ServletException, IOException {
        StringWriter writer = new StringWriter();

        HttpServletRequest request = TestUtils.createMockRequest("", "", null, null);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new NetworkServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
    }

    @Test
    public void testServletError() throws ServletException, IOException {
        PowerMockito.mockStatic(ServletUtils.class);
        Mockito.when(ServletUtils.verifyToken(Mockito.any(), Mockito.any())).thenThrow(new MockitoException("Test error do not panic"));

        HttpServletRequest request = TestUtils.createMockRequest("", "", null, null);
        HttpServletResponse response = TestUtils.createMockResponse(new StringWriter());
        new NetworkServlet().service(request, response);

        assertEquals(1, ErrorLog.getAll().size());
    }


    @Test
    public void testGetAvailableNetworksNoNetworks() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        User u = Token.findByValue(token).getUser();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/networks", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

        assertEquals(0, res.getJSONArray("value").length());
    }

    @Test
    public void testGetAvailableNetworks() throws Exception {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        User u = Token.findByValue(token).getUser();

        Group g1 = new Group("test", "test");
        g1.saveOrUpdate();

        Network n1 = new Network("test", "test");
        n1.addGroup(g1);
        n1.saveOrUpdate();

        u.addGroup(g1);
        u.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/networks", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

        assertEquals(1, res.getJSONArray("value").length());
        assertEquals((int) n1.getId(), res.getJSONArray("value").getJSONObject(0).getInt("id"));
    }

    @Test
    public void testGetAvailableNetworksAdmin() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Group g1 = new Group("test", "test");
        g1.saveOrUpdate();

        Network n1 = new Network("test", "test");
        n1.addGroup(g1);
        n1.saveOrUpdate();

        PowerMockito.mockStatic(VCenterManager.class);
        PowerMockito.doReturn(5).when(VCenterManager.class);
        VCenterManager.getNetworkMaxPorts(Mockito.eq("test"));

        HashMap<String, String> params = new HashMap<>();
        params.put("details", "true");

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/networks", params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

        assertEquals(1, res.getJSONArray("value").length());
        assertEquals((int) n1.getId(), res.getJSONArray("value").getJSONObject(0).getInt("id"));
        assertEquals(5, res.getJSONArray("value").getJSONObject(0).getInt("port_num"));
    }

    @Test
    public void testAddNetwork() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        HashMap<String, String> params = new HashMap<>();
        params.put("name", URLEncoder.encode("test network", "UTF-8"));
        params.put("port_num", "5");

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        PowerMockito.mockStatic(VCenterManager.class);
        PowerMockito.doReturn("net-123").when(VCenterManager.class);
        VCenterManager.createNetwork(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyBoolean());

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/networks", params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

        assertEquals(1, Network.getAll().size());
        Network network = Network.findByIdNetworkVcenter("net-123");
        assertNotNull(network);
        assertEquals("test network", network.getName());
    }


    @Test
    public void testAddNetworkBadRequest() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/networks", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
    }

    @Test
    public void testAddNetworkForbidden() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/networks", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
    }

    @Test
    public void testAddNetworkError() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        HashMap<String, String> params = new HashMap<>();
        params.put("name", "test network");
        params.put("port_num", "5");

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        PowerMockito.mockStatic(VCenterManager.class);
        PowerMockito.doReturn(null).when(VCenterManager.class);
        VCenterManager.createNetwork(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyBoolean());

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/networks", params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, res.getInt("code"));
    }

    @Test
    public void testEditNetwork() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Network network = new Network("test network", "net-123");
        network.saveOrUpdate();

        HashMap<String, String> params = new HashMap<>();
        params.put("name", "test network 2");
        params.put("port_num", "5");

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        PowerMockito.mockStatic(VCenterManager.class);
        PowerMockito.doReturn(true).when(VCenterManager.class);
        VCenterManager.editNetwork(Mockito.anyString(), Mockito.anyInt());

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/network/" + network.getId(), params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

        assertEquals(1, Network.getAll().size());
        network = Network.findByIdNetworkVcenter("net-123");
        assertNotNull(network);
        assertEquals(params.get("name"), network.getName());
    }

    @Test
    public void testEditNetworkForbidden() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/network/1651", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
    }

    @Test
    public void testEditNetworkNotFound() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/network/1651", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
    }

    @Test
    public void testEditNetworkError() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Network network = new Network("test network", "net-123");
        network.saveOrUpdate();

        HashMap<String, String> params = new HashMap<>();
        params.put("name", "test network 2");
        params.put("port_num", "5");

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        PowerMockito.mockStatic(VCenterManager.class);
        PowerMockito.doReturn(false).when(VCenterManager.class);
        VCenterManager.editNetwork(Mockito.anyString(), Mockito.anyInt());

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/network/" + network.getId(), params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, res.getInt("code"));
    }

    @Test
    public void testDeleteNetwork() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Network network = new Network("test network", "net-123");
        network.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        PowerMockito.mockStatic(VCenterManager.class);
        PowerMockito.doReturn(true).when(VCenterManager.class);
        VCenterManager.deleteNetwork(Mockito.anyString());

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/network/" + network.getId(), null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

        network = Network.findByIdNetworkVcenter("net-123");
        assertNull(network);
    }

    @Test
    public void testDeleteNetworkError() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Network network = new Network("test network", "net-123");
        network.saveOrUpdate();

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        PowerMockito.mockStatic(VCenterManager.class);
        PowerMockito.doReturn(false).when(VCenterManager.class);
        VCenterManager.deleteNetwork(Mockito.anyString());

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/network/" + network.getId(), null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, res.getInt("code"));
    }

    @Test
    public void testDeleteNetworkForbidden() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.STUDENT);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/network/9816", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
    }

    @Test
    public void testDeleteNetworkNotFound() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        StringWriter writer = new StringWriter();
        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/network/1651", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new NetworkServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
    }
}
