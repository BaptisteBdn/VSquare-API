package fr.eseo.vsquare.model;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import fr.eseo.vsquare.TestUtils;

public class SnapshotTest {
	
	final private Vm vm = new Vm();
	private User user;
	private Snapshot snap;
	
	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
		vm.setIdVmVcenter("vm-000");
		vm.setName("testVm");
		vm.setDescription("descTest");
		user = User.findByLogin("test");
		if (user == null){
			user = new User("test", "test test");
			user.saveOrUpdate();
		}
		vm.setUser(user);
		vm.saveOrUpdate();
		
		snap = new Snapshot(vm, "12", "noName", "noDesc");
	}
	
	@Test
	public void testSnapshotConstructor() {
		Snapshot snap = new Snapshot(vm, "12", "noName", "noDesc");
		assertTrue(snap.saveOrUpdate());
	}

	@Test
	public void testGetVm() {
		assertEquals(vm, snap.getVm());
	}

	@Test
	public void testSetVm() {
		snap.setVm(null);
		assertNull(snap.getVm());
	}

	@Test
	public void testGetIdSnapshotVcenter() {
		assertEquals("12", snap.getIdSnapshotVcenter());
	}

	@Test
	public void testSetIdSnapshotVcenter() {
		snap.setIdSnapshotVcenter("45");
		assertEquals("45",snap.getIdSnapshotVcenter());
	}

	@Test
	public void testGetName() {
		assertEquals("noName", snap.getName());
	}

	@Test
	public void testSetName() {
		snap.setName("45");
		assertEquals("45",snap.getName());
	}

	@Test
	public void testGetDescription() {
		assertEquals("noDesc", snap.getDescription());
	}

	@Test
	public void testSetDescription() {
		snap.setDescription("45");
		assertEquals("45",snap.getDescription());
	}

	@Test
	public void testGetParent() {
		Snapshot parent = new Snapshot(vm, "12", "noName", "noDesc");
		assertTrue(parent.saveOrUpdate());
		Snapshot child = new Snapshot(vm, "12", "noName", "noDesc", parent);
		assertEquals(parent, child.getParent());
	}

	@Test
	public void testSetParent() {
		snap.saveOrUpdate();
		Snapshot parent = new Snapshot(vm, "3", "noName0", "noDesc0");
		parent.saveOrUpdate();
		snap.setParent(parent);
		assertEquals(parent,snap.getParent());
	}

	@Test
	public void testHasAccessRead() {
		assertTrue(snap.hasAccessRead(user));		
	}

	@Test
	public void testHasAccessWrite() {
		assertTrue(snap.hasAccessWrite(user));
	}

	@Test
	public void testGetChildren() {
		Snapshot parent = new Snapshot(vm, "12", "noName", "noDesc");
		assertTrue(parent.saveOrUpdate());
		Snapshot child = new Snapshot(vm, "12", "noName", "noDesc", parent);
		child.saveOrUpdate();
		assertEquals(1, parent.getChildren().size());
	}

	@Test
	public void testFindByIdInt() {
		assertTrue(snap.saveOrUpdate());
		assertEquals(snap, Snapshot.findById(snap.getId()));
	}

	@Test
	public void testFindByIdSnapshotVcenter() {
		assertTrue(snap.saveOrUpdate());
		assertEquals(snap, Snapshot.findByIdSnapshotVcenter(snap.getIdSnapshotVcenter()));
	}

	@Test
	public void testGetAll() {
		Snapshot parent = new Snapshot(vm, "12", "noName", "noDesc");
		assertTrue(parent.saveOrUpdate());
		Snapshot child = new Snapshot(vm, "12", "noName", "noDesc", parent);
		child.saveOrUpdate();
		snap.saveOrUpdate();
		assertEquals(3, Snapshot.getAll().size());
	}
	
	@Test
	public void testToJSON(){
		Snapshot parent = new Snapshot(vm, "12", "noName", "noDesc");
		assertTrue(parent.saveOrUpdate());
		Snapshot child = new Snapshot(vm, "12", "noName", "noDesc", parent);
		child.saveOrUpdate();
		snap.saveOrUpdate();
		
		JSONObject res = snap.toJSON();
		assertEquals("12", res.getString("id_vcenter"));
		assertEquals("noName", res.getString("name"));
		assertEquals("noDesc", res.getString("description"));
		assertEquals(1, parent.toJSON(true).getJSONArray("children").length());
	}
	
	@Test
	public void testGetSnapshotForVm(){
		snap.saveOrUpdate();
		assertEquals(1, Snapshot.getSnapshotsForVm(vm).size());
	}
}
