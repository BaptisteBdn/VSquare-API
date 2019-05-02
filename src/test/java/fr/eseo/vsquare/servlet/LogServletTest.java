package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.ErrorLog;
import fr.eseo.vsquare.model.EventLog;
import fr.eseo.vsquare.model.EventLog.EventAction;
import fr.eseo.vsquare.model.Group;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.LDAPUtils;
import fr.eseo.vsquare.utils.ServletUtils;
import fr.eseo.vsquare.utils.Utils;
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
import java.util.Date;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"java.*","javax.*","org.*","fr.eseo.vsquare.model.*","fr.eseo.vsquare.utils.DatabaseManager"})
@PrepareForTest({LDAPUtils.class, ServletUtils.class})
public class LogServletTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
	}

	@Test
	public void testServletOptions() throws ServletException, IOException {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("OPTIONS", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new LogServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testServletNoAuth() throws ServletException, IOException {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new LogServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
	}
	
	@Test
	public void testServletError() throws ServletException, IOException {
		PowerMockito.mockStatic(ServletUtils.class);
		Mockito.when(ServletUtils.verifyToken(Mockito.any(), Mockito.any())).thenThrow(new MockitoException("Test error do not panic"));

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(new StringWriter());
		new LogServlet().service(request, response);
		
		assertEquals(1,ErrorLog.getAll().size());
	}
	
	@Test
	public void testListEventNoRight() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/log/events", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new LogServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN,res.getInt("code"));
	}
	
	@Test
	public void testListErrorsNoRight() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/log/errors", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new LogServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN,res.getInt("code"));
	}
	
	@Test
	public void testListEventBadRequest1() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/log/events", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new LogServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_BAD_REQUEST,res.getInt("code"));
	}
	
	@Test
	public void testListEventBadRequest2() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		HashMap<String,String> params = new HashMap<>();
		params.put("page", "test");
		params.put("page_size", "5");
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/log/events", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new LogServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_BAD_REQUEST,res.getInt("code"));
	}
	
	@Test
	public void testListErrorsBadRequest1() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/log/errors", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new LogServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_BAD_REQUEST,res.getInt("code"));
	}
	
	@Test
	public void testListErrorsBadRequest2() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		HashMap<String,String> params = new HashMap<>();
		params.put("page", "test");
		params.put("page_size", "5");
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/log/errors", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new LogServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_BAD_REQUEST,res.getInt("code"));
	}
	
	@Test
	public void testListEventEmpty() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		HashMap<String,String> params = new HashMap<>();
		params.put("page", "0");
		params.put("page_size", "100");
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/log/events", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new LogServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200,res.getInt("code"));
		JSONObject value = res.getJSONObject("value");
		assertEquals(0,value.getInt("total_count"));
		assertEquals(0,value.getInt("count"));
		assertEquals(0,value.getJSONArray("list").length());
	}
	
	@Test
	public void testListErrorsEmpty() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		HashMap<String,String> params = new HashMap<>();
		params.put("page", "0");
		params.put("page_size", "100");
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/log/errors", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new LogServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200,res.getInt("code"));
		JSONObject value = res.getJSONObject("value");
		assertEquals(0,value.getInt("total_count"));
		assertEquals(0,value.getInt("count"));
		assertEquals(0,value.getJSONArray("list").length());
	}
	
	@Test
	public void testListEvent() throws ServletException, IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		for(int i = 0; i < 100; i++) {
			Group g = new Group(""+i, ""+i);
			g.saveOrUpdate();
			EventLog e = new EventLog(u, EventAction.CREATE, g);
			e.saveOrUpdate();
			TestUtils.changeVSquareObjectDate(e,new Date(System.currentTimeMillis() - i* 1000));
			e.saveOrUpdate();
		}
		
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		HashMap<String,String> params = new HashMap<>();
		params.put("page", "3");
		params.put("page_size", "10");
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/log/events", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new LogServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200,res.getInt("code"));
		JSONObject value = res.getJSONObject("value");
		assertEquals(100,value.getInt("total_count"));
		assertEquals(10,value.getInt("count"));
		assertEquals(10,value.getJSONArray("list").length());
		assertEquals("30", value.getJSONArray("list").getJSONObject(0).getJSONObject("object").getString("name"));
		assertEquals("39", value.getJSONArray("list").getJSONObject(9).getJSONObject("object").getString("name"));
	}
	
	@Test
	public void testListErrors() throws ServletException, IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		for(int i = 0; i < 100; i++) {
            ErrorLog e = new ErrorLog(u, "" + i, "{'test':'test'}");
			e.saveOrUpdate();
			TestUtils.changeVSquareObjectDate(e,new Date(System.currentTimeMillis() - i* 1000));
			e.saveOrUpdate();
		}
		
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		HashMap<String,String> params = new HashMap<>();
		params.put("page", "3");
		params.put("page_size", "10");
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/log/errors", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new LogServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200,res.getInt("code"));
		JSONObject value = res.getJSONObject("value");
		assertEquals(100,value.getInt("total_count"));
		assertEquals(10,value.getInt("count"));
		assertEquals(10,value.getJSONArray("list").length());
		assertEquals("30", value.getJSONArray("list").getJSONObject(0).getString("error"));
        assertEquals("test", value.getJSONArray("list").getJSONObject(0).getJSONObject("request").getString("test"));
		assertEquals("39", value.getJSONArray("list").getJSONObject(9).getString("error"));
	}
}
