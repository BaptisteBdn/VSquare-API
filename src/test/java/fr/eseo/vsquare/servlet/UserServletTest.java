package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.ErrorLog;
import fr.eseo.vsquare.model.EventLog;
import fr.eseo.vsquare.model.EventLog.EventAction;
import fr.eseo.vsquare.model.Token;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.LDAPUtils;
import fr.eseo.vsquare.utils.ServletUtils;
import fr.eseo.vsquare.utils.Utils;
import org.json.JSONArray;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"java.*","javax.*","org.*","fr.eseo.vsquare.model.*","fr.eseo.vsquare.utils.DatabaseManager"})
@PrepareForTest({LDAPUtils.class, ServletUtils.class})
public class UserServletTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
	}

	@Test
	public void testServletOptions() throws ServletException, IOException {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("OPTIONS", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new UserServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testServletNoAuth() throws ServletException, IOException {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new UserServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
	}
	
	@Test
	public void testServletError() throws ServletException, IOException {
		PowerMockito.mockStatic(ServletUtils.class);
		Mockito.when(ServletUtils.verifyToken(Mockito.any(), Mockito.any())).thenThrow(new MockitoException("Test error do not panic"));

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(new StringWriter());
		new UserServlet().service(request, response);
		
		assertEquals(1,ErrorLog.getAll().size());
	}
	
	@Test
	public void testListUsersAdmin() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		User u0 = Token.findByValue(token).getUser();
		
		User u1 = new User("test1", UserType.REFERENT, "test1");
		u1.saveOrUpdate();
		
		User u2 = new User("test2", UserType.STUDENT, "test2");
		u2.saveOrUpdate();
		
		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/user", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new UserServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_OK,res.getInt("code"));
		
		JSONArray list = res.getJSONArray("value");
		assertEquals(3, list.length());
		ArrayList<Integer> listIds = new ArrayList<>();
		for(int i = 0; i < 3; i++)
			listIds.add(list.getJSONObject(i).getInt("id"));
		assertTrue(listIds.contains(u0.getId()));
		assertTrue(listIds.contains(u1.getId()));
		assertTrue(listIds.contains(u2.getId()));
	}
	
	@Test
	public void testListUsersQuery() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

        User u1 = new User("testQueryTest", UserType.REFERENT, "test");
		u1.saveOrUpdate();

        User u2 = new User("test2", UserType.STUDENT, "test queryTest");
		u2.saveOrUpdate();
		
		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		HashMap<String,String> params = new HashMap<>();
		params.put("query", "query");
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/user", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new UserServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_OK,res.getInt("code"));
		
		JSONArray list = res.getJSONArray("value");
		assertEquals(2, list.length());
		ArrayList<Integer> listIds = new ArrayList<>();
		for(int i = 0; i < 2; i++)
			listIds.add(list.getJSONObject(i).getInt("id"));
		assertTrue(listIds.contains(u1.getId()));
		assertTrue(listIds.contains(u2.getId()));
	}
	
	@Test
	public void testListUsersReferent() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);
		
		User u1 = new User("test1", UserType.STUDENT, "test1");
		u1.saveOrUpdate();
		
		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/user", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new UserServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_OK,res.getInt("code"));
		
		JSONArray list = res.getJSONArray("value");
		assertEquals(1, list.length());
		assertEquals((int)u1.getId(), list.getJSONObject(0).getInt("id"));
	}
	
	@Test
	public void testListUsersForbidden() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/user", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new UserServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN,res.getInt("code"));
	}
	
	@Test
	public void testGetUserInfo() throws Exception {
        User u = new User("user", UserType.REFERENT, "userCn");
		u.saveOrUpdate();
		
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET","/api/user/"+u.getId(), null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new UserServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200,res.getInt("code"));
		
		JSONObject value = res.getJSONObject("value");
		assertEquals((int)u.getId(), value.getInt("id"));
		assertEquals(u.getCommonName(), value.getString("common_name"));
	}
	
	@Test
	public void testEditUserInfoForbidden() throws Exception {
		User u = new User("user", UserType.REFERENT, "user");
		u.saveOrUpdate();
		
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST","/api/user/"+u.getId(), null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new UserServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN,res.getInt("code"));
	}
	
	@Test
	public void testEditUserInfoInvalidType() throws Exception {
		User u = new User("user", UserType.REFERENT, "user");
		u.saveOrUpdate();
		
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		HashMap<String,String> params = new HashMap<>();
		params.put("type", "invalid");
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST","/api/user/"+u.getId(), params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new UserServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_BAD_REQUEST,res.getInt("code"));
	}
	
	@Test
	public void testEditUserInfoOwnType() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		User user = Token.findByValue(token).getUser();
		
		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		HashMap<String,String> params = new HashMap<>();
		params.put("type", "referent");
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST","/api/user/"+user.getId(), params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new UserServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN,res.getInt("code"));
	}
	
	@Test
	public void testEditUserInfoType() throws Exception {
		User u = new User("user", UserType.REFERENT, "user");
		u.saveOrUpdate();
		
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		HashMap<String,String> params = new HashMap<>();
		params.put("type", "admin");
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("POST","/api/user/"+u.getId(), params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new UserServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200,res.getInt("code"));
		
		u = User.findById(u.getId());
		assertEquals(UserType.ADMIN, u.getType());
		
		List<EventLog> events = EventLog.getAll();
		assertEquals(1, events.size());
		assertEquals(Token.findByValue(token).getUser(), events.get(0).getUser());
		assertEquals(EventAction.EDIT, events.get(0).getAction());
		assertEquals(u, events.get(0).getObject());
	}
	
	
	@Test
	public void testScrapLDAP() throws Exception {
		List<User> users = User.getAll();
		assertTrue(users.isEmpty());
		
		String token = TestUtils.login("test", "test", UserType.ADMIN);
		
		ArrayList<String> list = new ArrayList<>();
		list.add("test2");

        Mockito.when(LDAPUtils.getCommonName("test2")).thenReturn("unknown");
		Mockito.when(LDAPUtils.scrapLDAP()).thenReturn(list);
		
		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/user/action/ldap", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new UserServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
		
		users = User.getAll();
		assertEquals(2, users.size());
		
	}
	
	@Test
	public void testScrapLDAPForbidden() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);

		HashMap<String,String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("PUT","/api/user/action/ldap", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		new UserServlet().service(request, response);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN,res.getInt("code"));
	}

}
