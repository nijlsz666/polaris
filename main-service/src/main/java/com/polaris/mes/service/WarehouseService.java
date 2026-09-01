package com.polaris.mes.service;

import java.util.List;
import java.util.Map;

/** Application boundary for WMS and PDA operations. Controllers depend on this contract only. */
public interface WarehouseService {
    Map<String, Object> summary();
    List<Map<String, Object>> listWarehouses();
    List<Map<String, Object>> listAreas(String warehouseCode);
    List<Map<String, Object>> listLocations(String warehouseCode);
    List<Map<String, Object>> listMaterials(String keyword);
    List<Map<String, Object>> listBatches(String materialCode, String status);
    List<Map<String, Object>> listInventory(String keyword, String warehouseCode, String stockStatus);
    List<Map<String, Object>> listTransactions(String keyword, String type, int limit);
    List<Map<String, Object>> listDocuments(String type, String status);
    List<Map<String, Object>> listDocumentLines(long documentId);
    List<Map<String, Object>> listCounts();
    List<Map<String, Object>> listCountLines(long countId);
    List<Map<String, Object>> listBarcodes(String keyword, String status);
    List<Map<String, Object>> listBarcodeRules();
    Map<String, Object> postTransaction(Map<String, Object> payload, String actor);
    Map<String, Object> changeLock(Map<String, Object> payload, String actor, boolean lock);
    Map<String, Object> changeReservation(Map<String, Object> payload, String actor, boolean reserve);
    Map<String, Object> changeStockStatus(Map<String, Object> payload, String actor, boolean freeze);
    Map<String, Object> createCount(Map<String, Object> payload, String actor);
    Map<String, Object> submitCount(long countId, Map<String, Object> payload, String actor);
    Map<String, Object> quickCount(Map<String, Object> payload, String actor);
    Map<String, Object> parseBarcode(String barcode);
    List<Map<String, Object>> trace(String materialCode, String batchNo, String barcode);
    Map<String, Object> createBarcode(Map<String, Object> payload);
    int printBarcodes(List<String> barcodes);
    void voidBarcode(String barcode);
    Map<String, Object> saveWarehouse(Map<String, Object> payload);
    Map<String, Object> saveLocation(Map<String, Object> payload);
    Map<String, Object> saveArea(Map<String, Object> payload);
    Map<String, Object> saveMaterial(Map<String, Object> payload);
    Map<String, Object> saveBarcodeRule(Map<String, Object> payload);
}
