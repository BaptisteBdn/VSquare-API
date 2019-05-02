package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.model.DownloadLink;
import fr.eseo.vsquare.utils.Logger;
import fr.eseo.vsquare.utils.ServletUtils;
import org.apache.commons.compress.utils.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.logging.Level;

/**
 * Servlet implementation class ExportServlet.
 * 
 * @author Clément Loiselet
 */
@WebServlet(name = "export", urlPatterns = { "/export/*" })
@MultipartConfig
public class ExportServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public ExportServlet() {
		super();
	}

	/**
	 * service at /api/export/{}.
	 */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
			map.put("GET /api/export/{}", () -> export( request, response));
			ServletUtils.mapRequest(request, response, map);
		} catch (Exception e) {
			Logger.log(Level.SEVERE, e.toString(), e);
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Export an OVA file from Vsphere.
	 * 
	 * @param request the http servlet request
	 * @param response the http servlet response
	 */
	private void export(HttpServletRequest request, HttpServletResponse response) {
		String[] path = request.getRequestURI().split("/");
		String externalLink = path[path.length-1];

		DownloadLink link = DownloadLink.findByExternalLink(externalLink);

		if (link == null){
			ServletUtils.sendError(response, HttpServletResponse.SC_NOT_FOUND, "bad link");
			return;
		}

		if (link.isTimeoutExceeded()){
			ServletUtils.sendError(response, HttpServletResponse.SC_CONFLICT, "link too old - try regenrating another link");
			return;
		}
		
		link.increaseDownloadTry();
		link.saveOrUpdate();
		
		File file = link.getInternalLinkAsFile();

		if (! file.exists()){
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "internal file could not be found");
			Logger.log(Level.WARNING, "download file couldn't be found : id {0} , path to file = {1}", link.getId(), link.getInternalLink());
			return;
		}

		if (! file.canRead()){
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "internal file could not be read");
			Logger.log(Level.WARNING, "download file couldn't be read : id {0} , path to file = {1}", link.getId(), link.getInternalLink());
			return;
		}

		// gets MIME type of the file
		String mimeType = "application/vmware";//or application/octet-stream

		// modifies response
		response.setContentType(mimeType);
		response.setContentLength((int) file.length());

		// forces download
		String headerKey = "Content-Disposition";
		String headerValue = String.format("attachment; filename=\"%s\"", file.getName());
		response.setHeader(headerKey, headerValue);

		// obtains response's output stream
		ServletOutputStream outStream;
		try {
			outStream = response.getOutputStream();
		} catch (IOException e1) {
			Logger.log(Level.WARNING, "could not open outputStream");
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "file transfer error (0)");
			return;
		}
		
		response.setStatus(HttpServletResponse.SC_OK);//RAAAAAAAAH
		try(FileInputStream inStream = new FileInputStream(file)){
			IOUtils.copy(inStream, outStream);
			outStream.flush();
		} catch (IOException e) {
			Logger.log(Level.WARNING, "error when serving OVA package");
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "file transfer error");
			return;
		}
		link.increaseDownloadSuccess();
		link.saveOrUpdate();
		Logger.log(Level.INFO, "OVA file served to client");

	}

}
