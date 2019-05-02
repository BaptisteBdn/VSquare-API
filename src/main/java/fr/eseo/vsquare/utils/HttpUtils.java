package fr.eseo.vsquare.utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Level;

/**
 * A class containing utils for http requests.
 *
 * @author Clement Gouin
 */
public final class HttpUtils {

    private static final String ENCODING = "UTF-8";

    private static final List<String> SUPPORTED_METHODS = new ArrayList<>(Arrays.asList("GET", "PUT", "POST", "DELETE"));

    private HttpUtils() {
    }

    /**
     * Execute an http/https request.
     *
     * @param sMethod the http method
     * @param sUrl    the url to reach
     * @return the results of the request
     */
    public static HttpResult executeRequest(String sMethod, String sUrl) {
        return executeRequest(sMethod, sUrl, null, null, null);
    }

    /**
     * Execute an http/https request.
     *
     * @param sMethod the http method
     * @param sUrl    the url to reach
     * @param params  the url parameters (or null if not needed)
     * @return the results of the request
     */
    public static HttpResult executeRequest(String sMethod, String sUrl, Map<String, String[]> params) {
        return executeRequest(sMethod, sUrl, params, null, null);
    }

    /**
     * Execute an http/https request.
     *
     * @param sMethod the http method
     * @param sUrl    the url to reach
     * @param data    the json data of the request
     * @return the results of the request
     */
    public static HttpResult executeRequest(String sMethod, String sUrl, JSONObject data) {
        return executeRequest(sMethod, sUrl, null, null, data);
    }

    /**
     * Execute an http/https request.
     *
     * @param sMethod the http method
     * @param sUrl    the url to reach
     * @param params  the url parameters (or null if not needed)
     * @param headers additional headers for the request (or null if not needed)
     * @return the results of the request
     */
    public static HttpResult executeRequest(String sMethod, String sUrl, Map<String, String[]> params,
                                            Map<String, String> headers) {
        return executeRequest(sMethod, sUrl, params, headers, null);
    }

    /**
     * Execute an http/https request.
     *
     * @param sMethod the http method
     * @param sUrl    the url to reach
     * @param params  the url parameters (or null if not needed)
     * @param data    the json data of the request
     * @return the results of the request
     */
    public static HttpResult executeRequest(String sMethod, String sUrl, Map<String, String[]> params,
                                            JSONObject data) {
        return executeRequest(sMethod, sUrl, params, null, data);
    }

    /**
     * Execute an http/https request.
     *
     * @param sMethod the http method
     * @param sUrl    the url to reach
     * @param params  the url parameters (or null if not needed)
     * @param headers additional headers for the request (or null if not needed)
     * @param data    the json data of the request
     * @return the results of the request
     */
    public static HttpResult executeRequest(String sMethod, String sUrl, Map<String, String[]> params,
                                            Map<String, String> headers, JSONObject data) {
        StringBuilder result = new StringBuilder();
        int responseCode = 0;
        Map<String, List<String>> responseHeaders = new HashMap<>(0);
        URL url;
        HttpURLConnection conn = null;
        try {
            Logger.log(Level.INFO, "{0} request to {1}{2}", sMethod, sUrl, getParametersString(params));

            url = new URL(sUrl + getParametersString(params));

            conn = (HttpURLConnection) url.openConnection();

            if (SUPPORTED_METHODS.contains(sMethod))
                conn.setRequestMethod(sMethod);
            else {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-HTTP-Method-Override", sMethod);
            }
            conn.setReadTimeout(Utils.getInt("http_request_timeout"));
            conn.setConnectTimeout(Utils.getInt("http_request_timeout"));
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Encoding", ENCODING);

            if (headers != null)
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    Logger.log(Level.FINE, "\theader {0} : {1}", entry.getKey(), entry.getValue());
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }

            if (data != null) {
                Logger.log(Level.FINE, "\tdata : {0}", data.toString());
                conn.setRequestProperty("Content-Type", "application/json");
                byte[] bData = data.toString().getBytes();
                conn.setRequestProperty("Content-Length", String.valueOf(bData.length));
                conn.setDoOutput(true);

                try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                    wr.write(bData);
                }
            }

            responseCode = conn.getResponseCode();

            if (responseCode == 301 || responseCode == 302) {
                String newURL = conn.getHeaderField("Location");
                Logger.log(Level.INFO, "Redirected to {0}", newURL);
                return executeRequest(sMethod, newURL, params, headers);
            }

