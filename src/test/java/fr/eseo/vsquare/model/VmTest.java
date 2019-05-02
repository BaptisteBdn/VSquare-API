package fr.eseo.vsquare.model;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.User.UserType;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.*;

public class VmTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
	}
	
	@Test
	public void testSaveOrUpdateVm() throws SQLException {
		User u = new User("test_user", UserType.ADMIN, "test");

		u.saveOrUpdate();

		Vm v  = new Vm(u, "id", "name", "desc");

		try (Statement st = TestUtils.getConnection().createStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT * FROM vm WHERE 1")) {
				assertFalse(rs.first());
			}

			assertTrue(v.saveOrUpdate());

			try (ResultSet rs = st.executeQuery("SELECT * FROM vm WHERE 1")) {
				assertTrue(rs.first());
				assertEquals((int) v.getId(), rs.getInt("id"));
				assertEquals("id", rs.getString("id_vm_vcenter"));
				assertEquals("name", rs.getString("name"));
				assertEquals("desc", rs.getString("description"));
			}

			v.setName("name2");
			assertTrue(v.saveOrUpdate());

			try (ResultSet rs = st.executeQuery("SELECT * FROM vm WHERE 1")) {
				assertTrue(rs.first());
				assertEquals("name2", rs.getString("name"));
			}
		}
	}
	
	@Test
	public void findByIdVmVcenter() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		
		Vm vm = new Vm(u, "vm-test", "test", "test");

		vm.saveOrUpdate();
		
		Vm vm2 = Vm.findByIdVmVcenter("vm-test");

		assertEquals(vm.getId(), vm2.getId());
		assertEquals("test", vm2.getName());

		u.delete();
	}

	@Test
	public void testHasAccessRead(){
		User student = new User("test_user", UserType.STUDENT, "test");
		User referent = new User("test_user", UserType.REFERENT, "test");
		User admin = new User("test_user", UserType.ADMIN, "test");
		Vm vm = new Vm(student, "vm-test", "test", "test");
		
		assertTrue(vm.hasAccessRead(student));
		assertTrue(vm.hasAccessRead(referent));
		assertTrue(vm.hasAccessRead(admin));
		
		vm.setUser(referent);
		assertFalse(vm.hasAccessRead(student));
		assertTrue(vm.hasAccessRead(referent));
		assertTrue(vm.hasAccessRead(admin));
		
		vm.setUser(admin);
		assertFalse(vm.hasAccessRead(student));
		assertFalse(vm.hasAccessRead(referent));
		assertTrue(vm.hasAccessRead(admin));
	}
	
	@Test
	public void testHasAccessWrite(){
		User student = new User("test_user", UserType.STUDENT, "test");
		User referent = new User("test_user", UserType.REFERENT, "test");
		User admin = new User("test_user", UserType.ADMIN, "test");
		Vm vm = new Vm(student, "vm-test", "test", "test");
		
		assertTrue(vm.hasAccessWrite(student));
		assertFalse(vm.hasAccessWrite(referent));
		assertTrue(vm.hasAccessWrite(admin));
		
		vm.setUser(referent);
		assertFalse(vm.hasAccessWrite(student));
		assertTrue(vm.hasAccessWrite(referent));
		assertTrue(vm.hasAccessWrite(admin));
		
		vm.setUser(admin);
		assertFalse(vm.hasAccessWrite(student));
		assertFalse(vm.hasAccessWrite(referent));
		assertTrue(vm.hasAccessWrite(admin));
	}
	
	@Test
	public void testGetAll(){
		User student = new User("test_user", UserType.STUDENT, "test");
		Vm vm = new Vm(student, "vm-test", "test", "test");
		student.saveOrUpdate();
		vm.saveOrUpdate();
		assertEquals(1, Vm.getAll().size());
	}
	
	@Test
	public void testToJSON() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		Vm v = new Vm(u,"test","name","desc");
		v.saveOrUpdate();
		JSONObject json = v.toJSON();
		assertEquals((int)v.getId(), json.getInt("id"));
        assertEquals(v.getIdVmVcenter(), json.getString("vm"));
		assertEquals(v.getName(), json.getString("name"));
        assertEquals(v.getDescription(), json.getString("desc"));
		assertTrue(json.has("user"));
		assertEquals((int)u.getId(),json.getJSONObject("user").getInt("id"));
		assertEquals(5, json.length());
	}
	
	@Test
	public void testToJSONDetailed() {
		User u = new User("test_user", UserType.ADMIN, "test");
		u.saveOrUpdate();
		Vm v = new Vm(u,"test","name","desc");
		v.saveOrUpdate();
		JSONObject json = v.toJSON(true);
		assertEquals((int)v.getId(), json.getInt("id"));
		assertTrue(json.has("creation_date"));
        assertEquals(v.getIdVmVcenter(), json.getString("vm"));
		assertEquals(v.getName(), json.getString("name"));
        assertEquals(v.getDescription(), json.getString("desc"));
		assertTrue(json.has("user"));
		assertEquals((int)u.getId(),json.getJSONObject("user").getInt("id"));
		assertEquals(6, json.length());
	}
}
