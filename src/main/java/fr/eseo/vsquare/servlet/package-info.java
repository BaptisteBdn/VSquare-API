/**
 * This package store all the Servlets used by the tomcat server
 * <p>
 * Each Servlet should follow this pattern :
 * <p>
 * * Override service from HttpServlet
 * <p>
 * * try catch any errors to send a nice error with ServletUtils.sendError
 * <p>
 * * map requests to private functions with ServletUtils.mapRequest
 * <p>
 * (See UserServlet for a classic example)
 * <p>
 * This package also store the InitContextListener which listen the tomcat server startup and shutdown
 */
package fr.eseo.vsquare.servlet;