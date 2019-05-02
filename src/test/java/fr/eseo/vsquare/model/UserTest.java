package fr.eseo.vsquare.model;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.Utils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class UserTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
	}
	
	@Test
    public void testSaveOrUpdateUser() throws SQLException, IllegalAccessException, NoSuchFieldException {
		User u = new User("test_user", UserType.ADMIN, "test");

		try (Statement st = TestUtils.getConnection().createStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT * FROM user WHERE 1")) {
				assertFalse(rs.first());
			}

			assertTrue(u.saveOrUpdate());

			try (ResultSet rs = st.executeQuery("SELECT * FROM user WHERE 1")) {
				assertTrue(rs.first());
				assertEquals((int) u.getId(), rs.getInt("id"));
				assertEquals("test_user", rs.getString("login"));
				assertEquals("test", rs.getString("common_name"));
			}

            Field commonName = User.class.getDeclaredField("commonName");
            commonName.setAccessible(true);
            commonName.set(u, "test3");

			assertTrue(u.saveOrUpdate());

			try (ResultSet rs = st.executeQuery("SELECT * FROM user WHERE 1")) {
				assertTrue(rs.first());
				assertEquals("test3", rs.getString("common_name"));
			}
		}

	}
	
	@Test
	public void testUserTypeComparison() {
		assertFalse(UserType.STUDENT.greaterThan(UserType.STUDENT));
		assertFalse(UserType.STUDENT.greaterThan(UserType.REFERENT));
		assertTrue(UserType.REFERENT.greaterThan(UserType.STUDENT));
		assertTrue(UserType.ADMIN.greaterThan(UserType.REFERENT));
		assertFalse(UserType.STUDENT.lesserThan(UserType.STUDENT));
		assertFalse(UserType.REFERENT.lesserThan(UserType.STUDENT));
		assertTrue(UserType.STUDENT.lesserThan(UserType.REFERENT));
		assertTrue(UserType.REFERENT.lesserThan(UserType.ADMIN));
	}
	
	@Test
	public void testFindByLogin() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		User u2 = User.findByLogin("test_user");

		assertEquals(u.getId(), u2.getId());
		assertEquals("test", u2.getCommonName());

		u.delete();

		assertNull(User.findByLogin("test_user"));
	}
	
	@Test
    public void testGenerateToken() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		List<Token> list = Token.getAll();
		assertEquals(0, list.size());
		
		Token t = u.generateToken();
		assertEquals(u,t.getUser());
		
		list = Token.getAll();
		assertEquals(1, list.size());
		assertEquals(t, list.get(0));
	}
	
	@Test
	public void testGetVms() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

		assertEquals(0, u.getVms().size());

        Vm vm = new Vm(u, "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		List<Vm> vms = u.getVms();
		
		assertEquals(1, vms.size());
		assertEquals(vm.getId(), vms.get(0).getId());
	}
	
	@Test
	public void testGetEffectivePermissions() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();

        assertEquals(Utils.getInt("default_cpu_count"), u.getEffectivePermission().getCpuCount());
        assertEquals(Utils.getInt("default_vm_count"), u.getEffectivePermission().getVmCount());
        assertEquals(Utils.getInt("default_memory_size"), u.getEffectivePermission().getMemorySize());
        assertEquals(Utils.getInt("default_disk_storage"), u.getEffectivePermission().getDiskStorage());
	}
	
	@Test
	public void test_getGroupsForUserNonNested(){
		User user = new User("test_groups",UserType.STUDENT, "test");
		Group group_parent = new Group("child","0 parents");
		Group group_middle = new Group("middle","1 parent - 1 child");
		Group group_child = new Group("parent","1 child - 1 grandchildren");
		
		group_parent.saveOrUpdate();
		group_middle.saveOrUpdate();
		group_child.saveOrUpdate();
		
		//test user in highest of them all
		user.addGroup(group_parent);
		user.saveOrUpdate();
		assertTrue(user.getGroups().contains(group_parent));
		Set<Group> groups = user.getGroupsForUser();
		assertEquals(2, groups.size());
		
		//test user in middle of them all
		user.addGroup(group_middle);
		user.saveOrUpdate();
		groups = user.getGroupsForUser();
		assertEquals(3, groups.size());

		//test user in lowest of them all
		user.removeGroup(group_middle);
		user.addGroup(group_child);
		user.saveOrUpdate();
		groups = user.getGroupsForUser();
		assertEquals(3, groups.size());
	}
	
	@Test
	public void test_get_groups_for_user_nested(){
		User user = new User("test_groups",UserType.STUDENT, "test");
		Group group_parent = new Group("child","0 parents");
		Group group_middle = new Group("middle","1 parent - 1 child", group_parent);
		Group group_child = new Group("parent","1 child - 1 grandchildren", group_middle);
		
		group_parent.saveOrUpdate();
		group_middle.saveOrUpdate();
		group_child.saveOrUpdate();
		
		//test user in highest of them all
		user.addGroup(group_parent);
		user.saveOrUpdate();
		assertTrue(user.getGroups().contains(group_parent));
		Set<Group> groups = user.getGroupsForUser();
		assertEquals(2, groups.size());
		
		//test user in middle of them all
		user.removeGroup(group_parent);
		user.addGroup(group_middle);
		user.saveOrUpdate();
		groups = user.getGroupsForUser();
		assertEquals(3, groups.size());

		//test user in lowest of them all
		user.removeGroup(group_middle);
		user.addGroup(group_child);
		user.saveOrUpdate();
		groups = user.getGroupsForUser();
		assertEquals(4, groups.size());

		//test user remove all sub groups
		user.addGroup(group_parent);
		user.addGroup(group_middle);
		user.removeGroup(group_parent);
		user.saveOrUpdate();
		groups = user.getGroupsForUser();
		assertEquals(1, groups.size());
	}
	
	@Test
	public void test_get_groups_for_admin(){
		User user = new User("test_groups",UserType.ADMIN, "test");
		user.saveOrUpdate();
		Group group_parent = new Group("child","0 parents");
		Group group_middle = new Group("middle","1 parent - 1 child", group_parent);
		Group group_child = new Group("parent","1 child - 1 grandchildren", group_middle);
		
		group_parent.saveOrUpdate();
		group_middle.saveOrUpdate();
		group_child.saveOrUpdate();
		
		
		Set<Group> groups = user.getGroupsForUser();
		assertEquals(6, groups.size());
	}

	@Test
	public void testToJSON() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		JSONObject json = u.toJSON();
		assertEquals((int)u.getId(), json.getInt("id"));
		assertEquals(u.getLogin(), json.getString("login"));
		assertEquals(u.getCommonName(), json.getString("common_name"));
		assertEquals(u.getType().toString(), json.getString("type"));
		assertEquals(4, json.length());
	}
	
	@Test
	public void testToJSONDetailed() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		JSONObject json = u.toJSON(true);
		assertEquals((int)u.getId(), json.getInt("id"));
		assertTrue(json.has("creation_date"));
		assertEquals(u.getLogin(), json.getString("login"));
		assertEquals(u.getCommonName(), json.getString("common_name"));
		assertEquals(u.getType().toString(), json.getString("type"));
		assertTrue(json.has("groups_id"));
		assertEquals(6, json.length());
	}

    @Test
    public void testGetAvailableNetworks() {
        Group g1 = new Group("parent", "parent group");
        g1.saveOrUpdate();

        User u = new User("test_user", UserType.ADMIN, "test");
        u.addGroup(g1);
        u.saveOrUpdate();

        assertTrue(u.getAvailableNetworks().isEmpty());

        Network n1 = new Network("n1", "n1");
        n1.addGroup(g1);
        n1.saveOrUpdate();

        u = User.findById(u.getId());
        assertTrue(u.getAvailableNetworks().contains(n1));
    }

    @Test
    public void testCheckGroups() {
        User user = new User("test_groups", UserType.ADMIN, "test");
        user.removeGroup(UserType.ADMIN.getDefaultGroup());
        user.saveOrUpdate();

        user = User.findById(user.getId());
        Set<Group> groups = user.getGroups();
        assertEquals(0, groups.size()); //no admin group

        assertTrue(user.checkGroups());
        user.saveOrUpdate();

        assertFalse(user.checkGroups());

        user = User.findById(user.getId());
        groups = user.getGroups();
        assertEquals(1, groups.size()); //admin group is back

        Group g1 = new Group("parent", "parent group");
        g1.saveOrUpdate();


        user.removeGroup(UserType.ADMIN.getDefaultGroup());
        user.addGroup(g1);
        user.addGroup(UserType.REFERENT.getDefaultGroup());
        user.saveOrUpdate();

        user = User.findById(user.getId());
        groups = user.getGroups();
        assertEquals(2, groups.size()); //

        assertTrue(user.checkGroups());
        user.saveOrUpdate();

        user = User.findById(user.getId());
        groups = user.getGroups();
        assertEquals(1, groups.size());
    }

    @Test
    public void testUtilCheckGroups() {
        User user = new User("test_groups", UserType.ADMIN, "test");
        user.saveOrUpdate();

        Group g1 = new Group("parent", "parent group");
        g1.saveOrUpdate();

        user.removeGroup(UserType.ADMIN.getDefaultGroup());
        user.addGroup(g1);
        user.addGroup(UserType.REFERENT.getDefaultGroup());
        user.saveOrUpdate();

        Set<Group> groups = user.getGroups();
        assertEquals(2, groups.size());

        Utils.checkUsers();

        groups = User.findById(user.getId()).getGroups();
        assertEquals(1, groups.size());
    }
	
}
