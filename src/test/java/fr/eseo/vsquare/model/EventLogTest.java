package fr.eseo.vsquare.model;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.EventLog.EventAction;
import fr.eseo.vsquare.model.EventLog.EventObject;
import fr.eseo.vsquare.model.User.UserType;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class EventLogTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
	}

	@Test
	public void testSaveOrUpdate() throws SQLException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		EventLog e = new EventLog(u, EventAction.DELETE, u);

		try (Statement st = TestUtils.getConnection().createStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT * FROM event_log WHERE 1")) {
				assertFalse(rs.first());
			}

			assertTrue(e.saveOrUpdate());

			try (ResultSet rs = st.executeQuery("SELECT * FROM event_log WHERE 1")) {
				assertTrue(rs.first());
				assertEquals((int) e.getId(), rs.getInt("id"));
				assertEquals("DELETE", rs.getString("action"));
			}

			Field action = EventLog.class.getDeclaredField("action");
			action.setAccessible(true);
			action.set(e, EventAction.CREATE);
			assertTrue(e.saveOrUpdate());

			try (ResultSet rs = st.executeQuery("SELECT * FROM event_log WHERE 1")) {
				assertTrue(rs.first());
				assertEquals("CREATE", rs.getString("action"));
			}
		}
	}

	@Test
	public void testGetObjectUser() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		EventLog e = new EventLog(u, EventAction.DELETE, u);
		e.saveOrUpdate();
		
		assertEquals(u, e.getObject());
	}
	
	@Test
	public void testGetObjectGroup() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		Group g = new Group("group","group");
		g.saveOrUpdate();
		
		EventLog e = new EventLog(u, EventAction.DELETE, g);
		e.saveOrUpdate();
		
		assertEquals(g, e.getObject());
	}
	
	@Test
	public void testGetObjectVM() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		Vm v = new Vm(u, "test","test","test");
		v.saveOrUpdate();
		
		EventLog e = new EventLog(u, EventAction.DELETE, v);
		e.saveOrUpdate();
		
		assertEquals(v, e.getObject());
	}
	
	@Test
	public void testConstructorError() {
		try {
			User u = new User("test_user", UserType.ADMIN, "test");
			u.saveOrUpdate();
			
			new EventLog(u, EventAction.DELETE, null);
			fail("no exception");
		}catch(IllegalArgumentException e) {}
	}
	
	@Test
	public void testConstructorError2() {
		try {
			User u = new User("test_user", UserType.ADMIN, "test");
			
			new EventLog(u, EventAction.DELETE, u);
			fail("no exception");
		}catch(IllegalArgumentException e) {}
	}
	
	@Test
	public void testConstructorError3() {
		try {
			User u = new User("test_user", UserType.ADMIN, "test");
			
			Token t = new Token("test", u);
			
			new EventLog(u, EventAction.DELETE, t);
			fail("no exception");
		}catch(IllegalArgumentException e) {}
	}
	
	@Test
	public void testToJSON() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		EventLog e = new EventLog(u, EventAction.DELETE, u);
		e.saveOrUpdate();

		JSONObject json = e.toJSON();
		assertEquals((int)e.getId(), json.getInt("id"));
		assertEquals(e.getAction().toString(), json.getString("action"));
		assertTrue(json.has("creation_date"));
		assertTrue(json.has("user"));
		assertEquals((int)u.getId(),json.getJSONObject("user").getInt("id"));
		JSONObject object = json.getJSONObject("object");
		assertEquals(u.getCommonName(), object.getString("name"));
		assertEquals((int)u.getId(), object.getInt("id"));
		assertEquals(EventObject.USER.toString(), object.getString("object"));
		
		assertEquals(5, json.length());
	}
	
	@Test
	public void testToJSONDetailed() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		EventLog e = new EventLog(u, EventAction.DELETE, u);
		e.saveOrUpdate();

		JSONObject json = e.toJSON(true);
		assertEquals((int)e.getId(), json.getInt("id"));
		assertEquals(e.getAction().toString(), json.getString("action"));
		assertTrue(json.has("creation_date"));
		assertTrue(json.has("user"));
		assertEquals((int)u.getId(),json.getJSONObject("user").getInt("id"));
		JSONObject object = json.getJSONObject("object");
		assertEquals(u.getCommonName(), object.getString("name"));
		assertEquals((int)u.getId(), object.getInt("id"));
		assertEquals(EventObject.USER.toString(), object.getString("object"));
		assertEquals(u.getCommonName(), object.getString("common_name"));
		assertEquals(5, json.length());
	}
	
	@Test
	public void testGetExtract() throws InterruptedException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		for (int i = 0; i < 10; i++) {
			Group g = new Group(""+i, ""+i);
			g.saveOrUpdate();
			EventLog e = new EventLog(u, EventAction.CREATE, g);
			e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
			e.saveOrUpdate();
		}

		List<EventLog> list = EventLog.getExtract(3, 4, null);

		assertEquals(4, list.size());
		assertEquals("3", ((Group) list.get(0).getObject()).getName());
		assertEquals("6", ((Group) list.get(3).getObject()).getName());

		assertEquals(10, EventLog.count(null));
	}

    @Test
    public void testGetExtractQuery() throws InterruptedException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        User u = new User("test_user", UserType.ADMIN, "test");
        u.saveOrUpdate();
		for (int i = 0; i < 10; i++) {
            Group g = new Group("blu" + i, "" + i);
            g.saveOrUpdate();
            EventLog e = new EventLog(u, EventAction.CREATE, g);
            e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
            e.saveOrUpdate();

            g = new Group("bla" + i, "" + i);
            g.saveOrUpdate();
            e = new EventLog(u, EventAction.CREATE, g);
            e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
            e.saveOrUpdate();
        }

		List<EventLog> list = EventLog.getExtract(3, 4, "BlU");

		assertEquals(4, list.size());
		assertEquals("blu3", ((Group) list.get(0).getObject()).getName());
		assertEquals("blu6", ((Group) list.get(3).getObject()).getName());

		assertEquals(10, EventLog.count("BlU"));
    }

    @Test
    public void testGetExtractQuery2() throws InterruptedException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        User u = new User("test_user", UserType.ADMIN, "test");
        u.saveOrUpdate();
		for (int i = 0; i < 10; i++) {
            Group g = new Group("test" + i, "" + i);
            g.saveOrUpdate();
            EventLog e = new EventLog(u, EventAction.CREATE, g);
            e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
            e.saveOrUpdate();

            User u2 = new User("test" + i, UserType.ADMIN, "" + i);
            u2.saveOrUpdate();
            e = new EventLog(u, EventAction.EDIT, u2);
            e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
            e.saveOrUpdate();
        }

		List<EventLog> list = EventLog.getExtract(3, 4, "group:test");

		assertEquals(4, list.size());
		assertEquals("test3", ((Group) list.get(0).getObject()).getName());
		assertEquals("test6", ((Group) list.get(3).getObject()).getName());

		assertEquals(10, EventLog.count("group:test"));
    }

    @Test
    public void testGetExtractQuery3() throws InterruptedException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        User u = new User("test_user", UserType.ADMIN, "test");
        u.saveOrUpdate();
		for (int i = 0; i < 10; i++) {
            Group g = new Group("" + i, "" + i);
            g.saveOrUpdate();
            EventLog e = new EventLog(u, EventAction.CREATE, g);
            e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
            e.saveOrUpdate();

            e = new EventLog(u, EventAction.DELETE, g);
            e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
            e.saveOrUpdate();
        }

		List<EventLog> list = EventLog.getExtract(3, 4, "action:create");

		assertEquals(4, list.size());
		assertEquals("3", ((Group) list.get(0).getObject()).getName());
		assertEquals("6", ((Group) list.get(3).getObject()).getName());

		assertEquals(10, EventLog.count("action:create"));
    }

    @Test
    public void testGetExtractQuery4() throws InterruptedException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        User u = new User("trest_user", UserType.ADMIN, "trest");
        u.saveOrUpdate();
		for (int i = 0; i < 10; i++) {
            Group g = new Group("test" + i, "" + i);
            g.saveOrUpdate();
            EventLog e = new EventLog(u, EventAction.CREATE, g);
            e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
            e.saveOrUpdate();

            User u2 = new User("test" + i, UserType.ADMIN, "test" + i);
            u2.saveOrUpdate();
            e = new EventLog(u, EventAction.EDIT, u2);
            e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
            e.saveOrUpdate();
        }

		List<EventLog> list = EventLog.getExtract(3, 4, "user:test");

		assertEquals(4, list.size());
		assertEquals("test3", ((User) list.get(0).getObject()).getLogin());
		assertEquals("test6", ((User) list.get(3).getObject()).getLogin());

		assertEquals(10, EventLog.count("user:test"));
    }

    @Test
    public void testGetExtractQuery5() throws InterruptedException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        User u = new User("test_user", UserType.ADMIN, "test");
        u.saveOrUpdate();
		for (int i = 0; i < 10; i++) {
            Group g = new Group("test" + i, "" + i);
            g.saveOrUpdate();
            EventLog e = new EventLog(u, EventAction.CREATE, g);
            e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
            e.saveOrUpdate();

            Vm vm = new Vm(u, "test" + i, "test" + i, "");
            vm.saveOrUpdate();
            e = new EventLog(u, EventAction.CREATE, vm);
            e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
            e.saveOrUpdate();
        }

		List<EventLog> list = EventLog.getExtract(3, 4, "vm:test");

		assertEquals(4, list.size());
		assertEquals("test3", ((Vm) list.get(0).getObject()).getName());
		assertEquals("test6", ((Vm) list.get(3).getObject()).getName());

		assertEquals(10, EventLog.count("vm:test"));
    }

}
