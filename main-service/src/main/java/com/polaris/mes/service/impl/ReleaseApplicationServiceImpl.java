package com.polaris.mes.service.impl;

import com.polaris.mes.service.ReleaseApplicationService;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class ReleaseApplicationServiceImpl implements ReleaseApplicationService {
    private final com.polaris.mes.service.ReleaseService delegate;
    public ReleaseApplicationServiceImpl(com.polaris.mes.service.ReleaseService delegate) { this.delegate = delegate; }
    @Override public Map<String, Object> overview() { return delegate.overview(); }
    @Override public List<Map<String, Object>> list() { return delegate.list(); }
    @Override public Map<String, Object> detail(long id) { return delegate.detail(id); }
    @Override public Map<String, Object> generate(Map<String, Object> payload, String actor) { return delegate.generate(payload, actor); }
    @Override public Map<String, Object> verify(long id, Map<String, Object> payload, String actor) { return delegate.verify(id, payload, actor); }
    @Override public Map<String, Object> publish(long id, String actor) { return delegate.publish(id, actor); }
    @Override public Path packagePath(long id) { return delegate.packagePath(id); }
}
