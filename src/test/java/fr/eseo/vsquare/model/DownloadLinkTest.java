package fr.eseo.vsquare.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.utils.Utils;

public class DownloadLinkTest {
	
	private DownloadLink link;
	private Vm vm;
	private User user;
	
	@Before
	public void beforeEach() throws Exception{
		TestUtils.initTest(true);
		
		user = User.findByLogin("test");
		if (user == null){
			user = new User("test", "test test");
			user.saveOrUpdate();
		}
		
		vm = new Vm(user, "vm-000", "testVM", "descTest");
		vm.saveOrUpdate();
		
		
		link = new DownloadLink("*/api/download/link","/path/to/file", vm);
		assertTrue(link.saveOrUpdate());
	}
	
	@Test
	public void testHashCode() {
		assertTrue(link.hashCode() != 0);
	}
	
	@Test
	public void testConstructor(){
		DownloadLink link = new DownloadLink("/path/to/file", vm);
		assertNotNull(link.getExternalLink());
		assertTrue(link.saveOrUpdate());
		System.out.println("esternal link>"+link.getExternalLink());
	}

	@Test
	public void testGetDownloadTry() {
		assertEquals(0, link.getDownloadTry());		
	}

	@Test
	public void testSetDownloadTry() {
		assertEquals(0, link.getDownloadTry());
		link.setDownloadTry(5);
		assertEquals(5, link.getDownloadTry());		
	}

	@Test
	public void testGetDownloadSuccess() {
		assertEquals(0, link.getDownloadSuccess());		
	}

	@Test
	public void testSetDownloadSuccess() {
		assertEquals(0, link.getDownloadSuccess());	
		link.setDownloadSuccess(8);
		assertEquals(8, link.getDownloadSuccess());	
	}

	@Test
	public void testGetExternalLink() {
		assertEquals("*/api/download/link", link.getExternalLink());	
	}

	@Test
	public void testSetExternalLink() {
		assertEquals("*/api/download/link", link.getExternalLink());
		link.setExternalLink("*/api/download/link58");
		assertEquals("*/api/download/link58", link.getExternalLink());
	}

	@Test
	public void testGetInternalLink() {
		assertEquals("/path/to/file", link.getInternalLink());
	}

	@Test
	public void testSetInternalLink() {
		assertEquals("/path/to/file", link.getInternalLink());
		link.setInternalLink("/path/to/file/2");
		assertEquals("/path/to/file/2", link.getInternalLink());
	}

	@Test
	public void testGetVm() {
		assertEquals(vm, link.getVm());
	}

	@Test
	public void testIncreaseDownloadTry() {
		assertEquals(0, link.getDownloadTry());
		link.increaseDownloadTry();
		assertEquals(1, link.getDownloadTry());
	}

	@Test
	public void testIncreaseDownloadSuccess() {
		assertEquals(0, link.getDownloadSuccess());
		link.increaseDownloadSuccess();
		assertEquals(1, link.getDownloadSuccess());
	}
	
	@Test
	public void testFindByExternalLink(){
		assertNotNull(DownloadLink.findByExternalLink(link.getExternalLink()));
	}

	@Test
	public void testFindByInternalLink(){
		assertNotNull(DownloadLink.findByInternalLink(link.getInternalLink()));
	}
	
	@Test
	public void testDateExceededFalse(){
		assertFalse(link.isTimeoutExceeded());
	}
	
	@Test
	public void testDateExceededTrue() throws Exception{
		Field f = VSquareObject.class.getDeclaredField("creationDate");
	    f.setAccessible(true);
	    Date date = new Date();
	    date.setTime((new Date()).getTime() - Utils.getInt("timeout_link")*60*1000-5*60*1000);
	    f.set(link, date);
		assertTrue(link.isTimeoutExceeded());
		
		date.setTime((new Date()).getTime() - Utils.getInt("timeout_link")*60*1000+5*60*1000);
	    f.set(link, date);
		assertFalse(link.isTimeoutExceeded());
	}
	
	@Test
	public void testFindByVm(){
		assertNotNull(DownloadLink.findByVm(vm));
	}
}