            readInputStream(result, conn.getInputStream());
            responseHeaders = conn.getHeaderFields();
        } catch (IOException e) {
            Logger.log(Level.SEVERE, e.toString());
            if (conn != null && responseCode >= 200)
                try {
                    readInputStream(result, conn.getErrorStream());
                } catch (IOException e1) {
                    Logger.log(Level.WARNING, e.toString());
                }
        }
        Logger.log(Level.INFO, "response : {0}", result.toString().replace("\n", ""));
        return new HttpResult(responseCode, result.toString(), responseHeaders);
    }

    public static boolean executePutRequest(String sUrl,
                                            Map<String, String> headers, String filePath) {
        URL url;
        HttpURLConnection conn;
        try {
            Logger.log(Level.INFO, "PUT request to {0} with file {1}", sUrl, filePath);

            url = new URL(sUrl);

            conn = (HttpURLConnection) url.openConnection();

            conn.setDoInput(true);
            conn.setAllowUserInteraction(true);
            conn.setDoOutput(true);

            if (headers != null)
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    Logger.log(Level.FINE, "\theader {0} : {1}", entry.getKey(), entry.getValue());
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }

            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Encoding", ENCODING);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Content-Length", "1024");

            long fileLen = new File(filePath).length();
            conn.setChunkedStreamingMode((int) fileLen);
            int code = -1;
            try (OutputStream out = conn.getOutputStream();
            		InputStream in = new BufferedInputStream(new FileInputStream(filePath))) {
                    int bufLen = 9 * 1024;
                    byte[] buf = new byte[bufLen];
                    byte[] tmp;
                    int len;
                    while ((len = in.read(buf, 0, bufLen)) != -1) {
                        tmp = new byte[len];
                        System.arraycopy(buf, 0, tmp, 0, len);
                        out.write(tmp, 0, len);
                    }
                    
                    code = conn.getResponseCode();
                    out.flush();
                }
            
            Logger.log(code == 200 || code == 201 ? Level.INFO : Level.WARNING, "Response {0} : {1}", code, conn.getResponseMessage());
            conn.disconnect();
            return code == 200 || code == 201;
        } catch (IOException e) {
            Logger.log(Level.SEVERE, e.toString(), e);
            return false;
        }
    }

    /**
     * Read an InputStream into a StringBuilder.
     *
     * @param sb the StringBuilder to use
     * @param is the InputStream to read
     * @throws IOException exception
     */
    private static void readInputStream(StringBuilder sb, InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            for (int c; (c = br.read()) >= 0; )
                sb.append((char) c);
        }
    }

    /**
     * Write the parameters into a url encoded format.
     *
     * @param params the params to pass to the request
     * @return the url format of the parameters
     */
    public static String getParametersString(Map<String, String[]> params) {
        if (params == null)
            return "";
        StringBuilder result = new StringBuilder();
        boolean first = true;
        try {
            for (Map.Entry<String, String[]> entry : params.entrySet()) {
                for (String p : entry.getValue()) {
                    if (first)
                        first = false;
                    else
                        result.append("&");

                    result.append(URLEncoder.encode(entry.getKey(), ENCODING));

                    result.append("=");
                    result.append(URLEncoder.encode(p, ENCODING));
                }
            }
        } catch (UnsupportedEncodingException e) {
            Logger.log(Level.WARNING, e.toString());
        }
        return "?" + result.toString();
    }

    /**
     * A class containing the simple results of a http request.
     */
    public static class HttpResult {

        public final int code;
        public final String result;
        public final Map<String, List<String>> headers;
        private final long timeMillis;
        private JSONObject json = null;

        public HttpResult(int code, String result, Map<String, List<String>> headers) {
            super();
            this.code = code;
            this.result = result;
            this.headers = headers;
            this.timeMillis = System.currentTimeMillis();
        }

        /**
         * @return the result of the request parsed as JSON
         */
        public JSONObject getJSON() {
            if (json == null)
                try {
                    json = new JSONObject(result);
                } catch (JSONException e) {
                    Logger.log(Level.WARNING, "Cannot parse JSON : {0}", result);
                    json = new JSONObject();
                }
            return json;
        }

        /**
         * @return the age of this result
         */
        public long getAge() {
            return System.currentTimeMillis() - timeMillis;
        }
    }

    /**
     * Encode a string into base64.
     *
     * @param source the source string
     * @return the converted string
     */
    private static String encodeBase64String(String source) {
        try {
            return new String(Base64.getEncoder().encode(source.getBytes(ENCODING)));
        } catch (UnsupportedEncodingException e) {
            return new String(Base64.getEncoder().encode(source.getBytes()));
        }
    }

    /**
     * Get the basic authentication header.
     *
     * @param username the username
     * @param password the password
     * @return a hashmap containing the basic authentication header
     */
    public static Map<String, String> getBasicAuthHeaders(String username, String password) {
        HashMap<String, String> headers = new HashMap<>();
        String value = HttpUtils.encodeBase64String(username + ":" + password);
        headers.put("Authorization", String.format("Basic %s", value));
        return headers;
    }
}
