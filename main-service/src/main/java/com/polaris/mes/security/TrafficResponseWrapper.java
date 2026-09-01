package com.polaris.mes.security;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Buffers a tenant response so its real body size can be charged before send. */
final class TrafficResponseWrapper extends HttpServletResponseWrapper {
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();
    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private int status = HttpServletResponse.SC_OK;

    TrafficResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    long bodyBytes() throws IOException {
        flushBuffer();
        return body.size();
    }

    void copyTo(HttpServletResponse response) throws IOException {
        flushBuffer();
        response.setStatus(status);
        if (response.getHeader("Content-Length") == null) response.setContentLengthLong(body.size());
        ServletOutputStream output = response.getOutputStream();
        body.writeTo(output);
        output.flush();
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (writer != null) throw new IllegalStateException("getWriter() has already been called");
        if (outputStream == null) {
            outputStream = new ServletOutputStream() {
                @Override public void write(int value) { body.write(value); }
                @Override public void write(byte[] buffer, int offset, int length) { body.write(buffer, offset, length); }
                @Override public boolean isReady() { return true; }
                @Override public void setWriteListener(WriteListener listener) { }
            };
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() {
        if (outputStream != null) throw new IllegalStateException("getOutputStream() has already been called");
        if (writer == null) {
            Charset charset;
            try { charset = Charset.forName(getCharacterEncoding()); }
            catch (Exception ignored) { charset = StandardCharsets.UTF_8; }
            writer = new PrintWriter(new OutputStreamWriter(body, charset));
        }
        return writer;
    }

    @Override public void flushBuffer() throws IOException {
        if (writer != null) writer.flush();
        if (outputStream != null) outputStream.flush();
    }
    @Override public void resetBuffer() { body.reset(); }
    @Override public void reset() { body.reset(); status = HttpServletResponse.SC_OK; }
    @Override public void setStatus(int status) { this.status = status; }
    @Override public int getStatus() { return status; }
    @Override public void sendError(int status) { this.status = status; }
    @Override public void sendError(int status, String message) { this.status = status; }
    @Override public void sendRedirect(String location) { this.status = HttpServletResponse.SC_FOUND; setHeader("Location", location); }
    @Override public boolean isCommitted() { return false; }
}
