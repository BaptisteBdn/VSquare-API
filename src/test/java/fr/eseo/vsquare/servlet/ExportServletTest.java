package fr.eseo.vsquare.servlet;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.HashMap;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.DownloadLink;
import fr.eseo.vsquare.model.Token;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.model.Vm;
import fr.eseo.vsquare.servlet.VMsServletTest.StubServletOutputStream;
import fr.eseo.vsquare.utils.LDAPUtils;
import fr.eseo.vsquare.utils.ServletUtils;
import fr.eseo.vsquare.utils.Utils;
import fr.eseo.vsquare.utils.VCenterManager;
import fr.eseo.vsquare.utils.VSphereConnector;
import fr.eseo.vsquare.utils.VSphereConnector.VmPowerState;
import fr.eseo.vsquare.utils.VSphereManager;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "java.*", "javax.*", "org.*", "fr.eseo.vsquare.model.*", "fr.eseo.vsquare.utils.DatabaseManager" })
@PrepareForTest({ ServletUtils.class, LDAPUtils.class})
public class ExportServletTest {
	
	@BeforeClass
	public static void setUp() throws Exception {
		VSphereManager.init();
	}
	
	@Before
	public void beforeEach() throws Exception{
		TestUtils.initTest(true);
	}
	
	@Test
	public void testExportSuccess() throws Exception {
		String token = TestUtils.login("test", "test", UserType.STUDENT);
		Token t = Token.findByValue(token);

		Vm vm = new Vm(t.getUser(), "id_vm", "vmName", "no link to vsphere");
		vm.saveOrUpdate();

		String pathToOVAtest = "vmware_compatible_light.ova";
		ClassLoader classLoader = getClass().getClassLoader();
		File savedFile = new File(classLoader.getResource(pathToOVAtest).getFile());

		DownloadLink link = new DownloadLink(savedFile.getAbsolutePath(), vm);
		assertTrue(link.saveOrUpdate());	

		HashMap<String, String> headers = new HashMap<>();//no headers
		HashMap<String, String> params = new HashMap<>();
		
		String requestFormatted = String.format("/api/export/%s", link.getExternalLink());
		HttpServletRequest request = TestUtils.createMockRequest("GET", requestFormatted, params, headers);
		
		StringWriter writer = new StringWriter();
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		
		StubServletOutputStream outStream = new StubServletOutputStream();
		when(response.getOutputStream()).thenReturn(outStream);
		
		assertFalse(outStream.hasBeenWritten());

		new ExportServlet().service(request, response);

		assertTrue(outStream.hasBeenWritten());
		System.out.println(outStream.baos.size());
	}
	
	/**
	 * class used to mock a ServletOutputStream
	 * @author Kalioz
	 *
	 */
	public class StubServletOutputStream extends ServletOutputStream {
		public ByteArrayOutputStream baos = new ByteArrayOutputStream();
		public void write(int i) throws IOException {
			baos.write(i);
		}
		@Override
		public boolean isReady() {return false;}
		@Override
		public void setWriteListener(WriteListener writeListener) {}
		
		public boolean hasBeenWritten(){
			return baos.size() != 0;
		}
	}

}
