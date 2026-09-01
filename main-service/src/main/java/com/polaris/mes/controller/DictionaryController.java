package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.service.DictionaryService;
import com.polaris.mes.common.RequestContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dictionaries")
public class DictionaryController {
    private final DictionaryService dictionaries;

    public DictionaryController(DictionaryService dictionaries) { this.dictionaries = dictionaries; }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listTypes(@RequestParam(required = false) String locale) { return ApiResponse.ok(dictionaries.types(locale)); }

    @GetMapping("/{type}")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable String type, @RequestParam(required = false) String locale, @RequestParam(defaultValue = "false") boolean includeDisabled) { return ApiResponse.ok(dictionaries.list(type, locale, includeDisabled)); }

    @PostMapping
    public ApiResponse<Map<String, Object>> save(@RequestBody Map<String, Object> payload) { return ApiResponse.ok(dictionaries.save(payload), "字典项已保存"); }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> payload) { RequestContext.requireRole("admin"); return ApiResponse.ok(dictionaries.update(id, payload), "字典项已更新"); }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) { RequestContext.requireRole("admin"); dictionaries.delete(id); return ApiResponse.ok(null, "字典项已删除"); }
}
