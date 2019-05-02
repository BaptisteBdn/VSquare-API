package fr.eseo.vsquare.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.User.UserType;

public class TokenTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
	}

	@Test
	public void testSaveOrUpdateToken() throws SQLException {
		User u = new User("test_user", UserType.ADMIN, "test");

		u.saveOrUpdate();

		Token t = new Token("test", u);

		try (Statement st = TestUtils.getConnection().createStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT * FROM token WHERE 1")) {
				assertFalse(rs.first());
			}

			assertTrue(t.saveOrUpdate());

			try (ResultSet rs = st.executeQuery("SELECT * FROM token WHERE 1")) {
				assertTrue(rs.first());
				assertEquals((int) t.getId(), rs.getInt("id"));
				assertEquals("test", rs.getString("value"));
			}

			t.setValue("test2");
			assertTrue(t.saveOrUpdate());

			try (ResultSet rs = st.executeQuery("SELECT * FROM token WHERE 1")) {
				assertTrue(rs.first());
				assertEquals("test2", rs.getString("value"));
			}
		}
	}
	
	@Test
	public void testFindByValue() {
		User u = new User("test_user", UserType.ADMIN, "test");

		u.saveOrUpdate();

		Token t = new Token("test", u);

		t.saveOrUpdate();

		Token t2 = Token.findByValue("test");
		assertEquals(t, t2);
		
		t.delete();

		assertNull(Token.findByValue("test"));
	}
	
	@Test
	public void testToJSON() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		Token t = new Token("test", u);
		t.saveOrUpdate();
		JSONObject json = t.toJSON();
		assertEquals((int)t.getId(), json.getInt("id"));
		assertEquals(t.getValue(), json.getString("value"));
		assertTrue(json.has("user"));
		assertEquals((int)u.getId(),json.getJSONObject("user").getInt("id"));
		assertEquals(3, json.length());
	}
	
	@Test
	public void testToJSONDetailed() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		Token t = new Token("test", u);
		t.saveOrUpdate();
		JSONObject json = t.toJSON(true);
		assertEquals((int)t.getId(), json.getInt("id"));
		assertTrue(json.has("creation_date"));
		assertEquals(t.getValue(), json.getString("value"));
		assertTrue(json.has("user"));
		assertEquals((int)u.getId(),json.getJSONObject("user").getInt("id"));
		assertEquals(4, json.length());
	}

}
