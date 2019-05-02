package fr.eseo.vsquare.model;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.User.UserType;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GroupTest {

    private final Group group_parent = new Group("test_groupe", "groupe sans parent");
    private final Group group_child = new Group("test_groupe_2", "groupe avec parent", group_parent);
	
	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);

		group_parent.saveOrUpdate();
		group_child.saveOrUpdate();
	}
	
	@Test
	public void testSaveOrUpdateGroup() throws SQLException {
		Group g = new Group("test_group", "test");

		try (Statement st = TestUtils.getConnection().createStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT * FROM user_group WHERE name = 'test_group'")) {
				assertFalse(rs.first());
			}

			assertTrue(g.saveOrUpdate());

			try (ResultSet rs = st.executeQuery("SELECT * FROM user_group WHERE name = 'test_group'")) {
				assertTrue(rs.first());
				assertEquals((int) g.getId(), rs.getInt("id"));
				assertEquals("test_group", rs.getString("name"));
				assertEquals("test", rs.getString("description"));
			}

			g.setDescription("test2");
			assertTrue(g.saveOrUpdate());

			try (ResultSet rs = st.executeQuery("SELECT * FROM user_group WHERE name = 'test_group'")) {
				assertTrue(rs.first());
				assertEquals("test_group", rs.getString("name"));
				assertEquals("test2", rs.getString("description"));
			}
		}
	}
	
	@Test
	public void testGroupCreation(){
		assertEquals(group_parent,group_child.getParent());
		Group group_child_bis = Group.findById(group_child.getId());
		assertEquals(group_child, group_child_bis);
	}

	@Test
	public void testToJSON() {
		Group g = new Group("test_groupe_3","test - un groupe uniquement là pour des tests, et rien d'autre");
		g.saveOrUpdate();
		JSONObject json = g.toJSON();
		assertEquals((int)g.getId(), json.getInt("id"));
		assertEquals("test_groupe_3",json.get("name"));
		assertEquals("test - un groupe uniquement là pour des tests, et rien d'autre", json.get("description"));		
		assertTrue("id_parent_group is NOT NULL",json.isNull("id_parent_group"));
		assertEquals(4, json.length());
	}
	
	@Test
	public void testToJSONDetailed() {
		Group g = new Group("test_groupe_3","test - un groupe uniquement là pour des tests, et rien d'autre");
		g.saveOrUpdate();
		JSONObject json = g.toJSON(true);
		assertEquals((int)g.getId(), json.getInt("id"));
		assertTrue(json.has("creation_date"));
		assertEquals("test_groupe_3",json.get("name"));
		assertEquals("test - un groupe uniquement là pour des tests, et rien d'autre", json.get("description"));		
		assertTrue("id_parent_group is NOT NULL",json.isNull("id_parent_group"));
		assertTrue(json.has("number_users"));
		assertTrue(json.has("users"));
        assertTrue(json.has("networks"));
        assertEquals(8, json.length());
	}
	
	@Test
	public void testGroupGetChildren(){
		List<Group> v = group_parent.getChildren();
		//System.out.println(group_child.getIdParentGroup());
		assertEquals("enfant non unique",1, v.size());
		assertEquals(group_child.getName(), v.get(0).getName());
	}
	
	
	
	@Test
	public void testGroupGetUsersSimple(){
		User user0 = new User("test_user_1",User.UserType.REFERENT,"test TEST");
		user0.addGroup(group_child);
		user0.saveOrUpdate();
		
		List<User> v = group_child.getUsers(false);
		assertEquals("Nombre d'user != 1",1, v.size());
		assertEquals(user0.getId(), v.get(0).getId());
		
		v = group_parent.getUsers(false);
		assertEquals("plus de zéro enfants",0, v.size());

		user0.delete();
	}
	
	@Test
	public void testGroupGetUsersRecursive(){

		User user0 = new User("test_user_1",User.UserType.REFERENT,"test TEST");
		user0.addGroup(group_child);
		user0.saveOrUpdate();
		
		
		User user1 = new User("test_user_2",User.UserType.REFERENT,"test TEST");
		user1.addGroup(group_parent);
		user1.saveOrUpdate();
		
		
		List<User> v = group_child.getUsers(true);
		assertEquals("plus d'un enfant",1, v.size());
		assertEquals(user0.getId(), v.get(0).getId());
		
		v = group_parent.getUsers(false);
		assertEquals("plus d'un enfant",1, v.size());
		assertEquals(user1.getId(), v.get(0).getId());
		
		v = group_parent.getUsers(true);
		assertEquals("plus de 2 enfants",2, v.size());

		user1.delete();
	}
	
	@Test
	public void testGetUsersMultipleGroups(){
		User user0 = new User("testXYZ",User.UserType.STUDENT, "test");
		Group g1 = new Group("test","test");
		g1.saveOrUpdate();
		Group g2 = new Group("test2","test2",g1.getId());
		g2.saveOrUpdate();
		user0.addGroup(g1);
		user0.addGroup(g2);
		user0.saveOrUpdate();
		assertEquals(1,g1.getUsers(true).size());
		assertEquals(1,g2.getUsers(true).size());
	}
	
	@Test
	public void testGetAllChildren(){
		Group parent = new Group("parent", "parent group");
		parent.saveOrUpdate();
		
		Group middle = new Group("middle", "middle group", parent);
		middle.saveOrUpdate();
		
		Group last = new Group("last", "last group", middle);
		last.saveOrUpdate();
		
		Set<Group> groups = parent.getAllChildren();
		assertTrue(groups.contains(middle));
		assertTrue(groups.contains(last));
	}

    @Test
    public void testGetAvailableNetworks() {
        Group g1 = new Group("parent", "parent group");
        g1.saveOrUpdate();

        Set<Network> g1n = g1.getAvailableNetworks();
        assertTrue(g1n.isEmpty());

        Network n1 = new Network("n1", "n1");
        n1.addGroup(g1);
        n1.saveOrUpdate();

        g1 = Group.findById(g1.getId());

        g1n = g1.getAvailableNetworks();
        assertTrue(g1n.contains(n1));

        Group g2 = new Group("middle", "middle group", g1);
        g2.saveOrUpdate();

        Network n2 = new Network("n2", "n2");
        n2.addGroup(g2);
        n2.saveOrUpdate();

        g2 = Group.findById(g2.getId());

        Set<Network> g2n = g2.getAvailableNetworks();
        assertTrue(g2n.contains(n2));
        assertTrue(g2n.contains(n1));

        Group g3 = new Group("last", "last group", g2);
        g3.saveOrUpdate();

        Network n3 = new Network("n3", "n3");
        n3.addGroup(g3);
        n3.saveOrUpdate();

        g3 = Group.findById(g3.getId());

        Set<Network> g3n = g3.getAvailableNetworks();
        assertTrue(g3n.contains(n3));
        assertTrue(g3n.contains(n2));
        assertTrue(g3n.contains(n1));
    }
    
    @Test
    public void testGetAvailableTemplates() {
		User u = new User("test_user", UserType.ADMIN, "test");
		
		u.saveOrUpdate();

        Group g1 = new Group("parent", "parent group");
        g1.saveOrUpdate();

        Set<Vm> g1n = g1.getAvailableTemplates();
        assertTrue(g1n.isEmpty());

        Vm n1 = new Vm(u, "id", "name", "desc");
        n1.setTemplate(true);
        assertTrue(n1.saveOrUpdate());
        
        g1.addTemplate(n1);
        
        g1.saveOrUpdate();

        g1 = Group.findById(g1.getId());

        g1n = g1.getAvailableTemplates();
        assertTrue(g1n.contains(n1));

        Group g2 = new Group("middle", "middle group", g1);
        g2.saveOrUpdate();

        Vm n2 = new Vm(u, "id2", "name", "desc");
        n2.setTemplate(true);
        n2.saveOrUpdate();
        g2.getTemplates();
        g2.addTemplate(n2);
        
        g2.saveOrUpdate();
        
        g2 = Group.findById(g2.getId());

        Set<Vm> g2n = g2.getAvailableTemplates();
        assertTrue(g2n.contains(n2));
        assertTrue(g2n.contains(n1));

        Group g3 = new Group("last", "last group", g2);
        g3.saveOrUpdate();


        Vm n3 = new Vm(u, "id3", "name", "desc");
        n3.setTemplate(true);
        n3.saveOrUpdate();
        g3.addTemplate(n3);
        
        g3.saveOrUpdate();

        g3 = Group.findById(g3.getId());

        Set<Vm> g3n = g3.getAvailableTemplates();
        assertTrue(g3n.contains(n3));
        assertTrue(g3n.contains(n2));
        assertTrue(g3n.contains(n1));
    }

}