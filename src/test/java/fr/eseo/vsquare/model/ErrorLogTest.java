package fr.eseo.vsquare.model;

import fr.eseo.vsquare.TestUtils;
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

public class ErrorLogTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
	}

	@Test
	public void testSaveOrUpdate() throws SQLException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		ErrorLog e = new ErrorLog(u, "error");

		try (Statement st = TestUtils.getConnection().createStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT * FROM error_log WHERE 1")) {
				assertFalse(rs.first());
			}

			assertTrue(e.saveOrUpdate());

			try (ResultSet rs = st.executeQuery("SELECT * FROM error_log WHERE 1")) {
				assertTrue(rs.first());
				assertEquals((int) e.getId(), rs.getInt("id"));
				assertEquals("error", rs.getString("error"));
			}

			Field error = ErrorLog.class.getDeclaredField("error");
			error.setAccessible(true);
			error.set(e, "error2");
			assertTrue(e.saveOrUpdate());

			try (ResultSet rs = st.executeQuery("SELECT * FROM error_log WHERE 1")) {
				assertTrue(rs.first());
				assertEquals("error2", rs.getString("error"));
			}
		}
	}

	@Test
	public void testLongError() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		StringBuilder str = new StringBuilder();
		for(int i = 0; i < 3500; i++)
			str.append("errorerrorerrorerror");
		assertEquals(70000, str.length());
		
		ErrorLog e = new ErrorLog(u, str.toString());
		assertTrue(e.saveOrUpdate());
		
		assertEquals(65535, e.getError().length());
	}

	@Test
	public void testToJSON() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		ErrorLog e = new ErrorLog(u, "error");
		e.saveOrUpdate();

		JSONObject json = e.toJSON();
		assertEquals((int)e.getId(), json.getInt("id"));
		assertEquals(e.getError(), json.getString("error"));
		assertTrue(json.has("creation_date"));
		assertTrue(json.has("user"));
		assertEquals((int)u.getId(),json.getJSONObject("user").getInt("id"));
		
		assertEquals(4, json.length());
	}

    @Test
    public void testToJSONNoUser() {
        ErrorLog e = new ErrorLog(null, "error");
        e.saveOrUpdate();

        JSONObject json = e.toJSON();
        assertEquals((int) e.getId(), json.getInt("id"));
        assertEquals(e.getError(), json.getString("error"));
        assertTrue(json.has("creation_date"));
        assertFalse(json.has("user"));

		assertEquals(3, json.length());
    }
	
	@Test
	public void testToJSONDetailed() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		ErrorLog e = new ErrorLog(u, "error");
		e.saveOrUpdate();

		JSONObject json = e.toJSON();
		assertEquals((int)e.getId(), json.getInt("id"));
		assertEquals(e.getError(), json.getString("error"));
		assertTrue(json.has("creation_date"));
		assertTrue(json.has("user"));
		assertEquals((int)u.getId(),json.getJSONObject("user").getInt("id"));
		
		assertEquals(4, json.length());
	}
	
	@Test
	public void testGetExtract() throws InterruptedException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
        for (int i = 0; i < 10; i++) {
			ErrorLog e = new ErrorLog(u, ""+i);
			e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
			e.saveOrUpdate();
		}

        List<ErrorLog> list = ErrorLog.getExtract(3, 4, null);

        assertEquals(4, list.size());
        assertEquals("3", list.get(0).getError());
        assertEquals("6", list.get(3).getError());

        assertEquals(10, ErrorLog.count(null));
	}

    @Test
    public void testGetExtractQuery() throws InterruptedException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        User u = new User("test_user", UserType.ADMIN, "test");
        u.saveOrUpdate();
        for (int i = 0; i < 10; i++) {
            ErrorLog e = new ErrorLog(u, "blu" + i);
            e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
            e.saveOrUpdate();

            e = new ErrorLog(u, "bla" + i);
            e.saveOrUpdate();
            TestUtils.changeVSquareObjectDate(e, new Date(System.currentTimeMillis() - i * 10000));
            e.saveOrUpdate();
        }

        List<ErrorLog> list = ErrorLog.getExtract(3, 4, "BlU");

        assertEquals(4, list.size());
        assertEquals("blu3", list.get(0).getError());
        assertEquals("blu6", list.get(3).getError());

        assertEquals(10, ErrorLog.count("BlU"));
    }

}
