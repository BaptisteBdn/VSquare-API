package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.Token;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.Vm;
import fr.eseo.vsquare.utils.*;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"java.*", "javax.*", "org.*", "fr.eseo.vsquare.model.*", "fr.eseo.vsquare.utils.DatabaseManager", "com.vmware.vim25.*"})
@PrepareForTest({VSphereManager.class, LDAPUtils.class, ServletUtils.class})
public class ImportServletTest {
	
	@BeforeClass
    public static void initClass(){
        assertTrue(VCenterManager.init());
        VSphereManager.init(Utils.getString("vsphere_host"));
    }

    @Before
    public void init() throws Exception{
        TestUtils.initTest(true);
    }

    @AfterClass
    public static void cleanUp(){
        VCenterManager.exit();
    }
	
	@Test
	public void importOVASuccess() throws ServletException, IOException {
		ClassLoader classLoader = getClass().getClassLoader();
    	File file = new File(classLoader.getResource("vmware_compatible_light.ova").getFile());
    	
    	String token = TestUtils.login("test", "test", User.UserType.ADMIN);
    	assertEquals(0, Token.findByValue(token).getUser().getVms().size());
		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("vmName", "new VM");
		
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/import/ova", params, headers);
		TestUtils.addPartMockRequest(request, "ovaFile", file);
		
		
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new ImportServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("200 on POST:/api/group", HttpServletResponse.SC_OK, res.getInt("code"));
		
		assertEquals(1, Token.findByValue(token).getUser().getVms().size());
		
		Vm vm = Token.findByValue(token).getUser().getVms().get(0);
		VSphereConnector.deleteVm(vm);
		vm.delete();
	}
	
	@Test
	public void importOVAbadFile() throws ServletException, IOException{
		ClassLoader classLoader = getClass().getClassLoader();
    	File file = new File(classLoader.getResource("vmware_compatible_light/main.ovf").getFile());
    	
    	String token = TestUtils.login("test", "test", User.UserType.ADMIN);
    	assertEquals(0, Token.findByValue(token).getUser().getVms().size());
		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("vmName", "new VM");
		
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/import/ova", params, headers);
		TestUtils.addPartMockRequest(request, "ovaFile", file);
		
		
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new ImportServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("400 on POST:/api/group", HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
		
		assertEquals(0, Token.findByValue(token).getUser().getVms().size());
	}
	
	@Test
	public void testImportVmMaximumNumber() throws ServletException, IOException{
		ClassLoader classLoader = getClass().getClassLoader();
    	File file = new File(classLoader.getResource("vmware_compatible_light.ova").getFile());
    	
    	String token = TestUtils.login("test", "test", User.UserType.STUDENT);
    	User user = Token.findByValue(token).getUser();
    	assertEquals(0, user.getVms().size());
    	
    	user.getEffectivePermission().setVmCount(0);
    	
		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("vmName", "new VM");
		
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/import/ova", params, headers);
		TestUtils.addPartMockRequest(request, "ovaFile", file);
		
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new ImportServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("403 on POST:/api/group", HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
		assertEquals(0, user.getVms().size());
	}
	
	@Test
	public void testImportOVABadRequest() throws ServletException, IOException{
    	String token = TestUtils.login("test", "test", User.UserType.ADMIN);
    	User user = Token.findByValue(token).getUser();
    	assertEquals(0, user.getVms().size());
    	
		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("vmName", "new VM");
		
		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/import/ova", params, headers);
		
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new ImportServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("400 on POST:/api/group", HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
		assertEquals(0, user.getVms().size());
	}

}
