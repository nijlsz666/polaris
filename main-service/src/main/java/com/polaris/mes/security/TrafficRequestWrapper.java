package com.polaris.mes.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Counts bytes actually read from an authenticated tenant request body. */
final class TrafficRequestWrapper extends HttpServletRequestWrapper {
    private long bytesRead;
    private ServletInputStream inputStream;

    TrafficRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    long bytesRead() {
        return bytesRead;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (inputStream == null) {
            ServletInputStream source = super.getInputStream();
            inputStream = new ServletInputStream() {
                @Override public int read() throws IOException {
                    int value = source.read();
                    if (value >= 0) bytesRead++;
                    return value;
                }
                @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                    int count = source.read(buffer, offset, length);
                    if (count > 0) bytesRead += count;
                    return count;
                }
                @Override public boolean isFinished() { return source.isFinished(); }
                @Override public boolean isReady() { return source.isReady(); }
                @Override public void setReadListener(ReadListener listener) { source.setReadListener(listener); }
            };
        }
        return inputStream;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        String encoding = getCharacterEncoding();
        return new BufferedReader(new InputStreamReader(getInputStream(), encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
    }
}
