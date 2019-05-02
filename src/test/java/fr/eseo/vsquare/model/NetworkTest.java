package fr.eseo.vsquare.model;

import fr.eseo.vsquare.TestUtils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.*;

public class NetworkTest {

    @Before
    public void setUp() throws Exception {
        TestUtils.initTest(true);
    }

    @Test
    public void testSaveOrUpdateNetwork() throws SQLException {
        Group g = new Group("test", "test");

        g.saveOrUpdate();

        Network n = new Network("test", "testnetwork");

        try (Statement st = TestUtils.getConnection().createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT * FROM network WHERE 1")) {
                assertFalse(rs.first());
            }

            assertTrue(n.saveOrUpdate());

            try (ResultSet rs = st.executeQuery("SELECT * FROM network WHERE 1")) {
                assertTrue(rs.first());
                assertEquals((int) n.getId(), rs.getInt("id"));
                assertEquals("test", rs.getString("name"));
            }

            n.setName("test2");
            assertTrue(n.saveOrUpdate());

            try (ResultSet rs = st.executeQuery("SELECT * FROM network WHERE 1")) {
                assertTrue(rs.first());
                assertEquals("test2", rs.getString("name"));
            }
        }
    }

    @Test
    public void testGetAll() {
        Group g = new Group("test", "test");
        g.saveOrUpdate();
        Network n = new Network("test", "test");
        n.saveOrUpdate();
        assertEquals(1, Network.getAll().size());
    }

    @Test
    public void testFindByIdNetworkVcenter() {
        Group g = new Group("test", "test");
        g.saveOrUpdate();
        Network n = new Network("test", "test-123");
        n.saveOrUpdate();
        Network n2 = new Network("test", "network-33");
        n2.saveOrUpdate();

        Network n3 = Network.findByIdNetworkVcenter("network-33");
        assertNotNull(n3);
        assertEquals(n2.getId(), n3.getId());
    }

    @Test
    public void testToJSON() {
        Group g = new Group("test", "test");
        g.saveOrUpdate();
        Network n = new Network("name", "network-123");
        n.addGroup(g);
        n.saveOrUpdate();
        JSONObject json = n.toJSON();
        assertEquals((int) n.getId(), json.getInt("id"));
        assertEquals(n.getName(), json.getString("name"));
        assertEquals(2, json.length());
    }

    @Test
    public void testToJSONDetailed() {
        Group g = new Group("test", "test");
        g.saveOrUpdate();
        Network n = new Network("name", "network-123");
        n.addGroup(g);
        n.saveOrUpdate();
        JSONObject json = n.toJSON(true);
        assertEquals((int) n.getId(), json.getInt("id"));
        assertEquals(n.getName(), json.getString("name"));
        assertEquals(n.getIdNetworkVcenter(), json.getString("network"));
        assertTrue(json.has("creation_date"));
        assertTrue(json.has("groups"));
        assertEquals((int) g.getId(), json.getJSONArray("groups").getJSONObject(0).getInt("id"));
        assertEquals(5, json.length());
    }

}
