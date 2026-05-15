package com.realtimebus.web;

import android.content.Context;
import android.content.res.AssetManager;
import fi.iki.elonen.NanoHTTPD;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class IbusLocalServer extends NanoHTTPD {
    private static final String ASSET_ROOT = "site";
    private static final String PROXY_PREFIX = "/__ibus_proxy__/";
    private static final Set<String> ALLOWED_HOSTS = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(
            "app.ibuscloud.com",
            "apptest.ibuscloud.com",
            "apph5.ibuscloud.com",
            "apph5-test.ibuscloud.com"
        ))
    );
    private static final Set<String> HOP_BY_HOP_HEADERS = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "transfer-encoding",
            "upgrade",
            "host"
        ))
    );

    private final AssetManager assetManager;

    IbusLocalServer(Context context, int port) {
        super("127.0.0.1", port);
        this.assetManager = context.getAssets();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String path = session.getUri();
        if (path == null || path.isEmpty() || "/".equals(path)
            || "/实时公交网页".equals(path)
            || "/实时公交网页/".equals(path)
            || "/ibuscloud_realtime".equals(path)
            || "/ibuscloud_realtime/".equals(path)
            || "/ibuscloud_realtime/index.html".equals(path)) {
            return redirectToApp(session.getQueryParameterString());
        }
        if (path.startsWith(PROXY_PREFIX)) {
            return proxyRequest(session);
        }
        return serveAsset(path);
    }

    private Response redirectToApp(String query) {
        String location = "/index.html";
        if (query != null && !query.isEmpty()) {
            location += "?" + query;
        }
        Response response = newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, "");
        response.addHeader("Location", location);
        return response;
    }

    private Response serveAsset(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (normalized.isEmpty()) {
            normalized = "index.html";
        }
        try {
            InputStream stream = assetManager.open(ASSET_ROOT + "/" + normalized);
            String mimeType = guessMimeType(normalized);
            return newChunkedResponse(Response.Status.OK, mimeType, stream);
        } catch (IOException notFound) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not found");
        }
    }

    private Response proxyRequest(IHTTPSession session) {
        String path = session.getUri();
        String remote = path.substring(PROXY_PREFIX.length());
        int slash = remote.indexOf('/');
        String host = slash >= 0 ? remote.substring(0, slash) : remote;
        String remotePath = slash >= 0 ? remote.substring(slash + 1) : "";
        if (!ALLOWED_HOSTS.contains(host)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Proxy host not allowed");
        }

        String target = "https://" + host + "/" + remotePath;
        String query = session.getQueryParameterString();
        if (query != null && !query.isEmpty()) {
            target += "?" + query;
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
            connection.setRequestMethod(session.getMethod().name());
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);

            for (Map.Entry<String, String> entry : session.getHeaders().entrySet()) {
                String headerName = entry.getKey();
                if (headerName == null) {
                    continue;
                }
                String lower = headerName.toLowerCase(Locale.ROOT);
                if (HOP_BY_HOP_HEADERS.contains(lower)) {
                    continue;
                }
                connection.setRequestProperty(headerName, entry.getValue());
            }
            if (connection.getRequestProperty("User-Agent") == null) {
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 Android WebView Proxy");
            }

            byte[] body = readRequestBody(session);
            if (body.length > 0) {
                connection.setDoOutput(true);
                connection.getOutputStream().write(body);
            }

            int statusCode = connection.getResponseCode();
            InputStream responseStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (responseStream == null) {
                responseStream = new ByteArrayInputStream(new byte[0]);
            }

            Response response = newChunkedResponse(status(statusCode), contentType(connection), responseStream);
            Map<String, List<String>> headerFields = connection.getHeaderFields();
            for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
                String headerName = entry.getKey();
                if (headerName == null) {
                    continue;
                }
                String lower = headerName.toLowerCase(Locale.ROOT);
                if (HOP_BY_HOP_HEADERS.contains(lower)) {
                    continue;
                }
                for (String value : entry.getValue()) {
                    response.addHeader(headerName, value);
                }
            }
            return response;
        } catch (Exception exception) {
            return newFixedLengthResponse(
                new SimpleStatus(502, "Bad Gateway"),
                NanoHTTPD.MIME_PLAINTEXT,
                "Proxy error: " + exception.getMessage()
            );
        }
    }

    private static byte[] readRequestBody(IHTTPSession session) throws IOException, ResponseException {
        Method method = session.getMethod();
        if (method != Method.POST && method != Method.PUT && method != Method.PATCH) {
            return new byte[0];
        }
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String content = files.get("postData");
        if (content != null) {
            return content.getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    private static String guessMimeType(String path) {
        String mimeType = URLConnection.guessContentTypeFromName(path);
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private static String contentType(URLConnection connection) {
        String contentType = connection.getContentType();
        return contentType != null ? contentType : "application/octet-stream";
    }

    private static Response.IStatus status(int code) {
        for (Response.Status status : Response.Status.values()) {
            if (status.getRequestStatus() == code) {
                return status;
            }
        }
        return new SimpleStatus(code, "");
    }

    private static final class SimpleStatus implements Response.IStatus {
        private final int requestStatus;
        private final String description;

        private SimpleStatus(int requestStatus, String description) {
            this.requestStatus = requestStatus;
            this.description = description;
        }

        @Override
        public int getRequestStatus() {
            return requestStatus;
        }

        @Override
        public String getDescription() {
            return requestStatus + " " + description;
        }
    }
}
