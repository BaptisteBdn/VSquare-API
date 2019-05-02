package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.*;
import fr.eseo.vsquare.model.EventLog.EventAction;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.*;
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
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"java.*","javax.*","org.*","fr.eseo.vsquare.model.*","fr.eseo.vsquare.utils.DatabaseManager"})
@PrepareForTest({VSphereManager.class, LDAPUtils.class, ServletUtils.class, VCenterManager.class})
public class GroupServletTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
	}

	@Test
	public void testServletOptions() throws ServletException, IOException {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("OPTIONS", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testServletNoAuth() throws ServletException, IOException {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("401 on GET:/api/groups", HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
	}
	
	@Test
	public void testServletError() throws ServletException, IOException {
		PowerMockito.mockStatic(ServletUtils.class);
		Mockito.when(ServletUtils.verifyToken(Mockito.any(), Mockito.any())).thenThrow(new MockitoException("Test error do not panic"));

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(new StringWriter());
		new GroupServlet().service(request, response);
		
		assertEquals(1,ErrorLog.getAll().size());
	}
	
	@Test
	public void testGetGroupsSuccess() throws ServletException, IOException {
		String token = TestUtils.login("test", "test");

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

        assertEquals("One group initially : Etudiants", 1, Token.findByValue(token).getUser().getGroups().size());

		// case of no groups present
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/groups", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("200 on /api/group", HttpServletResponse.SC_OK, res.getInt("code"));
		assertEquals("only default group should be present", 1, res.getJSONArray("value").length());

		// case of 2 groups presents
		StringWriter writer2 = new StringWriter();
		Group group_parent = new Group("parent", "parent group");
		group_parent.saveOrUpdate();
		Group group_child = new Group("child", "child_group", group_parent.getId());
		group_child.saveOrUpdate();

		User userTest = Token.findByValue(token).getUser();
		userTest.addGroup(group_child);
		userTest.saveOrUpdate();

		request = TestUtils.createMockRequest("GET", "/api/groups", null, headers);
		response = TestUtils.createMockResponse(writer2);
		new GroupServlet().service(request, response);
		res = TestUtils.getResponseAsJSON(writer2);
		assertEquals("200 on /api/groups", HttpServletResponse.SC_OK, res.getInt("code"));
		assertEquals("3 groups should be present", 3, res.getJSONArray("value").length());
	}
	
	@Test
	public void testAddGroupNoPrivileges() throws ServletException, IOException {
		String token = TestUtils.login("test", "test");

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "myName");
		params.put("description", "myDescription");

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("403 on POST:/api/group", HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testEditGroupNoPrivileges() throws ServletException, IOException {
		String token = TestUtils.login("test", "test");

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/test", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("403 on POST:/api/group/(.*)", HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testDeleteGroupNoPrivileges() throws ServletException, IOException {
		String token = TestUtils.login("test", "test");

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/test", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("403 on POST:/api/group/(.*)", HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testAddGroupBadArgs() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name2", "myName");
		params.put("description", "myDescription");

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("400 on POST:/api/group", HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
	}

	@Test
	public void testAddGroupSuccessWithoutParent() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "myName");
		params.put("description", "myDescription");

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("400 on POST:/api/group", HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
	}

	@Test
	public void testAddGroupSuccessWithParent() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		// parent
		Group parent = new Group("parent", "parent group");
		parent.saveOrUpdate();

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "myName");
		params.put("description", "myDescription");
		params.put("id_parent_group", String.valueOf(parent.getId()));

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group", params, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("200 on POST:/api/group", HttpServletResponse.SC_OK, res.getInt("code"));

		JSONObject groupJson = res.getJSONObject("value");
        assertTrue(groupJson.has("id"));
		assertEquals("myName", groupJson.getString("name"));
		assertEquals("myDescription", groupJson.getString("description"));
		assertEquals(parent.getId().intValue(), groupJson.getInt("id_parent_group"));
		
		List<EventLog> events = EventLog.getAll();
		assertEquals(1, events.size());
		assertEquals(Token.findByValue(token).getUser(), events.get(0).getUser());
		assertEquals(EventAction.CREATE, events.get(0).getAction());
		assertEquals(Group.findById(groupJson.getInt("id")), events.get(0).getObject());
	}

	@Test
	public void testGetGroupSuccess() throws ServletException, IOException {
		String token = TestUtils.login("test", "test");

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		// parent
		Group parent = new Group("parent", "parent group");
		parent.saveOrUpdate();

		// user
		User user = new User("testounet", User.UserType.STUDENT, "testounet");
		user.addGroup(parent);
		user.saveOrUpdate();

		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/" + parent.getId(), null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("200 on POST:/api/group/XXX", HttpServletResponse.SC_OK, res.getInt("code"));
		assertEquals(parent.getId().intValue(), res.getJSONObject("value").getInt("id"));
		assertEquals("parent", res.getJSONObject("value").getString("name"));
		assertEquals("parent group", res.getJSONObject("value").getString("description"));
		assertEquals("1 user present", 1, res.getJSONObject("value").getJSONArray("users").length());
	}

	@Test
	public void testGetGroupFail() throws ServletException, IOException {
		String token = TestUtils.login("test", "test");

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/268435842", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("404 on POST:/api/group", HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}

	@Test
	public void testGetGroupFail2() throws ServletException, IOException {
		String token = TestUtils.login("test", "test");

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/bla", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("400 on POST:/api/group", HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
	}
	
	@Test
	public void testEditGroupFail() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/268415842", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("404 on POST:/api/group", HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}
	
	@Test
	public void testEditGroupFail2() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/bla", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("400 on POST:/api/group", HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
	}

	@Test
	public void testDeleteGroupFail() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/26841842", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("404 on POST:/api/group", HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}

	@Test
	public void testDeleteGroupFail2() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/bla", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("400 on POST:/api/group", HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
	}
	
	public void testEditGroupBadAuth() throws ServletException, IOException {
		Group group = new Group("basicName", "basicDescription");
		group.saveOrUpdate();

		String token = TestUtils.login("test", "test");

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "myName");
		params.put("description", "myDescription");

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/" + group.getId(), params,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("403 on POST:/api/group/XXX", HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testEditGroupSuccess() throws ServletException, IOException {
		Group group = new Group("basicName", "basicDescription");
		group.saveOrUpdate();

		Group parent = new Group("parent", "parent");
		parent.saveOrUpdate();
		
		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HashMap<String, String> params = new HashMap<>();
		params.put("name", "myName");
		params.put("description", "myDescription");
		params.put("id_parent_group", parent.getId().toString());

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/" + group.getId(), params,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("200 on POST:/api/group/XXX", HttpServletResponse.SC_OK, res.getInt("code"));

		group = Group.findById(group.getId());

		assertEquals("myName", group.getName());
		assertEquals("myDescription", group.getDescription());
		assertEquals(parent.getId(), group.getParent().getId());
		
		List<EventLog> events = EventLog.getAll();
		assertEquals(1, events.size());
		assertEquals(Token.findByValue(token).getUser(), events.get(0).getUser());
		assertEquals(EventAction.EDIT, events.get(0).getAction());
		assertEquals(group, events.get(0).getObject());
	}

	@Test
	public void testDeleteGroupSuccess() throws ServletException, IOException {
		Group group = new Group("basicName", "basicDescription");
		group.saveOrUpdate();

		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/" + group.getId(), null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("200 on DELETE:/api/group/XXX", HttpServletResponse.SC_OK, res.getInt("code"));

		int grpid = group.getId();
		group = Group.findById(grpid);
		assertNull(group);
		
		List<EventLog> events = EventLog.getAll();
		assertEquals(1, events.size());
		assertEquals(Token.findByValue(token).getUser(), events.get(0).getUser());
		assertEquals(EventAction.DELETE, events.get(0).getAction());
		assertNull(events.get(0).getObject());
		assertEquals(grpid, events.get(0).getObjectId());
	}
	
	@Test
	public void testDeleteGroupDefaultGroup() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/" + UserType.ADMIN.getDefaultGroup().getId(), null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testAddUser() throws ServletException, IOException {
		Group group = new Group("basicName", "basicDescription", UserType.STUDENT.getDefaultGroup());
		group.saveOrUpdate();
		assertNotNull(group.getId());
		User student = new User("student", User.UserType.STUDENT, "student");
		student.saveOrUpdate();
		assertNotNull(student.getId());

		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("PUT",
				"/api/group/" + group.getId() + "/user/" + student.getId(), null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("200 on PUT:/api/group/XXX/user/XXX", HttpServletResponse.SC_OK, res.getInt("code"));

		group = Group.findById(group.getId());
		assertEquals(1, group.getUsers().size());

		student = User.findById(student.getId());
		assertEquals(1, student.getGroups().size());
		
		List<EventLog> events = EventLog.getAll();
		assertEquals(1, events.size());
		assertEquals(Token.findByValue(token).getUser(), events.get(0).getUser());
		assertEquals(EventAction.EDIT, events.get(0).getAction());
		assertEquals(group, events.get(0).getObject());
	}
	
	@Test
	public void testAddUserNoRights() throws ServletException, IOException {
		Group group = new Group("basicName", "basicDescription");
		group.saveOrUpdate();
		assertNotNull(group.getId());
		User student = new User("student", User.UserType.STUDENT, "student");
		student.saveOrUpdate();
		assertNotNull(student.getId());

		String token = TestUtils.login("test", "test", User.UserType.REFERENT);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("PUT",
				"/api/group/" + group.getId() + "/user/" + student.getId(), null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(403, res.getInt("code"));
	}
	
	@Test
	public void testAddUserFail() throws ServletException, IOException {
		Group group = new Group("basicName", "basicDescription");
		group.saveOrUpdate();
		assertNotNull(group.getId());

		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("PUT",
				"/api/group/" + group.getId() + "/user/bla", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}
	
	@Test
	public void testAddUserFail2() throws ServletException, IOException {
		Group group = new Group("basicName", "basicDescription");
		group.saveOrUpdate();
		assertNotNull(group.getId());

		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("PUT",
				"/api/group/" + group.getId() + "/user/4534", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(404, res.getInt("code"));
	}

	@Test
	public void testAddUserFail3() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		User student = new User("student", User.UserType.STUDENT, "student");
		student.saveOrUpdate();
		
		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("PUT",
				"/api/group/142435/user/"+student.getId(), null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(404, res.getInt("code"));
	}
	
	@Test
	public void testAddUserFail4() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		User student = new User("student", User.UserType.STUDENT, "student");
		student.saveOrUpdate();
		
		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("PUT",
				"/api/group/bla/user/"+student.getId(), null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}
	
	@Test
	public void testRemoveGroupUser() throws ServletException, IOException {
		Group group = new Group("basicName", "basicDescription");
		group.saveOrUpdate();

		User student = new User("student", User.UserType.STUDENT, "student");
		student.addGroup(group);
		student.saveOrUpdate();

		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE",
				"/api/group/" + group.getId() + "/user/" + student.getId(), null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals("200 on DELETE:/api/group/XXX/user/XXX", HttpServletResponse.SC_OK, res.getInt("code"));

		group = Group.findById(group.getId());
		assertEquals(0, group.getUsers().size());
		
		List<EventLog> events = EventLog.getAll();
		assertEquals(1, events.size());
		assertEquals(Token.findByValue(token).getUser(), events.get(0).getUser());
		assertEquals(EventAction.EDIT, events.get(0).getAction());
		assertEquals(group, events.get(0).getObject());
	}
	
	@Test
	public void testRemoveGroupUserNoRights() throws ServletException, IOException {
		Group group = new Group("basicName", "basicDescription");
		group.saveOrUpdate();

		User student = new User("student", User.UserType.STUDENT, "student");
		student.addGroup(group);
		student.saveOrUpdate();

		String token = TestUtils.login("test", "test", User.UserType.REFERENT);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE",
				"/api/group/" + group.getId() + "/user/" + student.getId(), null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(403, res.getInt("code"));
	}
	
	@Test
	public void testRemoveGroupUserFail() throws ServletException, IOException {
		Group group = new Group("basicName", "basicDescription");
		group.saveOrUpdate();

		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE",
				"/api/group/" + group.getId() + "/user/12346", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(404, res.getInt("code"));
	}
	
	@Test
	public void testRemoveGroupUserFail2() throws ServletException, IOException {
		Group group = new Group("basicName", "basicDescription");
		group.saveOrUpdate();

		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE",
				"/api/group/" + group.getId() + "/user/bla", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}
	
	@Test
	public void testRemoveGroupUserFail3() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		User student = new User("student", User.UserType.STUDENT, "student");
		student.saveOrUpdate();
		
		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE",
				"/api/group/135164/user/"+student.getId(), null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(404, res.getInt("code"));
	}
	
	@Test
	public void testRemoveGroupUserFail4() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", User.UserType.ADMIN);

		User student = new User("student", User.UserType.STUDENT, "student");
		student.saveOrUpdate();
		
		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE",
				"/api/group/bla/user/"+student.getId(), null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}

	@Test
	public void testListVmsSuccessReferent() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);

		// parent
		Group parent = new Group("parent", "parent group");
		parent.saveOrUpdate();

		User u = new User("test_student", "test_student");
		u.addGroup(parent);
		u.saveOrUpdate();

		Vm vm = new Vm(u, "id_vm", "vmname", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONArray());
		vsphereRes.getJSONArray("value").put(TestUtils.getVmMockVcenterJSON(vm));
		TestUtils.mockVSphereAllResponse(200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/" + parent.getId() + "/vm", null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new GroupServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));

		JSONArray value = res.getJSONArray("value");

		assertEquals(1, value.length());

		assertEquals(vm.getName(), value.getJSONObject(0).getString("name"));
		assertEquals(vm.getDescription(), value.getJSONObject(0).getString("desc"));
		assertEquals((int) u.getId(), value.getJSONObject(0).getJSONObject("user").getInt("id"));
		assertTrue(value.getJSONObject(0).has("memory_size_MiB"));
		assertTrue(value.getJSONObject(0).has("power_state"));	
		assertTrue(value.getJSONObject(0).has("cpu_count"));
	}
	
	@Test
	public void testListVmsSuccessSubGroup() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);

		Group parent = new Group("parent", "parent group");
		parent.saveOrUpdate();
		
		Group middle = new Group("middle", "middle group", parent);
		middle.saveOrUpdate();
		
		Group last = new Group("last", "last group", middle);
		last.saveOrUpdate();

		User u = new User("test_student", "test_student");
		u.addGroup(last);
		u.saveOrUpdate();

		Vm vm = new Vm(u, "id_vm", "vmname", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONArray());
		vsphereRes.getJSONArray("value").put(TestUtils.getVmMockVcenterJSON(vm));
		TestUtils.mockVSphereAllResponse(200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/" + parent.getId() + "/vm", null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new GroupServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));

		JSONArray value = res.getJSONArray("value");

		assertEquals(1, value.length());

		assertEquals(vm.getName(), value.getJSONObject(0).getString("name"));
		assertEquals(vm.getDescription(), value.getJSONObject(0).getString("desc"));
		assertEquals((int) u.getId(), value.getJSONObject(0).getJSONObject("user").getInt("id"));
		assertTrue(value.getJSONObject(0).has("memory_size_MiB"));
		assertTrue(value.getJSONObject(0).has("power_state"));	
		assertTrue(value.getJSONObject(0).has("cpu_count"));
	}

	@Test
	public void testListVmsSuccessAdmin() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		// parent
		Group parent = new Group("parent", "parent group");
		parent.saveOrUpdate();

		User u = new User("test_student", UserType.ADMIN, "test_student");
		u.addGroup(parent);
		u.saveOrUpdate();

		Vm vm = new Vm(u, "id_vm", "vmname", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONArray());
		vsphereRes.getJSONArray("value").put(TestUtils.getVmMockVcenterJSON(vm));
		TestUtils.mockVSphereAllResponse(200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/" + parent.getId() + "/vm", null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new GroupServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));

		JSONArray value = res.getJSONArray("value");

		assertEquals(1, value.length());

		assertEquals(vm.getName(), value.getJSONObject(0).getString("name"));
		assertEquals(vm.getDescription(), value.getJSONObject(0).getString("desc"));
		assertEquals((int) u.getId(), value.getJSONObject(0).getJSONObject("user").getInt("id"));
		assertTrue(value.getJSONObject(0).has("memory_size_MiB"));
		assertTrue(value.getJSONObject(0).has("power_state"));	
		assertTrue(value.getJSONObject(0).has("cpu_count"));
	}
	
	@Test
	public void testListVmsStudent() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);

		// parent
		Group parent = new Group("parent", "parent group");
		parent.saveOrUpdate();

		User u = new User("test_student", "test_student");
		u.addGroup(parent);
		u.saveOrUpdate();

		Vm vm = new Vm(u, "id_vm", "vmname", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONArray());
		vsphereRes.getJSONArray("value").put(TestUtils.getVmMockVcenterJSON(vm));
		TestUtils.mockVSphereAllResponse(200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/" + parent.getId() + "/vm", null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new GroupServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(403, res.getInt("code"));
	}

	@Test
	public void testListVmsNoVm() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);

		// parent
		Group parent = new Group("parent", "parent group");
		parent.saveOrUpdate();

		User u = new User("test_student", UserType.REFERENT, "test_student");
		u.addGroup(parent);
		u.saveOrUpdate();

		Vm vm = new Vm(u, "id_vm", "vmname", "no link to vsphere");
		vm.saveOrUpdate();

		JSONObject vsphereRes = new JSONObject();
		vsphereRes.put("value", new JSONArray());
		vsphereRes.getJSONArray("value").put(TestUtils.getVmMockVcenterJSON(vm));
		TestUtils.mockVSphereAllResponse(200, vsphereRes);

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/" + parent.getId() + "/vm", null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new GroupServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));

		JSONArray value = res.getJSONArray("value");

		assertEquals(0, value.length());
	}
	
	@Test
	public void testListVmsFail() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);
		
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/123/vm", null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new GroupServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(404, res.getInt("code"));
	}
	
	@Test
	public void testListVmsFail2() throws Exception {
		String token = TestUtils.login("test", "test", UserType.REFERENT);
		
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/bla/vm", null,
				headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new GroupServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(400, res.getInt("code"));
	}

    @Test
    public void testGetGroupPermissionSuccess() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        Permission permission = new Permission(1, 2, 3, 4);
        permission.saveOrUpdate();

        Group g = new Group("test", "test");
        g.setPermission(permission);
        g.saveOrUpdate();

        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/" + g.getId() + "/permission", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

        JSONObject value = res.getJSONObject("value");
        assertEquals(permission.getVmCount(), value.getInt("vm_count"));
        assertEquals(permission.getCpuCount(), value.getInt("cpu_count"));
        assertEquals(permission.getDiskStorage(), value.getInt("disk_storage"));
        assertEquals(permission.getMemorySize(), value.getInt("memory_size"));
    }

    @Test
    public void testGetGroupPermissionInvalid() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.REFERENT);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/5/permission", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
    }

    @Test
    public void testGetGroupPermissionNotFound() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/135344312/permission", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
    }

    @Test
    public void testCreateGroupPermissionSuccess() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Group g = new Group("test", "test");
        g.saveOrUpdate();

        User u = Token.findByValue(token).getUser();
        u.addGroup(g);
        u.setPrivateNetwork("net-123");
        u.saveOrUpdate();

        Vm vm = new Vm(u, "test", "test", "test");
        vm.saveOrUpdate();

        JSONObject jsonVm = new JSONObject();
        jsonVm.put("value", TestUtils.getVmMockVcenterJSON(vm, true));

        TestUtils.mockVSphereResponse("GET", "/vcenter/vm/" + vm.getIdVmVcenter(), 200,
                jsonVm, true);
        TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/memory", 200,
                new JSONObject(), false);
        TestUtils.mockVSphereResponse("PATCH", "/vcenter/vm/" + vm.getIdVmVcenter() + "/hardware/cpu", 200,
                new JSONObject(), false);

        PowerMockito.mockStatic(VCenterManager.class);
        PowerMockito.doReturn(true).when(VCenterManager.class);
        VCenterManager.editNetwork(Mockito.eq(u.getPrivateNetwork()), Mockito.anyInt());
        PowerMockito.doReturn(true).when(VCenterManager.class);
        VCenterManager.shutdownVM(Mockito.eq(vm));

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HashMap<String, String> params = new HashMap<>();
        params.put("vm_count", "4");
        params.put("cpu_count", "3");
        params.put("memory_size", "512");
        params.put("disk_storage", "1024");

        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/" + g.getId() + "/permission", params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

        JSONObject value = res.getJSONObject("value");
        assertEquals(4, value.getInt("vm_count"));
        assertEquals(3, value.getInt("cpu_count"));
        assertEquals(512, value.getInt("memory_size"));
        assertEquals(1024, value.getInt("disk_storage"));
    }

    @Test
    public void testCreateGroupPermissionBadRequest() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HashMap<String, String> params = new HashMap<>();
        params.put("vm_count", "4");
        params.put("cpu_count", "3");
        params.put("memory_size", "-2");
        params.put("disk_storage", "1");

        Group g = new Group("test", "test");
        g.saveOrUpdate();

        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/" + g.getId() + "/permission", params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
    }

    @Test
    public void testCreateGroupPermissionInvalid() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.REFERENT);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/5/permission", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
    }

    @Test
    public void testCreateGroupPermissionNotFound() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/135344312/permission", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
    }

    @Test
    public void testDeleteGroupPermissionSuccess() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        Permission permission = new Permission(1, 2, 3, 4);
        permission.saveOrUpdate();

        Permission permissionParent = new Permission(6, 7, 8, 9);
        permissionParent.saveOrUpdate();

        Group gParent = new Group("testparent", "test");
        gParent.setPermission(permissionParent);
        gParent.saveOrUpdate();

        Group g = new Group("test", "test");
        g.setPermission(permission);
        g.setIdParentGroup(gParent.getId());
        g.saveOrUpdate();

        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/" + g.getId() + "/permission", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

        JSONObject value = res.getJSONObject("value");
        assertEquals(permissionParent.getVmCount(), value.getInt("vm_count"));
        assertEquals(permissionParent.getCpuCount(), value.getInt("cpu_count"));
        assertEquals(permissionParent.getDiskStorage(), value.getInt("disk_storage"));
        assertEquals(permissionParent.getMemorySize(), value.getInt("memory_size"));
    }

    @Test
    public void testDeleteGroupPermissionInvalid() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.REFERENT);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/5/permission", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
    }

    @Test
    public void testDeleteGroupPermissionNotFound() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/135344312/permission", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
    }

    @Test
    public void testListGroupNetwork() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Group parent = new Group("parent", "test");
        parent.saveOrUpdate();

		Network np = new Network("test", "test");
		np.addGroup(parent);
        np.saveOrUpdate();

        Group g = new Group("test", "test", parent);
        g.saveOrUpdate();

		Network n = new Network("test", "test");
		n.addGroup(g);
        n.saveOrUpdate();

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/" + g.getId() + "/networks", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

        JSONArray array = res.getJSONArray("value");
        assertEquals(2, array.length());
        assertEquals((int) n.getId(), array.getJSONObject(0).getInt("id"));
        assertFalse(array.getJSONObject(0).getBoolean("inherited"));
        assertEquals((int) np.getId(), array.getJSONObject(1).getInt("id"));
        assertTrue(array.getJSONObject(1).getBoolean("inherited"));
    }

    @Test
    public void testListGroupNetworkInvalid() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.REFERENT);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/135344312/networks", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
    }

    @Test
    public void testListGroupNetworkNotFound() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/group/135344312/networks", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
    }

    @Test
    public void testAddGroupNetwork() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        JSONObject vsphereRes = new JSONObject();
        vsphereRes.put("value", new JSONArray());
        vsphereRes.getJSONArray("value").put(new JSONObject());
        vsphereRes.getJSONArray("value").getJSONObject(0).put("name", "VM Network");
        vsphereRes.getJSONArray("value").getJSONObject(0).put("type", "STANDARD_PORTGROUP");
        vsphereRes.getJSONArray("value").getJSONObject(0).put("network", "network-12");
        TestUtils.mockVSphereResponse("GET", "/vcenter/network", 200, vsphereRes);

        Group g = new Group("test", "test");
        g.saveOrUpdate();

		Network n = new Network("test", "test");
		n.saveOrUpdate();

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/group/" + g.getId() + "/network/" + n.getId(), null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

        g = Group.findById(g.getId());
        assertEquals(1, g.getNetworks().size());
    }

    @Test
    public void testAddGroupNetworkBadRequest() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Group g = new Group("test", "test");
        g.saveOrUpdate();

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/group/" + g.getId() + "/networks", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
    }

    @Test
    public void testAddGroupNetworkWrongNetwork() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        JSONObject vsphereRes = new JSONObject();
        vsphereRes.put("value", new JSONArray());
        TestUtils.mockVSphereResponse("GET", "/vcenter/network", 200, vsphereRes);

        Group g = new Group("test", "test");
        g.saveOrUpdate();

        HashMap<String, String> params = new HashMap<>();
        params.put("name", "name");
        params.put("network", "network-12");

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/group/" + g.getId() + "/networks", params, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
    }

    @Test
    public void testAddGroupNetworkInvalid() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.REFERENT);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/group/135344312/network/515436", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
    }

    @Test
    public void testAddGroupNetworkNotFound() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/group/135344312/network/12134535", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
    }

	@Test
	public void testAddGroupNetworkNotFound2() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Group g = new Group("test", "test");
		g.saveOrUpdate();

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("PUT", "/api/group/" + g.getId() + "/network/12134535", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}

    @Test
    public void testDeleteGroupNetwork() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Group g = new Group("test", "test");
        g.saveOrUpdate();

		Network n = new Network("test", "test");
		n.addGroup(g);
        n.saveOrUpdate();

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/" + g.getId() + "/network/" + n.getId(), null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

        g = Group.findById(g.getId());
        assertEquals(0, g.getNetworks().size());
    }

    @Test
    public void testDeleteGroupNetworkWrongGroup() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Group g = new Group("test", "test");
        g.saveOrUpdate();

        Group g2 = new Group("test2", "test");
        g2.saveOrUpdate();

		Network n = new Network("test", "test");
		n.addGroup(g2);
        n.saveOrUpdate();

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/" + g.getId() + "/network/" + n.getId(), null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
    }

    @Test
    public void testDeleteGroupNetworkInvalid() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.REFERENT);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/135344312/network/1516431", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
    }

    @Test
    public void testDeleteGroupNetworkNotFound() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/135344312/network/1516431", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
    }

    @Test
    public void testDeleteGroupNetworkNotFound2() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        Group g = new Group("test", "test");
        g.saveOrUpdate();

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/" + g.getId() + "/network/1516431", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
    }

    @Test
    public void testSetPowerAllSuccess() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);
        Token t = Token.findByValue(token);

        Group g = new Group("test", "test");
        g.saveOrUpdate();

        User admin = t.getUser();
        admin.addGroup(g);
        admin.saveOrUpdate();

        User student = new User("student", UserType.STUDENT, "student");
        student.addGroup(g);
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
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/" + g.getId() + "/power/start", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new GroupServlet().service(request, response);

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

        Group g = new Group("test", "test");
        g.saveOrUpdate();

        User student = new User("student", UserType.STUDENT, "student");
        student.addGroup(g);
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
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/" + g.getId() + "/power/start", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new GroupServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(403, res.getInt("code"));
    }

    @Test
    public void testSetPowerAllBadAction() throws Exception {
        String token = TestUtils.login("test", "test", UserType.ADMIN);
        Token t = Token.findByValue(token);

        Group g = new Group("test", "test");
        g.saveOrUpdate();

        User student = new User("student", UserType.STUDENT, "student");
        student.addGroup(g);
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
        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/" + g.getId() + "/power/badAction", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        new GroupServlet().service(request, response);

        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(400, res.getInt("code"));
    }

    @Test
    public void testSetPowerAllNotFound() throws ServletException, IOException {
        String token = TestUtils.login("test", "test", UserType.ADMIN);

        StringWriter writer = new StringWriter();
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Utils.getString("auth_token_header"), token);

        HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/group/135344312/power/start", null, headers);
        HttpServletResponse response = TestUtils.createMockResponse(writer);
        new GroupServlet().service(request, response);
        JSONObject res = TestUtils.getResponseAsJSON(writer);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
    }

	@Test
	public void testResetGroupUserNotFound() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/135344312/reset/users", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}

	@Test
	public void testResetGroupUserForbidden() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.REFERENT);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/135344312/reset/users", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testResetGroupUser() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Group g1 = new Group("test1", "test");
		g1.saveOrUpdate();

		Group g2 = new Group("test2", "test", g1);
		g2.saveOrUpdate();

		Group g3 = new Group("test3", "test");
		g3.saveOrUpdate();

		User u1 = new User("test1", "test1");
		u1.addGroup(g1);
		u1.addGroup(g2);
		u1.saveOrUpdate();

		User u2 = new User("test2", "test2");
		u2.addGroup(g2);
		u2.addGroup(g3);
		u2.saveOrUpdate();

		assertEquals(3, u1.getGroups().size());
		assertEquals(3, u2.getGroups().size());

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/" + g1.getId() + "/reset/users", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

		u1 = User.findById(u1.getId());
		u2 = User.findById(u2.getId());

		assertNotNull(u1);
		assertNotNull(u2);

		assertEquals(1, u1.getGroups().size());
		assertEquals(2, u2.getGroups().size());
		assertTrue(u2.getGroups().contains(g3));
	}

	@Test
	public void testResetGroupVmsNotFound() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/135344312/reset/vms", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}

	@Test
	public void testResetGroupVmsForbidden() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.REFERENT);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/135344312/reset/vms", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getInt("code"));
	}

	@Test
	public void testResetGroupVms() throws ServletException, IOException {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Group g1 = new Group("test1", "test");
		g1.saveOrUpdate();

		Group g2 = new Group("test2", "test", g1);
		g2.saveOrUpdate();

		User u1 = new User("test1", "test1");
		u1.addGroup(g1);
		u1.saveOrUpdate();

		Vm vm1 = new Vm(u1, "test1", "test", "test");
		vm1.saveOrUpdate();

		User u2 = new User("test2", "test2");
		u2.addGroup(g2);
		u2.saveOrUpdate();

		Vm vm2 = new Vm(u2, "test2", "test", "test");
		vm2.saveOrUpdate();

		PowerMockito.mockStatic(VCenterManager.class);
		PowerMockito.doReturn(true).when(VCenterManager.class);
		VCenterManager.shutdownVM(Mockito.any());
		PowerMockito.doReturn(true).when(VCenterManager.class);
		try {
			VCenterManager.deleteVm(Mockito.any());
		} catch (InterruptedException ignored) {
		}

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/" + g1.getId() + "/reset/vms", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));

		assertNull(Vm.findById(vm1.getId()));
		assertNull(Vm.findById(vm2.getId()));
	}

	@Test
	public void testResetGroupVmsError() throws Exception {
		String token = TestUtils.login("test", "test", UserType.ADMIN);

		Group g1 = new Group("test1", "test");
		g1.saveOrUpdate();

		User u1 = new User("test1", "test1");
		u1.addGroup(g1);
		u1.saveOrUpdate();

		Vm vm1 = new Vm(u1, "test1", "test", "test");
		vm1.saveOrUpdate();

		PowerMockito.mockStatic(VCenterManager.class);
		PowerMockito.when(VCenterManager.class, "shutdownVM", Mockito.any()).thenThrow(RemoteException.class);

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/group/" + g1.getId() + "/reset/vms", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new GroupServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, res.getInt("code"));

		assertNotNull(Vm.findById(vm1.getId()));
	}
}
