package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.service.WarehouseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/warehouse")
public class WarehouseController {
    private final WarehouseService warehouse;

    public WarehouseController(WarehouseService warehouse) {
        this.warehouse = warehouse;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() { return ApiResponse.ok(warehouse.summary()); }

    @GetMapping("/warehouses")
    public ApiResponse<List<Map<String, Object>>> warehouses() { return ApiResponse.ok(warehouse.listWarehouses()); }

    @GetMapping("/areas")
    public ApiResponse<List<Map<String, Object>>> areas(@RequestParam(required = false) String warehouseCode) { return ApiResponse.ok(warehouse.listAreas(warehouseCode)); }

    @GetMapping("/locations")
    public ApiResponse<List<Map<String, Object>>> locations(@RequestParam(required = false) String warehouseCode) { return ApiResponse.ok(warehouse.listLocations(warehouseCode)); }

    @GetMapping("/materials")
    public ApiResponse<List<Map<String, Object>>> materials(@RequestParam(required = false) String keyword) { return ApiResponse.ok(warehouse.listMaterials(keyword)); }

    @GetMapping("/batches")
    public ApiResponse<List<Map<String, Object>>> batches(@RequestParam(required = false) String materialCode, @RequestParam(required = false) String status) { return ApiResponse.ok(warehouse.listBatches(materialCode, status)); }

    @GetMapping("/inventory")
    public ApiResponse<List<Map<String, Object>>> inventory(@RequestParam(required = false) String keyword, @RequestParam(required = false) String warehouseCode, @RequestParam(required = false) String stockStatus) {
        return ApiResponse.ok(warehouse.listInventory(keyword, warehouseCode, stockStatus));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<Map<String, Object>>> transactions(@RequestParam(required = false) String keyword, @RequestParam(required = false) String type, @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(warehouse.listTransactions(keyword, type, limit));
    }

    @PostMapping("/transactions")
    public ApiResponse<Map<String, Object>> transaction(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return ApiResponse.ok(warehouse.postTransaction(payload, RequestContext.actor(request)), "库存事务已提交");
    }

    @PostMapping("/lock")
    public ApiResponse<Map<String, Object>> lock(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return ApiResponse.ok(warehouse.changeLock(payload, RequestContext.actor(request), true), "库存已锁定");
    }

    @PostMapping("/unlock")
    public ApiResponse<Map<String, Object>> unlock(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return ApiResponse.ok(warehouse.changeLock(payload, RequestContext.actor(request), false), "库存已解锁");
    }

    @PostMapping("/reserve")
    public ApiResponse<Map<String, Object>> reserve(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return ApiResponse.ok(warehouse.changeReservation(payload, RequestContext.actor(request), true), "库存已预留");
    }

    @PostMapping("/release")
    public ApiResponse<Map<String, Object>> release(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return ApiResponse.ok(warehouse.changeReservation(payload, RequestContext.actor(request), false), "库存预留已释放");
    }

    @PostMapping("/freeze")
    public ApiResponse<Map<String, Object>> freeze(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return ApiResponse.ok(warehouse.changeStockStatus(payload, RequestContext.actor(request), true), "批次库存已冻结");
    }

    @PostMapping("/unfreeze")
    public ApiResponse<Map<String, Object>> unfreeze(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return ApiResponse.ok(warehouse.changeStockStatus(payload, RequestContext.actor(request), false), "批次库存已解冻");
    }

    @GetMapping("/documents")
    public ApiResponse<List<Map<String, Object>>> documents(@RequestParam(required = false) String type, @RequestParam(required = false) String status) {
        return ApiResponse.ok(warehouse.listDocuments(type, status));
    }

    @GetMapping("/documents/{id}/lines")
    public ApiResponse<List<Map<String, Object>>> documentLines(@PathVariable long id) {
        return ApiResponse.ok(warehouse.listDocumentLines(id));
    }

    @GetMapping("/counts")
    public ApiResponse<List<Map<String, Object>>> counts() { return ApiResponse.ok(warehouse.listCounts()); }

    @GetMapping("/counts/{id}/lines")
    public ApiResponse<List<Map<String, Object>>> countLines(@PathVariable long id) { return ApiResponse.ok(warehouse.listCountLines(id)); }

    @PostMapping("/counts")
    public ApiResponse<Map<String, Object>> createCount(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return ApiResponse.ok(warehouse.createCount(payload, RequestContext.actor(request)), "盘点单已创建");
    }

    @PostMapping("/counts/{id}/submit")
    public ApiResponse<Map<String, Object>> submitCount(@PathVariable long id, @RequestBody(required = false) Map<String, Object> payload, HttpServletRequest request) {
        return ApiResponse.ok(warehouse.submitCount(id, payload == null ? Map.of() : payload, RequestContext.actor(request)), "盘点已提交");
    }

    @PostMapping("/counts/quick")
    public ApiResponse<Map<String, Object>> quickCount(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return ApiResponse.ok(warehouse.quickCount(payload, RequestContext.actor(request)), "扫码盘点已提交");
    }

    @GetMapping("/barcodes")
    public ApiResponse<List<Map<String, Object>>> barcodes(@RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return ApiResponse.ok(warehouse.listBarcodes(keyword, status));
    }

    @PostMapping("/barcodes")
    public ApiResponse<Map<String, Object>> createBarcode(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(warehouse.createBarcode(payload), "条码已生成");
    }

    @PostMapping("/barcodes/print")
    public ApiResponse<Map<String, Object>> printBarcodes(@RequestBody Map<String, Object> payload) {
        Object value = payload.get("barcodes");
        List<String> barcodes = value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of(String.valueOf(payload.getOrDefault("barcode", "")));
        return ApiResponse.ok(Map.of("printed", warehouse.printBarcodes(barcodes)), "条码打印记录已更新");
    }

    @PostMapping("/barcodes/{barcode}/void")
    public ApiResponse<Void> voidBarcode(@PathVariable String barcode) {
        warehouse.voidBarcode(barcode);
        return ApiResponse.ok(null, "条码已作废");
    }

    @GetMapping("/barcodes/parse")
    public ApiResponse<Map<String, Object>> parseBarcode(@RequestParam String barcode) { return ApiResponse.ok(warehouse.parseBarcode(barcode)); }

    @GetMapping("/barcode-rules")
    public ApiResponse<List<Map<String, Object>>> barcodeRules() { return ApiResponse.ok(warehouse.listBarcodeRules()); }

    @GetMapping("/trace")
    public ApiResponse<List<Map<String, Object>>> trace(@RequestParam(required = false) String materialCode, @RequestParam(required = false) String batchNo, @RequestParam(required = false) String barcode) {
        return ApiResponse.ok(warehouse.trace(materialCode, batchNo, barcode));
    }

    @PostMapping("/masters/warehouses")
    public ApiResponse<Map<String, Object>> saveWarehouse(@RequestBody Map<String, Object> payload) { return ApiResponse.ok(warehouse.saveWarehouse(payload), "仓库主数据已保存"); }

    @PostMapping("/masters/locations")
    public ApiResponse<Map<String, Object>> saveLocation(@RequestBody Map<String, Object> payload) { return ApiResponse.ok(warehouse.saveLocation(payload), "库位主数据已保存"); }

    @PostMapping("/masters/areas")
    public ApiResponse<Map<String, Object>> saveArea(@RequestBody Map<String, Object> payload) { return ApiResponse.ok(warehouse.saveArea(payload), "库区主数据已保存"); }

    @PostMapping("/masters/materials")
    public ApiResponse<Map<String, Object>> saveMaterial(@RequestBody Map<String, Object> payload) { return ApiResponse.ok(warehouse.saveMaterial(payload), "物料主数据已保存"); }

    @PostMapping("/masters/barcode-rules")
    public ApiResponse<Map<String, Object>> saveBarcodeRule(@RequestBody Map<String, Object> payload) { return ApiResponse.ok(warehouse.saveBarcodeRule(payload), "条码规则已保存"); }
}
