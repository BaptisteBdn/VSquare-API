package fr.eseo.vsquare.utils;

import com.vmware.vim25.NotAuthenticated;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VirtualMachineSnapshot;
import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.Vm;
import org.easymock.EasyMock;
import org.json.JSONArray;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.easymock.PowerMock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.rmi.RemoteException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(org.junit.runners.JUnit4.class)
@PrepareForTest({ServiceInstance.class, Utils.class, VCenterManager.class})
public class VCenterManagerErrorTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        Logger.init("logging.properties", TestUtils.LOG_LEVEL);
    }

    @Test
    public void testInitError() {
        PowerMockito.mockStatic(Utils.class);
        when(Utils.getString(anyString())).thenThrow(RemoteException.class);
        assertFalse(VCenterManager.init());
    }

    @Test
    public void testGetConsoleNull() throws Exception {
        PowerMockito.spy(VCenterManager.class);
        PowerMockito.doNothing().when(VCenterManager.class, "checkConnection");
        assertNull(VCenterManager.getRemoteConsoleUrl(null));
    }

    @Test
    public void testGetConsoleError() throws Exception {
        VirtualMachine vmMock = Mockito.mock(VirtualMachine.class);
        when(vmMock.acquireTicket(any())).thenThrow(RemoteException.class);

        Vm vm = new Vm();

        PowerMock.mockStaticPartial(VCenterManager.class, "getVmVCenter", "checkConnection");
        VCenterManager.getVmVCenter(EasyMock.eq(vm));
        PowerMock.expectLastCall().andReturn(vmMock);
        VCenterManager.checkConnection();
        PowerMock.expectLastCall().andVoid();
        PowerMock.replay(VCenterManager.class);

        assertNull(VCenterManager.getRemoteConsoleUrl(vm));
    }

    @Test
    public void testGetConsoleError2() throws Exception {
        VirtualMachine vmMock = Mockito.mock(VirtualMachine.class);
        when(vmMock.acquireTicket(any())).thenThrow(NotAuthenticated.class);

        Vm vm = new Vm();
        PowerMock.mockStaticPartial(VCenterManager.class, "getVmVCenter", "checkConnection");
        VCenterManager.getVmVCenter(EasyMock.eq(vm));
        PowerMock.expectLastCall().andReturn(vmMock);
        VCenterManager.checkConnection();
        PowerMock.expectLastCall().andVoid();
        PowerMock.replay(VCenterManager.class);

        assertNull(VCenterManager.getRemoteConsoleUrl(vm));
    }

    @Test
    public void testGetSnapshotListNull() {
        assertEquals(0, VCenterManager.getSnapshotList(null).length);
    }

    @Test
    public void testSnapshotToJSONNull() {
    	JSONArray o = new JSONArray();
        assertNull(VCenterManager.snapshotToJSON(null, o));
    }

    @Test
    public void testCreateSnapshotNull() throws Exception {
        PowerMockito.spy(VCenterManager.class);
        PowerMockito.doNothing().when(VCenterManager.class, "checkConnection");
        assertNull(VCenterManager.createSnapshot(null, "tmp", "tmp"));
    }

    @Test
    public void testCreateSnapshotError() throws Exception {
        VirtualMachine vmMock = Mockito.mock(VirtualMachine.class);
        Mockito.when(vmMock.createSnapshot_Task(eq("test"), eq("test"), eq(false), eq(false))).thenThrow(RemoteException.class);

        Vm vm = new Vm();
        PowerMock.mockStaticPartial(VCenterManager.class, "getVmVCenter", "checkConnection");
        VCenterManager.checkConnection();
        PowerMock.expectLastCall().andVoid();
        VCenterManager.getVmVCenter(EasyMock.eq(vm));
        PowerMock.expectLastCall().andReturn(vmMock);
        PowerMock.replay(VCenterManager.class);

        assertNull(VCenterManager.createSnapshot(vm, "test", "test"));
    }

    @Test
    public void testRevertSnapshotError() throws Exception {
        Vm vm = new Vm();
        int id = -1;

        VirtualMachineSnapshot snapMock = Mockito.mock(VirtualMachineSnapshot.class);
        Mockito.when(snapMock.revertToSnapshot_Task(eq(null), eq(true))).thenThrow(RemoteException.class);

        PowerMock.mockStaticPartial(VCenterManager.class, "getSnapshotFromTree", "checkConnection");
        VCenterManager.checkConnection();
        PowerMock.expectLastCall().andVoid();
        PowerMock.expectPrivate(VCenterManager.class, "getSnapshotFromTree", EasyMock.eq(vm), EasyMock.eq(id)).andReturn(snapMock);
        PowerMock.replay(VCenterManager.class);

        assertFalse(VCenterManager.revertSnapshot(vm, id));
    }

    @Test
    public void testDeleteSnapshotError() throws Exception {
        Vm vm = new Vm();
        int id = -1;

        VirtualMachineSnapshot snapMock = Mockito.mock(VirtualMachineSnapshot.class);
        Mockito.when(snapMock.removeSnapshot_Task(eq(false))).thenThrow(RemoteException.class);

        PowerMock.mockStaticPartial(VCenterManager.class, "getSnapshotFromTree", "checkConnection");
        VCenterManager.checkConnection();
        PowerMock.expectLastCall().andVoid();
        PowerMock.expectPrivate(VCenterManager.class, "getSnapshotFromTree", EasyMock.eq(vm), EasyMock.eq(id)).andReturn(snapMock);
        PowerMock.replay(VCenterManager.class);

        assertFalse(VCenterManager.deleteSnapshot(vm, id, false));
    }
}
