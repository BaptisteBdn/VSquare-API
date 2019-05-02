 package fr.eseo.vsquare.utils;

 import com.vmware.vim25.VirtualMachineGuestOsIdentifier;
import com.vmware.vim25.VirtualMachineSnapshotTree;
 import com.vmware.vim25.mo.*;
 import fr.eseo.vsquare.TestUtils;
 import fr.eseo.vsquare.model.Snapshot;
 import fr.eseo.vsquare.model.User;
 import fr.eseo.vsquare.model.Vm;
 import org.json.JSONArray;
 import org.json.JSONObject;
 import org.junit.*;
 import org.junit.runners.MethodSorters;
 import org.mockito.Mockito;
 import org.powermock.api.mockito.PowerMockito;

 import java.io.File;
 import java.io.IOException;
 import java.nio.file.Files;
 import java.rmi.RemoteException;
 import java.text.SimpleDateFormat;
 import java.util.Calendar;
import java.util.UUID;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VCenterManagerTest {

	private static String VM_ID; //put an existing vm's id - not much choice here
	private static String VM_NAME;

	private static int idSnapshot = -1;

	private static String portGroupName = "testPortGroup" + System.currentTimeMillis();

	private static String idPortGroup;
	
	private static Vm getFakeVm(){
		Vm vm = new Vm();
		vm.setIdVmVcenter(VM_ID);
		vm.setName(VM_NAME);
		User user = new User("test User éè@", "USER test");
		vm.setUser(user);
		return vm;
	}

	@BeforeClass
	public static void init() throws Exception{
		Logger.init("logging.properties", TestUtils.LOG_LEVEL);
		assertTrue(VCenterManager.init());
		VSphereManager.init(Utils.getString("vsphere_host"));
		
		//create a VM to test
		String name = "CC2_vm_test"+UUID.randomUUID();
		String id = VSphereConnector.createVm(VCenterManager.findEmptiestDatastore(), name);

		assertNotNull(id);
		VM_ID = id;
		VM_NAME = name;
	}

	@AfterClass
	public static void cleanUp() throws RemoteException, InterruptedException{
		VCenterManager.deleteVm(getFakeVm());
		VCenterManager.exit();
	}

	private void compareSnapshotToJSON(VirtualMachineSnapshotTree snapshot, JSONObject json) {
		assertNotNull(json);
		assertEquals(snapshot.getName(), json.getString("name"));
		assertEquals(snapshot.getDescription(), json.getString("description"));
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		assertEquals(dateFormat.format(snapshot.getCreateTime().getTime()), json.get("creationDate"));
	}

	@Test
	public void test01_testGetVmCenterNull() {
		assertNull(VCenterManager.getVmVCenter((Vm) null));
		Vm vmTest = new Vm();
		assertNull(VCenterManager.getVmVCenter(vmTest));
		vmTest.setIdVmVcenter("vm-badID");
		assertNull(VCenterManager.getVmVCenter(vmTest));

		vmTest.setIdVmVcenter("vm-001");
		assertNull(VCenterManager.getVmVCenter(vmTest));
	}

	@Test
	public void test02_testGetVmVcenterSuccess() {
		Vm vm = new Vm();
		vm.setIdVmVcenter(VM_ID);
		VirtualMachine vmVcenter = VCenterManager.getVmVCenter(vm);
		assertEquals(VM_NAME, vmVcenter.getName());
	}

	@Test
	public void test03_testGetVmVcenterList() {
		VirtualMachine[] list = VCenterManager.getVmVcenterList();
		VirtualMachine vm0 = list[0];
		System.out.println("UUID : "+vm0.getConfig().getInstanceUuid());
		System.out.println("UUID 2: "+vm0.getConfig().getLocationId());
		System.out.println("name : "+vm0.getConfig().getName());
		assertTrue(0 != list.length);//yeah, we do not know how many VM we actually have on the server. we can just guess it's more than 0 though
	}

	@Test
	public void test04_testSnapshotToJSONSimple() {
		VirtualMachineSnapshotTree snapshot = new VirtualMachineSnapshotTree();
		snapshot.setDescription("snap's description");
		Calendar calendar = Calendar.getInstance();
		calendar.set(2018, 1, 1, 1, 1, 1);
		snapshot.setCreateTime(calendar);
		snapshot.setName("snap's name");

		JSONArray mainOutput = new JSONArray();
		JSONObject json1 = VCenterManager.snapshotToJSON(snapshot, mainOutput);
		compareSnapshotToJSON(snapshot, json1);
	}

	@Test
	public void test05_testSnapshotToJSONMultiple() {
		VirtualMachineSnapshotTree snapshot = new VirtualMachineSnapshotTree();
		snapshot.setDescription("snap's description");
		Calendar calendar = Calendar.getInstance();
		calendar.set(2018, 1, 1, 1, 1, 1);
		snapshot.setCreateTime(calendar);
		snapshot.setName("snap's name");

		VirtualMachineSnapshotTree snapshot2 = new VirtualMachineSnapshotTree();
		snapshot2.setDescription("snap's description 2");
		Calendar calendar2 = Calendar.getInstance();
		calendar2.set(2019, 1, 1, 1, 1, 1);
		snapshot2.setCreateTime(calendar);
		snapshot2.setName("snap's name 2");

		VirtualMachineSnapshotTree[] children = {snapshot2};

		snapshot.setChildSnapshotList(children);

		JSONArray mainOutput = new JSONArray();

		JSONObject json1 = VCenterManager.snapshotToJSON(snapshot, mainOutput);
		compareSnapshotToJSON(snapshot, json1);
		assertEquals(1, json1.getJSONArray("children").length());
		compareSnapshotToJSON(snapshot2, json1.getJSONArray("children").getJSONObject(0));
		System.out.println(mainOutput);
	}

	@Test
	public void test06_testGetTicket() {
		Vm vm = new Vm();
		vm.setIdVmVcenter(VM_ID);
		VSphereManager.init(Utils.getString("vsphere_host"));
		if (VSphereConnector.getVmPowerState(vm) != VSphereConnector.VmPowerState.POWERED_ON)
			VSphereConnector.setVmPowerStart(vm);
		String ticket = VCenterManager.getRemoteConsoleUrl(vm);
			assertNotNull(ticket);
	}

	@Test
	public void test07_testCreateSnapshot() {
		Vm vm = new Vm();
		vm.setIdVmVcenter(VM_ID);
		String snapId = VCenterManager.createSnapshot(vm, "test", "test");
		assertNotNull(snapId);
		System.out.println("snapshot ID : "+ snapId);
	}

	@Test
	public void test08_testGetSnapshotList() {
		Vm vm = new Vm();
		vm.setIdVmVcenter(VM_ID);
		VirtualMachine vmVcenter = VCenterManager.getVmVCenter(vm);
		assertNotNull(vmVcenter.getName());
		JSONArray list = VCenterManager.getSnapshotListJSON(vm);
		assertTrue(list.length() > 0);
		boolean found = false;
		for (int i = 0; i < list.length(); i++) {
			JSONObject snap = list.getJSONObject(i);
			if (snap.getString("name").equals("test") &&
					snap.getString("description").equals("test")) {
				found = true;
				idSnapshot = snap.getInt("id");
				System.out.println("id snapshot : "+idSnapshot);
			}
		}
		assertTrue(found);
	}

	@Test
	@Ignore
	public void test09_testEditSnapshot() throws InterruptedException{
		Vm vm = new Vm();
		vm.setIdVmVcenter(VM_ID);
		VirtualMachine vmVcenter = VCenterManager.getVmVCenter(vm);
		assertNotNull(vmVcenter.getName());
		assertTrue(-1 != idSnapshot);

		VCenterManager.editSnapshot(vm, idSnapshot, "edited name", "edited description");

		VirtualMachineSnapshotTree snapshot = VCenterManager.searchSnapshotTree(vm, idSnapshot);

		assertEquals("edited name",snapshot.getName());
		assertEquals("edited description", snapshot.getDescription());
	}

	@Test
	public void test10_testRevertSnapshot() {
		assertTrue(idSnapshot >= 0);
		Vm vm = new Vm();
		vm.setIdVmVcenter(VM_ID);
		assertNotNull(VCenterManager.createSnapshot(vm, "test2", "test"));
		assertTrue(VCenterManager.revertSnapshot(vm, idSnapshot));
	}

	@Test
	public void test11_testDeleteSnapshot() {
		Vm vm = new Vm();
		vm.setIdVmVcenter(VM_ID);
		JSONArray list = VCenterManager.getSnapshotListJSON(vm);
		assertTrue(list.length() > 0);
		for (int i = 0; i < list.length(); i++) {
			JSONObject snap = list.getJSONObject(i);
			assertTrue("deletion unsuccessful : "+snap.getString("name"),VCenterManager.deleteSnapshot(vm, snap.getInt("id"), false));
		}
	}
	
	@Test
	public void test12_downloadAndPackageOVA() throws IOException{
		Vm vm = getFakeVm();
		
		assertNotNull(vm);
		VCenterManager.shutdownVM(vm);//shutdown the vm if necessary
		
		File file = VCenterManager.downloadAndPackageOVA(vm);
		
		assertNotNull(file);
		System.out.println("path to downloaded ova > "+file.getAbsolutePath());
		Files.delete(file.toPath());
	}
	
	@Test
	public void testDeleteSnapshotFromObject(){
		Vm vm = getFakeVm();
		String snapId = VCenterManager.createSnapshot(vm, "newSnap", "newDesc");
		assertNotNull(snapId);
		Snapshot snap = new Snapshot(vm, snapId, "name", "desc");
		assertTrue(VCenterManager.deleteSnapshot(snap));
	}

	@Test
	public void testGetVmVcenterString(){
		String test = VM_NAME;
		VirtualMachine vm = VCenterManager.getVmVCenter(test);
		assertNotNull("VM by name was NOT found",vm);
		assertEquals(test, vm.getName());
	}

	@Test
	public void testGetOVFinOVA() throws IOException{
		String pathToOVAtest = "vmware_compatible_light.ova";
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource(pathToOVAtest).getFile());
		assertNotNull(VCenterManager.findOVFinOVA(file));
	}

	@Test
	public void testUploadOVA() throws IOException{
		String pathToOVAtest = "vmware_compatible_light.ova";
		String newName = "CC2_importTest_ova";

		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource(pathToOVAtest).getFile());


		String idVmVcenter = VCenterManager.importLocalOVA(file, newName);
		assertNotNull(idVmVcenter);

		Vm vm = new Vm();
		vm.setIdVmVcenter(idVmVcenter);
		VSphereConnector.deleteVm(vm);
	}

	@Test
	public void testcreateVCenterVmName(){
		Vm vm = getFakeVm();
		assertNotNull(VCenterManager.createVCenterVmName(vm));
	}

	@Test
	public void testGetVmFolder() throws RemoteException{
		Vm vm = getFakeVm();
		VirtualMachine vmVcenter = VCenterManager.getVmVCenter(vm);
		assertNotNull("Initialisation failed - test aborted",vmVcenter);
		Folder parent = VCenterManager.getVmFolder(vmVcenter);
		assertNotNull(parent);
	}

	@Test
	public void testCloneVm() throws IOException, InterruptedException{
		Vm vm = getFakeVm();
		String newVmId = VCenterManager.cloneVm(vm, "test", false);

		assertNotNull(newVmId);
		Vm vm2 = new Vm();
		vm2.setIdVmVcenter(newVmId);
		VSphereConnector.deleteVm(vm2);
	}

	@Test
	public void testCloneVmFromSnapshot() throws IOException, InterruptedException{
		Vm vm = new Vm();
		vm.setIdVmVcenter(VM_ID);

		VirtualMachineSnapshotTree[] list = VCenterManager.getSnapshotList(vm);

		if (list.length ==  0){
			fail("no snapshot to clone");
		}
		
		Snapshot snapshot = new Snapshot(vm, list[0].getSnapshot().val, "name","desc");

		String newVmId = VCenterManager.vmFromSnapshot(snapshot , "cloned snap");

		assertNotNull(newVmId);

		Vm vm2 = new Vm();
		vm2.setIdVmVcenter(newVmId);
		VSphereConnector.deleteVm(vm2);
	}

	@Test
	public void testCloneVmFromSnapshotBadParameter() throws IOException, InterruptedException{
		Vm vm = new Vm();
		vm.setIdVmVcenter("bad id");
		
		Snapshot snapshot = new Snapshot(vm, "snapshot-123", "name", "desc");
		
		String newVmId = VCenterManager.vmFromSnapshot(snapshot , "cloned snap");

		assertNull(newVmId);
	}
	
	@Test
	@Ignore
	public void testModifVm() throws Exception{
		Vm vm = getFakeVm();
		PowerMockito.mockStatic(VCenterManager.class);
		Mockito.when(VCenterManager.waitForTask(Mockito.any()))
		.thenReturn(Task.SUCCESS);
		Mockito.when(VCenterManager.modifyDisk(Mockito.any(),Mockito.any(),Mockito.any())).thenCallRealMethod();
		Mockito.when(VCenterManager.getVmVCenter(vm)).thenCallRealMethod();
		boolean result = VCenterManager.modifyDisk(vm, "Hard disk 1", (long) 1024);
		assertTrue(result);
	}

	@Test
	@Ignore
	public void testModifVmException() throws Exception{
		Vm vm = getFakeVm();
		PowerMockito.mockStatic(VCenterManager.class);
		Mockito.when(VCenterManager.waitForTask(Mockito.any()))
		.thenThrow(InterruptedException.class);
		Mockito.when(VCenterManager.modifyDisk(Mockito.any(),Mockito.any(),Mockito.any())).thenCallRealMethod();
		Mockito.when(VCenterManager.getVmVCenter(vm)).thenCallRealMethod();
		boolean result = VCenterManager.modifyDisk(vm, "Hard disk 1",(long) 1024);
		assertTrue(!result);
	}

	@Test
	public void testModifVmFail() throws IOException, InterruptedException{
		Vm vm = getFakeVm();
		boolean result = VCenterManager.modifyDisk(vm, "Hard disk 1", (long) 1024);
		assertTrue(!result);
	}

	@Test
	public void testModifVmNull() throws IOException, InterruptedException{
		Vm vm = new Vm();
		boolean result = VCenterManager.modifyDisk(vm, "Hard disk 1",(long) 1024);
		assertTrue(!result);
	}

	@Test
	public void testModifVmNoDisk() throws IOException, InterruptedException{
		Vm vm = getFakeVm();
		boolean result = VCenterManager.modifyDisk(vm, "No disk",(long) 1024);
		assertTrue(!result);
	}	
	
	@Test
    @Ignore
	public void testCloneTemplate() throws IOException, InterruptedException {
		Vm myVm = new Vm();
		myVm.setUser(new User("beduneba", "Baptiste Beduneau"));
		myVm.setName("Template_test");
        myVm.setIdVmVcenter("vm-457");
        VCenterManager.cloneVm(myVm, "test", true);
	}
	
	@Test
	public void testGetTemplate() {
		System.out.println(VCenterManager.getTemplate());
	}

	@Test
	public void testGetHostInfo() throws RemoteException{
		Vm vm = getFakeVm();	

		assertNotNull(VCenterManager.findHost(vm));
		assertNotNull(VCenterManager.findHostIp(vm));
		
		vm = new Vm();
		vm.setIdVmVcenter("vm-707");
		System.out.println(VCenterManager.findHostIp(vm));
	}
	
	@Test
	public void testGetHostCapacity() throws RemoteException{
		HostSystem host = VCenterManager.findBestHost();
		
		assertNotNull(host);
		System.out.println(host.getName());
	}
	
	@Test
	public void testFindEmptiestDatastore() throws RemoteException{
		Datastore ds = VCenterManager.findEmptiestDatastore();
		assertNotNull(ds);
		System.out.println("emptiest datastore : "+ds.getName() + " ( "+ ds.getInfo().getFreeSpace()/ (1024*1024) + " Mo Free)");
	}
	
	@Test
	public void testfindHostWithEmptiestDatastore() throws RemoteException{
		HostSystem host = VCenterManager.findHostWithEmptiestDatastore();
		assertNotNull(host);
		System.out.println(host.getName());	
	}

	@Test
	public void testPortGroup_a_create() {
		idPortGroup = VCenterManager.createNetwork("DSwitch", portGroupName, 2, true);
		assertNotNull(idPortGroup);
	}

	@Test
	public void testPortGroup_b_edit() {
		assertNotNull(idPortGroup);
		assertTrue(VCenterManager.editNetwork(idPortGroup, 15));
	}

	@Test
	public void testPortGroup_c_delete() {
		assertNotNull(idPortGroup);
		assertTrue(VCenterManager.deleteNetwork(idPortGroup));
	}

	@Test
	public void testgetDownloadFolderNameBasic() {
		Vm vm = getFakeVm();
		vm.setName("abc");
		User user = new User("login", "commonName");
		vm.setUser(user);
		assertEquals("login_abc", VCenterManager.getDownloadFolderName(vm));
	}

	@Test
	public void testgetDownloadFolderNameWithBadChar() {
		Vm vm = getFakeVm();
		vm.setName("abc def @éè");
		User user = new User("login !??", "commonName");
		vm.setUser(user);
		assertEquals("login_abcdef", VCenterManager.getDownloadFolderName(vm));
	}
	
	@Test
	public void testMountISO() throws RemoteException{
		Vm vm = getFakeVm();
		assertTrue(VCenterManager.mountISO(vm, "[datastore1-34]+ISO/debian-9.3.0-amd64-netinst.iso"));
	}
	
	@Test
	public void testUploadFile() throws RemoteException{
		Datastore ds = VCenterManager.findEmptiestDatastore();
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource("slitaz-5.0-rc1.iso").getFile());
		assertTrue(VCenterManager.sendFileToAPI(file, ds, "test/CC2testupload_"+UUID.randomUUID()));
	}
	
	@Test
	public void testGVirtualMachineGuestOsIdentifier(){
		VirtualMachineGuestOsIdentifier guest = VirtualMachineGuestOsIdentifier.debian6_64Guest;
		assertEquals("debian6_64Guest", guest.name());
	}
		
	@Test
    public void testCreateVM_VSphere() throws RemoteException, InterruptedException{

    	System.out.println(VCenterManager.findEmptiestDatastore().getName());
    	String vmId = VSphereConnector.createVm(VCenterManager.findEmptiestDatastore(), "testCreateVM"+UUID.randomUUID());
    	assertNotNull(vmId);
    	System.out.println("created > "+vmId);
    	Vm vm = new Vm();
    	vm.setIdVmVcenter(vmId);
    	assertTrue(VCenterManager.deleteVm(vm));
    }
	
	@Test
	public void testImportISO() throws IOException, InterruptedException{
		Datastore ds = VCenterManager.findEmptiestDatastore();
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource("slitaz-5.0-rc1.iso").getFile());
		String vmId = VCenterManager.importLocalISO(file, "created From ISO"+UUID.randomUUID());
		
		assertNotNull(vmId);
		System.out.println(vmId);

    	Vm vm = new Vm();
    	vm.setIdVmVcenter(vmId);
    	assertTrue(VCenterManager.deleteVm(vm));
	}
}
