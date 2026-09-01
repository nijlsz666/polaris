package com.polaris.mes.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.polaris.mes.annotation.AuditOperation;
import com.polaris.mes.annotation.Idempotent;
import com.polaris.mes.annotation.RequireRole;
import com.polaris.mes.common.TenantContext;
import com.polaris.mes.mapper.BarcodeMapper;
import com.polaris.mes.mapper.InventoryMapper;
import com.polaris.mes.mapper.MaterialTransactionMapper;
import com.polaris.mes.mapper.WhBatchMapper;
import com.polaris.mes.mapper.WhDocumentMapper;
import com.polaris.mes.mapper.WhDocumentLineMapper;
import com.polaris.mes.mapper.WhLocationMapper;
import com.polaris.mes.mapper.WhMaterialMapper;
import com.polaris.mes.mapper.WhStorageAreaMapper;
import com.polaris.mes.mapper.WhWarehouseMapper;
import com.polaris.mes.model.entity.Barcode;
import com.polaris.mes.model.entity.Inventory;
import com.polaris.mes.model.entity.MaterialTransaction;
import com.polaris.mes.model.entity.WhBatch;
import com.polaris.mes.model.entity.WhDocument;
import com.polaris.mes.model.entity.WhDocumentLine;
import com.polaris.mes.model.entity.WhLocation;
import com.polaris.mes.model.entity.WhMaterial;
import com.polaris.mes.model.entity.WhStorageArea;
import com.polaris.mes.model.entity.WhWarehouse;
import com.polaris.mes.service.WarehouseService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * WMS application service. The read/master-data path uses MyBatis-Plus entities
 * and mappers; compatibility SQL for older installations stays private to this
 * application service while schemas are upgraded incrementally.
 */
@Service
public class WarehouseServiceImpl implements WarehouseService {
    private static final Set<String> TRANSACTION_TYPES = Set.of("RECEIPT", "PUTAWAY", "ISSUE", "RETURN", "FINISHED_RECEIPT", "SALES_OUTBOUND", "TRANSFER_OUT", "TRANSFER_IN", "MOVE", "SCRAP", "ADJUSTMENT");
    private static final Set<String> OUTBOUND_TYPES = Set.of("ISSUE", "SALES_OUTBOUND", "TRANSFER_OUT", "SCRAP");
    private static final Set<String> COMPAT_TRANSACTION_TYPES = Set.of(
            "RECEIPT", "PUTAWAY", "ISSUE", "RETURN", "FINISHED_RECEIPT",
            "SALES_OUTBOUND", "TRANSFER_OUT", "TRANSFER_IN", "MOVE", "SCRAP", "ADJUSTMENT");
    private static final Set<String> COMPAT_OUTBOUND_TYPES = Set.of(
            "ISSUE", "SALES_OUTBOUND", "TRANSFER_OUT", "SCRAP");
    private final JdbcTemplate jdbc;
    private final WhWarehouseMapper warehouseMapper;
    private final WhStorageAreaMapper areaMapper;
    private final WhLocationMapper locationMapper;
    private final WhMaterialMapper materialMapper;
    private final WhBatchMapper batchMapper;
    private final InventoryMapper inventoryMapper;
    private final MaterialTransactionMapper transactionMapper;
    private final BarcodeMapper barcodeMapper;
    private final WhDocumentMapper documentMapper;
    private final WhDocumentLineMapper documentLineMapper;

    public WarehouseServiceImpl(JdbcTemplate jdbc,
                                WhWarehouseMapper warehouseMapper,
                                WhStorageAreaMapper areaMapper,
                                WhLocationMapper locationMapper,
                                WhMaterialMapper materialMapper,
                                WhBatchMapper batchMapper,
                                InventoryMapper inventoryMapper,
                                MaterialTransactionMapper transactionMapper,
                                BarcodeMapper barcodeMapper,
                                WhDocumentMapper documentMapper,
                                WhDocumentLineMapper documentLineMapper) {
        this.jdbc = jdbc;
        this.warehouseMapper = warehouseMapper;
        this.areaMapper = areaMapper;
        this.locationMapper = locationMapper;
        this.materialMapper = materialMapper;
        this.batchMapper = batchMapper;
        this.inventoryMapper = inventoryMapper;
        this.transactionMapper = transactionMapper;
        this.barcodeMapper = barcodeMapper;
        this.documentMapper = documentMapper;
        this.documentLineMapper = documentLineMapper;
    }

    @Override
    public Map<String, Object> summary() {
        Map<String, Object> result;
        try {
            result = new LinkedHashMap<>(compatSummary());
        } catch (DataAccessException ex) {
            result = new LinkedHashMap<>();
        }
        try {
            List<Inventory> rows = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>().eq(Inventory::getTenantId, tenantId()));
            result.put("inventorySkus", rows.stream().map(Inventory::getMaterialCode).distinct().count());
            result.put("inventoryQty", rows.stream().mapToInt(row -> value(row.getAvailableQty())).sum());
            result.put("lockedQty", rows.stream().mapToInt(row -> value(row.getLockedQty())).sum());
            result.put("reservedQty", rows.stream().mapToInt(row -> value(row.getReservedQty())).sum());
            result.put("inTransitQty", rows.stream().mapToInt(row -> value(row.getInTransitQty())).sum());
            result.put("lowStock", rows.stream().filter(row -> value(row.getAvailableQty()) < value(row.getSafetyStock())).count());
            result.put("frozenStock", rows.stream().filter(row -> "FROZEN".equals(row.getStockStatus())).count());
            return result;
        } catch (DataAccessException ex) {
            List<Map<String, Object>> rows = compatListInventory(null, null, null);
            result.put("inventorySkus", rows.stream().map(row -> text(row.get("material_code"))).distinct().count());
            result.put("inventoryQty", rows.stream().mapToInt(row -> number(row.get("available_qty"), 0)).sum());
            result.put("lockedQty", rows.stream().mapToInt(row -> number(row.get("locked_qty"), 0)).sum());
            result.putIfAbsent("reservedQty", 0);
            result.putIfAbsent("inTransitQty", 0);
            result.put("lowStock", rows.stream().filter(row -> number(row.get("available_qty"), 0) < number(row.get("safety_stock"), 0)).count());
            result.putIfAbsent("frozenStock", 0);
            result.putIfAbsent("expiringBatches", 0);
            result.putIfAbsent("openCounts", 0);
            result.putIfAbsent("todayTransactions", 0);
            result.putIfAbsent("transactionTrend", List.of());
            return result;
        }
    }

    @Override
    public List<Map<String, Object>> listWarehouses() {
        try {
            return warehouseMapper.selectList(new LambdaQueryWrapper<WhWarehouse>()
                            .eq(WhWarehouse::getTenantId, tenantId()).orderByAsc(WhWarehouse::getWarehouseCode))
                    .stream().map(this::warehouseMap).toList();
        } catch (DataAccessException ex) { return compatListWarehouses(); }
    }

    @Override
    public List<Map<String, Object>> listAreas(String warehouseCode) {
        LambdaQueryWrapper<WhStorageArea> query = new LambdaQueryWrapper<WhStorageArea>()
                .eq(WhStorageArea::getTenantId, tenantId()).orderByAsc(WhStorageArea::getWarehouseCode).orderByAsc(WhStorageArea::getAreaCode);
        if (notBlank(warehouseCode)) query.eq(WhStorageArea::getWarehouseCode, warehouseCode);
        try { return areaMapper.selectList(query).stream().map(this::areaMap).toList(); }
        catch (DataAccessException ex) { return compatListAreas(warehouseCode); }
    }

    @Override
    public List<Map<String, Object>> listLocations(String warehouseCode) {
        LambdaQueryWrapper<WhLocation> query = new LambdaQueryWrapper<WhLocation>()
                .eq(WhLocation::getTenantId, tenantId()).orderByAsc(WhLocation::getWarehouseCode).orderByAsc(WhLocation::getLocationCode);
        if (notBlank(warehouseCode)) query.eq(WhLocation::getWarehouseCode, warehouseCode);
        try { return locationMapper.selectList(query).stream().map(this::locationMap).toList(); }
        catch (DataAccessException ex) { return compatListLocations(warehouseCode); }
    }

    @Override
    public List<Map<String, Object>> listMaterials(String keyword) {
        LambdaQueryWrapper<WhMaterial> query = new LambdaQueryWrapper<WhMaterial>()
                .eq(WhMaterial::getTenantId, tenantId()).orderByAsc(WhMaterial::getMaterialCode);
        if (notBlank(keyword)) query.and(q -> q.like(WhMaterial::getMaterialCode, keyword.trim()).or().like(WhMaterial::getMaterialName, keyword.trim()));
        try { return materialMapper.selectList(query).stream().map(this::materialMap).toList(); }
        catch (DataAccessException ex) { return compatListMaterials(keyword); }
    }

    @Override
    public List<Map<String, Object>> listBatches(String materialCode, String status) {
        LambdaQueryWrapper<WhBatch> query = new LambdaQueryWrapper<WhBatch>()
                .eq(WhBatch::getTenantId, tenantId()).orderByAsc(WhBatch::getExpiryDate).orderByAsc(WhBatch::getBatchNo);
        if (notBlank(materialCode)) query.eq(WhBatch::getMaterialCode, materialCode);
        if (notBlank(status)) query.eq(WhBatch::getBatchStatus, status);
        try { return batchMapper.selectList(query).stream().map(this::batchMap).toList(); }
        catch (DataAccessException ex) { return compatListBatches(materialCode, status); }
    }

    @Override
    public List<Map<String, Object>> listInventory(String keyword, String warehouseCode, String stockStatus) {
        LambdaQueryWrapper<Inventory> query = new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getTenantId, tenantId())
                .orderByAsc(Inventory::getMaterialCode).orderByAsc(Inventory::getWarehouseCode)
                .orderByAsc(Inventory::getLocationCode).orderByAsc(Inventory::getBatchNo);
        if (notBlank(keyword)) {
            query.and(q -> q.like(Inventory::getMaterialCode, keyword.trim())
                    .or().like(Inventory::getMaterialName, keyword.trim())
                    .or().like(Inventory::getBatchNo, keyword.trim())
                    .or().like(Inventory::getLocationCode, keyword.trim()));
        }
        if (notBlank(warehouseCode)) query.eq(Inventory::getWarehouseCode, warehouseCode);
        if (notBlank(stockStatus)) query.eq(Inventory::getStockStatus, stockStatus);
        try {
            return inventoryMapper.selectList(query).stream().map(this::inventoryMap).toList();
        } catch (DataAccessException ex) {
            // Older installations may not have received the additive WMS columns yet.
            return compatListInventory(keyword, warehouseCode, stockStatus);
        }
    }

    @Override
    public List<Map<String, Object>> listTransactions(String keyword, String type, int limit) {
        LambdaQueryWrapper<MaterialTransaction> query = new LambdaQueryWrapper<MaterialTransaction>()
                .eq(MaterialTransaction::getTenantId, tenantId()).orderByDesc(MaterialTransaction::getId)
                .last("limit " + Math.max(1, Math.min(limit, 500)));
        if (notBlank(keyword)) query.and(q -> q.like(MaterialTransaction::getTransactionNo, keyword.trim())
                .or().like(MaterialTransaction::getMaterialCode, keyword.trim())
                .or().like(MaterialTransaction::getSourceDocNo, keyword.trim())
                .or().like(MaterialTransaction::getDocumentNo, keyword.trim()));
        if (notBlank(type)) query.eq(MaterialTransaction::getTransactionType, type);
        try { return transactionMapper.selectList(query).stream().map(this::transactionMap).toList(); }
        catch (DataAccessException ex) { return compatListTransactions(keyword, type, limit); }
    }

    @Override
    public List<Map<String, Object>> listDocuments(String type, String status) {
        LambdaQueryWrapper<WhDocument> query = new LambdaQueryWrapper<WhDocument>()
                .eq(WhDocument::getTenantId, tenantId()).orderByDesc(WhDocument::getId);
        if (notBlank(type)) query.eq(WhDocument::getDocumentType, type);
        if (notBlank(status)) query.eq(WhDocument::getStatus, status);
        try { return documentMapper.selectList(query).stream().map(this::documentMap).toList(); }
        catch (DataAccessException ex) { return compatListDocuments(type, status); }
    }

    @Override
    public List<Map<String, Object>> listDocumentLines(long documentId) {
        try {
            return documentLineMapper.selectList(new LambdaQueryWrapper<WhDocumentLine>()
                    .eq(WhDocumentLine::getTenantId, tenantId()).eq(WhDocumentLine::getDocumentId, documentId)
                    .orderByAsc(WhDocumentLine::getLineNo)).stream().map(this::documentLineMap).toList();
        } catch (DataAccessException ex) {
            return compatListDocumentLines(documentId);
        }
    }

    @Override public List<Map<String, Object>> listCounts() { return compatListCounts(); }
    @Override public List<Map<String, Object>> listCountLines(long countId) { return compatListCountLines(countId); }

    @Override
    public List<Map<String, Object>> listBarcodes(String keyword, String status) {
        LambdaQueryWrapper<Barcode> query = new LambdaQueryWrapper<Barcode>()
                .eq(Barcode::getTenantId, tenantId()).orderByDesc(Barcode::getId);
        if (notBlank(keyword)) query.and(q -> q.like(Barcode::getBarcode, keyword.trim())
                .or().like(Barcode::getMaterialCode, keyword.trim()).or().like(Barcode::getBatchNo, keyword.trim()));
        if (notBlank(status)) query.eq(Barcode::getStatus, status);
        try { return barcodeMapper.selectList(query).stream().map(this::barcodeMap).toList(); }
        catch (DataAccessException ex) { return compatListBarcodes(keyword, status); }
    }

    @Override public List<Map<String, Object>> listBarcodeRules() { return compatListBarcodeRules(); }

    @Override
    @Transactional
    @AuditOperation(action = "WMS_TRANSACTION_POST", resource = "INVENTORY")
    @Idempotent
    @RequireRole({"admin", "warehouse"})
    public Map<String, Object> postTransaction(Map<String, Object> payload, String actor) {
        try {
            return postTransactionByMybatis(payload, actor);
        } catch (DataAccessException ex) {
            return compatPostTransaction(payload, actor);
        }
    }

    @Override
    @Transactional
    @AuditOperation(action = "WMS_STOCK_LOCK", resource = "INVENTORY")
    @RequireRole({"admin", "warehouse"})
    public Map<String, Object> changeLock(Map<String, Object> payload, String actor, boolean lock) {
        try {
            String materialCode = required(payload, "materialCode", "物料编码");
            String warehouseCode = required(payload, "warehouseCode", "仓库编码");
            String locationCode = required(payload, "locationCode", "库位编码");
            int quantity = positive(payload.get("quantity"), "数量");
            String batchNo = text(payload.get("batchNo"));
            int updated = inventoryMapper.update(null, stockUpdate(materialCode, warehouseCode, locationCode, batchNo)
                    .and(lock ? q -> q.ge(Inventory::getAvailableQty, quantity) : q -> q.ge(Inventory::getLockedQty, quantity))
                    .setSql(lock ? "available_qty=available_qty-" + quantity + ",locked_qty=locked_qty+" + quantity : "available_qty=available_qty+" + quantity + ",locked_qty=locked_qty-" + quantity)
                    .setSql("version_no=version_no+1"));
            if (updated == 0) throw new IllegalArgumentException(lock ? "可用库存不足或库存记录不存在" : "锁定库存不足或库存记录不存在");
            recordSimpleTransaction(lock ? "LOCK" : "UNLOCK", materialCode, warehouseCode, locationCode, batchNo, quantity, actor, text(payload.get("reasonCode")));
            return Map.of("materialCode", materialCode, "quantity", quantity, "locked", lock);
        } catch (DataAccessException ex) { return compatChangeLock(payload, actor, lock); }
    }

    @Override
    @Transactional
    @AuditOperation(action = "WMS_STOCK_RESERVE", resource = "INVENTORY")
    @RequireRole({"admin", "warehouse"})
    public Map<String, Object> changeReservation(Map<String, Object> payload, String actor, boolean reserve) {
        try {
            String materialCode = required(payload, "materialCode", "物料编码");
            String warehouseCode = required(payload, "warehouseCode", "仓库编码");
            String locationCode = required(payload, "locationCode", "库位编码");
            int quantity = positive(payload.get("quantity"), "数量");
            String batchNo = text(payload.get("batchNo"));
            LambdaUpdateWrapper<Inventory> update = stockUpdate(materialCode, warehouseCode, locationCode, batchNo);
            if (reserve) update.eq(Inventory::getStockStatus, "AVAILABLE").ge(Inventory::getAvailableQty, quantity);
            else update.ge(Inventory::getReservedQty, quantity);
            update.setSql(reserve ? "available_qty=available_qty-" + quantity + ",reserved_qty=reserved_qty+" + quantity : "available_qty=available_qty+" + quantity + ",reserved_qty=reserved_qty-" + quantity).setSql("version_no=version_no+1");
            if (inventoryMapper.update(null, update) == 0) throw new IllegalArgumentException(reserve ? "可用库存不足、已冻结或库存记录不存在" : "预留库存不足或库存记录不存在");
            recordSimpleTransaction(reserve ? "RESERVE" : "RELEASE", materialCode, warehouseCode, locationCode, batchNo, quantity, actor, text(payload.get("reasonCode")));
            return Map.of("materialCode", materialCode, "quantity", quantity, "reserved", reserve);
        } catch (DataAccessException ex) { return compatChangeReservation(payload, actor, reserve); }
    }

    @Override
    @Transactional
    @AuditOperation(action = "WMS_STOCK_STATUS", resource = "INVENTORY")
    @RequireRole({"admin", "warehouse"})
    public Map<String, Object> changeStockStatus(Map<String, Object> payload, String actor, boolean freeze) {
        try {
            String materialCode = required(payload, "materialCode", "物料编码");
            String warehouseCode = required(payload, "warehouseCode", "仓库编码");
            String locationCode = required(payload, "locationCode", "库位编码");
            String batchNo = text(payload.get("batchNo"));
            String status = freeze ? "FROZEN" : "AVAILABLE";
            int updated = inventoryMapper.update(null, stockUpdate(materialCode, warehouseCode, locationCode, batchNo).set(Inventory::getStockStatus, status).setSql("version_no=version_no+1"));
            if (updated == 0) throw new IllegalArgumentException("库存记录不存在");
            recordSimpleTransaction(freeze ? "FREEZE" : "UNFREEZE", materialCode, warehouseCode, locationCode, batchNo, 0, actor, text(payload.get("reasonCode")));
            return Map.of("materialCode", materialCode, "stockStatus", status);
        } catch (DataAccessException ex) { return compatChangeStockStatus(payload, actor, freeze); }
    }

    @Override
    @Transactional
    @RequireRole({"admin", "warehouse"})
    public Map<String, Object> createCount(Map<String, Object> payload, String actor) { return compatCreateCount(payload, actor); }
    @Override
    @Transactional
    @RequireRole({"admin", "warehouse"})
    public Map<String, Object> submitCount(long countId, Map<String, Object> payload, String actor) { return compatSubmitCount(countId, payload, actor); }
    @Override @RequireRole({"admin", "warehouse"}) public Map<String, Object> quickCount(Map<String, Object> payload, String actor) { return compatQuickCount(payload, actor); }

    @Override
    public Map<String, Object> parseBarcode(String barcode) {
        Barcode record = barcodeMapper.selectOne(new LambdaQueryWrapper<Barcode>()
                .eq(Barcode::getTenantId, tenantId()).eq(Barcode::getBarcode, barcode));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", record != null);
        result.put("barcode", barcode);
        if (record != null) {
            result.putAll(barcodeMap(record));
            result.put("inventory", listInventory(record.getMaterialCode(), record.getWarehouseCode(), null));
            result.put("trace", trace(record.getMaterialCode(), record.getBatchNo(), barcode));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> trace(String materialCode, String batchNo, String barcode) {
        if (notBlank(barcode)) {
            Barcode record = barcodeMapper.selectOne(new LambdaQueryWrapper<Barcode>()
                    .eq(Barcode::getTenantId, tenantId()).eq(Barcode::getBarcode, barcode));
            if (record != null) { materialCode = record.getMaterialCode(); batchNo = record.getBatchNo(); }
        }
        if (!notBlank(materialCode)) return List.of();
        LambdaQueryWrapper<MaterialTransaction> query = new LambdaQueryWrapper<MaterialTransaction>()
                .eq(MaterialTransaction::getTenantId, tenantId()).eq(MaterialTransaction::getMaterialCode, materialCode)
                .orderByAsc(MaterialTransaction::getId);
        if (notBlank(batchNo)) query.eq(MaterialTransaction::getBatchNo, batchNo);
        return transactionMapper.selectList(query).stream().map(this::traceMap).toList();
    }

    @Override
    @Transactional
    @AuditOperation(action = "BARCODE_CREATE", resource = "BARCODE")
    @RequireRole({"admin", "warehouse"})
    public Map<String, Object> createBarcode(Map<String, Object> payload) {
        String code = text(payload.get("barcode"));
        if (!notBlank(code)) code = "PDA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        Barcode entity = new Barcode();
        entity.setTenantId(tenantId());
        entity.setBarcode(code);
        entity.setBarcodeType(defaultText(payload.get("barcodeType"), "MATERIAL"));
        entity.setMaterialCode(text(payload.get("materialCode")));
        entity.setBatchNo(text(payload.get("batchNo")));
        entity.setStatus("ACTIVE");
        entity.setSourceDocNo(text(payload.get("sourceDocNo")));
        entity.setWarehouseCode(text(payload.get("warehouseCode")));
        entity.setLocationCode(text(payload.get("locationCode")));
        entity.setPrintedCount(0);
        barcodeMapper.insert(entity);
        return barcodeMap(barcodeMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional
    @RequireRole({"admin", "warehouse"})
    public int printBarcodes(List<String> barcodes) {
        if (barcodes == null || barcodes.isEmpty()) throw new IllegalArgumentException("请选择要打印的条码");
        int total = 0;
        for (String code : barcodes) {
            total += barcodeMapper.update(null, new LambdaUpdateWrapper<Barcode>()
                    .eq(Barcode::getTenantId, tenantId()).eq(Barcode::getBarcode, code).eq(Barcode::getStatus, "ACTIVE")
                    .setSql("printed_count=coalesce(printed_count,0)+1").set(Barcode::getPrintedAt, LocalDateTime.now()));
        }
        return total;
    }

    @Override
    @Transactional
    @RequireRole({"admin", "warehouse"})
    public void voidBarcode(String barcode) {
        int updated = barcodeMapper.update(null, new LambdaUpdateWrapper<Barcode>()
                .eq(Barcode::getTenantId, tenantId()).eq(Barcode::getBarcode, barcode).ne(Barcode::getStatus, "VOID")
                .set(Barcode::getStatus, "VOID").set(Barcode::getVoidedAt, LocalDateTime.now()));
        if (updated == 0) throw new IllegalArgumentException("条码不存在或已作废");
    }

    @Override
    @Transactional
    @RequireRole({"admin", "warehouse"})
    public Map<String, Object> saveWarehouse(Map<String, Object> payload) {
        String code = required(payload, "warehouseCode", "仓库编码");
        WhWarehouse entity = warehouseMapper.selectOne(new LambdaQueryWrapper<WhWarehouse>().eq(WhWarehouse::getTenantId, tenantId()).eq(WhWarehouse::getWarehouseCode, code));
        if (entity == null) entity = new WhWarehouse();
        entity.setTenantId(tenantId()); entity.setWarehouseCode(code); entity.setWarehouseName(required(payload, "warehouseName", "仓库名称"));
        entity.setWarehouseType(defaultText(payload.get("warehouseType"), "GENERAL")); entity.setOwnerCode(text(payload.get("ownerCode")));
        entity.setStatus(defaultText(payload.get("status"), "ACTIVE")); entity.setRemark(text(payload.get("remark")));
        if (entity.getId() == null) warehouseMapper.insert(entity); else warehouseMapper.updateById(entity);
        return warehouseMap(warehouseMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional
    @RequireRole({"admin", "warehouse"})
    public Map<String, Object> saveLocation(Map<String, Object> payload) {
        String warehouse = required(payload, "warehouseCode", "仓库编码"); String code = required(payload, "locationCode", "库位编码");
        WhLocation entity = locationMapper.selectOne(new LambdaQueryWrapper<WhLocation>().eq(WhLocation::getTenantId, tenantId()).eq(WhLocation::getWarehouseCode, warehouse).eq(WhLocation::getLocationCode, code));
        if (entity == null) entity = new WhLocation();
        entity.setTenantId(tenantId()); entity.setWarehouseCode(warehouse); entity.setAreaCode(text(payload.get("areaCode"))); entity.setLocationCode(code);
        entity.setLocationName(text(payload.get("locationName"))); entity.setLocationType(defaultText(payload.get("locationType"), "BIN")); entity.setCapacityQty(number(payload.get("capacityQty"), 0)); entity.setStatus(defaultText(payload.get("status"), "AVAILABLE"));
        if (entity.getId() == null) locationMapper.insert(entity); else locationMapper.updateById(entity);
        return locationMap(locationMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional
    @RequireRole({"admin", "warehouse"})
    public Map<String, Object> saveArea(Map<String, Object> payload) {
        String warehouse = required(payload, "warehouseCode", "仓库编码"); String code = required(payload, "areaCode", "库区编码");
        WhStorageArea entity = areaMapper.selectOne(new LambdaQueryWrapper<WhStorageArea>().eq(WhStorageArea::getTenantId, tenantId()).eq(WhStorageArea::getWarehouseCode, warehouse).eq(WhStorageArea::getAreaCode, code));
        if (entity == null) entity = new WhStorageArea();
        entity.setTenantId(tenantId()); entity.setWarehouseCode(warehouse); entity.setAreaCode(code); entity.setAreaName(required(payload, "areaName", "库区名称")); entity.setAreaType(defaultText(payload.get("areaType"), "NORMAL")); entity.setStatus(defaultText(payload.get("status"), "ACTIVE")); entity.setRemark(text(payload.get("remark")));
        if (entity.getId() == null) areaMapper.insert(entity); else areaMapper.updateById(entity);
        return areaMap(areaMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional
    @RequireRole({"admin", "warehouse"})
    public Map<String, Object> saveMaterial(Map<String, Object> payload) {
        String code = required(payload, "materialCode", "物料编码");
        WhMaterial entity = materialMapper.selectOne(new LambdaQueryWrapper<WhMaterial>().eq(WhMaterial::getTenantId, tenantId()).eq(WhMaterial::getMaterialCode, code));
        if (entity == null) entity = new WhMaterial();
        entity.setTenantId(tenantId()); entity.setMaterialCode(code); entity.setMaterialName(required(payload, "materialName", "物料名称")); entity.setMaterialType(defaultText(payload.get("materialType"), "RAW")); entity.setUnit(defaultText(payload.get("unit"), "件")); entity.setLotControl(flag(payload.get("lotControl"), true)); entity.setSerialControl(flag(payload.get("serialControl"), false)); entity.setShelfLifeDays(number(payload.get("shelfLifeDays"), 0)); entity.setSafetyStock(number(payload.get("safetyStock"), 0)); entity.setStatus(defaultText(payload.get("status"), "ACTIVE")); entity.setRemark(text(payload.get("remark")));
        if (entity.getId() == null) materialMapper.insert(entity); else materialMapper.updateById(entity);
        return materialMap(materialMapper.selectById(entity.getId()));
    }

    @Override
    public Map<String, Object> saveBarcodeRule(Map<String, Object> payload) { return compatSaveBarcodeRule(payload); }

    private Map<String, Object> postTransactionByMybatis(Map<String, Object> payload, String actor) {
        String type = defaultText(payload.get("transactionType"), "RECEIPT").toUpperCase();
        if (!TRANSACTION_TYPES.contains(type)) throw new IllegalArgumentException("不支持的库存事务类型: " + type);
        int quantity = positive(payload.get("quantity"), "数量");
        String materialCode = required(payload, "materialCode", "物料编码");
        WhMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<WhMaterial>().eq(WhMaterial::getTenantId, tenantId()).eq(WhMaterial::getMaterialCode, materialCode));
        String materialName = defaultText(payload.get("materialName"), material == null ? materialCode : material.getMaterialName());
        String unit = defaultText(payload.get("unit"), material == null ? "件" : material.getUnit());
        String warehouseCode = required(payload, "warehouseCode", "仓库编码");
        String locationCode = required(payload, "locationCode", "库位编码");
        String batchNo = text(payload.get("batchNo"));
        String fromWarehouse = defaultText(payload.get("fromWarehouseCode"), warehouseCode);
        String fromLocation = text(payload.get("fromLocationCode"));
        String toWarehouse = defaultText(payload.get("toWarehouseCode"), warehouseCode);
        String toLocation = defaultText(payload.get("toLocationCode"), locationCode);
        String idempotencyKey = text(payload.get("idempotencyKey"));
        if (notBlank(idempotencyKey)) {
            MaterialTransaction existing = transactionMapper.selectOne(new LambdaQueryWrapper<MaterialTransaction>().eq(MaterialTransaction::getTenantId, tenantId()).eq(MaterialTransaction::getIdempotencyKey, idempotencyKey));
            if (existing != null) return transactionMap(existing);
        }
        boolean source = OUTBOUND_TYPES.contains(type) || "PUTAWAY".equals(type) || "MOVE".equals(type);
        boolean target = Set.of("RECEIPT", "RETURN", "FINISHED_RECEIPT", "TRANSFER_IN", "PUTAWAY", "MOVE", "ADJUSTMENT").contains(type);
        if ("PUTAWAY".equals(type) || "MOVE".equals(type)) {
            if (!notBlank(fromLocation)) throw new IllegalArgumentException("移库/上架必须提供来源库位");
            if (fromWarehouse.equals(toWarehouse) && fromLocation.equals(toLocation)) throw new IllegalArgumentException("来源库位和目标库位不能相同");
        }
        if ("ADJUSTMENT".equals(type)) {
            int signed = number(payload.get("adjustment"), 0);
            if (signed == 0) throw new IllegalArgumentException("调整数量不能为 0");
            quantity = Math.abs(signed); source = signed < 0; target = signed > 0;
        }
        ensureMaterial(materialCode, materialName, unit, payload, material);
        if (notBlank(batchNo)) ensureBatch(materialCode, batchNo, payload);
        if (source) changeStock(fromWarehouse, notBlank(fromLocation) ? fromLocation : locationCode, materialCode, materialName, batchNo, unit, -quantity, payload);
        if (target) changeStock(toWarehouse, toLocation, materialCode, materialName, batchNo, unit, quantity, payload);

        String documentNo = "WD-" + TenantContext.require().tenantCode() + "-" + UUID.randomUUID();
        String transactionNo = "TX-" + TenantContext.require().tenantCode() + "-" + UUID.randomUUID();
        WhDocument document = new WhDocument();
        document.setTenantId(tenantId()); document.setDocumentNo(documentNo); document.setDocumentType(type); document.setStatus("COMPLETED"); document.setSourceDocNo(text(payload.get("sourceDocNo"))); document.setWarehouseCode(warehouseCode); document.setFromWarehouseCode(fromWarehouse); document.setToWarehouseCode(toWarehouse); document.setOperatorName(actor); document.setRemark(text(payload.get("remark"))); document.setIdempotencyKey(idempotencyKey); document.setCompletedAt(LocalDateTime.now());
        documentMapper.insert(document);
        WhDocumentLine line = new WhDocumentLine();
        line.setTenantId(tenantId()); line.setDocumentId(document.getId()); line.setLineNo(1); line.setMaterialCode(materialCode); line.setMaterialName(materialName); line.setUnit(unit); line.setPlannedQty(quantity); line.setActualQty(quantity); line.setBatchNo(batchNo); line.setFromLocationCode(fromLocation); line.setToLocationCode(toLocation); line.setWorkOrderNo(text(payload.get("workOrderNo"))); line.setQualityStatus(text(payload.get("qualityStatus")));
        documentLineMapper.insert(line);
        MaterialTransaction transaction = new MaterialTransaction();
        transaction.setTenantId(tenantId()); transaction.setTransactionNo(transactionNo); transaction.setTransactionType(type); transaction.setMaterialCode(materialCode); transaction.setMaterialName(materialName); transaction.setWarehouseCode(warehouseCode); transaction.setLocationCode(locationCode); transaction.setBatchNo(batchNo); transaction.setQuantity(quantity); transaction.setUnit(unit); transaction.setOperatorName(actor); transaction.setSourceDocNo(text(payload.get("sourceDocNo"))); transaction.setDocumentNo(documentNo); transaction.setFromWarehouseCode(fromWarehouse); transaction.setFromLocationCode(fromLocation); transaction.setToWarehouseCode(toWarehouse); transaction.setToLocationCode(toLocation); transaction.setReasonCode(text(payload.get("reasonCode"))); transaction.setIdempotencyKey(idempotencyKey); transaction.setStatus("COMPLETED"); transaction.setRemark(text(payload.get("remark")));
        transactionMapper.insert(transaction);
        return Map.of("transactionNo", transactionNo, "documentNo", documentNo, "transactionType", type, "quantity", quantity, "materialCode", materialCode, "fromLocationCode", fromLocation == null ? "" : fromLocation, "toLocationCode", toLocation, "status", "COMPLETED");
    }

    private LambdaUpdateWrapper<Inventory> stockUpdate(String materialCode, String warehouseCode, String locationCode, String batchNo) {
        LambdaUpdateWrapper<Inventory> update = new LambdaUpdateWrapper<Inventory>().eq(Inventory::getTenantId, tenantId()).eq(Inventory::getMaterialCode, materialCode).eq(Inventory::getWarehouseCode, warehouseCode).eq(Inventory::getLocationCode, locationCode);
        if (notBlank(batchNo)) update.eq(Inventory::getBatchNo, batchNo); else update.isNull(Inventory::getBatchNo);
        return update;
    }

    private void changeStock(String warehouseCode, String locationCode, String materialCode, String materialName, String batchNo, String unit, int delta, Map<String, Object> payload) {
        if (!notBlank(locationCode)) throw new IllegalArgumentException("库位不能为空");
        Inventory row = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>().eq(Inventory::getTenantId, tenantId()).eq(Inventory::getMaterialCode, materialCode).eq(Inventory::getWarehouseCode, warehouseCode).eq(Inventory::getLocationCode, locationCode).eq(notBlank(batchNo), Inventory::getBatchNo, batchNo).isNull(!notBlank(batchNo), Inventory::getBatchNo));
        if (row == null) {
            if (delta < 0) throw new IllegalArgumentException("可用库存不足或库存记录不存在");
            Inventory created = new Inventory(); created.setTenantId(tenantId()); created.setMaterialCode(materialCode); created.setMaterialName(materialName); created.setWarehouseCode(warehouseCode); created.setLocationCode(locationCode); created.setBatchNo(batchNo); created.setAvailableQty(delta); created.setLockedQty(0); created.setReservedQty(0); created.setInTransitQty(0); created.setUnit(unit); created.setSafetyStock(materialSafetyStock(materialCode)); created.setStockStatus(defaultText(payload.get("stockStatus"), "AVAILABLE")); created.setExpiryDate(date(payload.get("expiryDate"))); created.setVersionNo(0L); inventoryMapper.insert(created); return;
        }
        if (delta < 0 && ("FROZEN".equals(row.getStockStatus()) || "HOLD".equals(row.getStockStatus())) && !flag(payload.get("force"), false)) throw new IllegalArgumentException("库存状态为" + row.getStockStatus() + "，不可直接出库");
        if (delta < 0 && !flag(payload.get("force"), false) && notBlank(batchNo)) {
            WhBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<WhBatch>().eq(WhBatch::getTenantId, tenantId()).eq(WhBatch::getMaterialCode, materialCode).eq(WhBatch::getBatchNo, batchNo));
            if (batch == null || !"PASSED".equalsIgnoreCase(defaultText(batch.getQualityStatus(), "PENDING"))) throw new IllegalArgumentException("批次质量状态为" + (batch == null ? "PENDING" : batch.getQualityStatus()) + "，检验放行前不可出库");
            if (!"ACTIVE".equalsIgnoreCase(defaultText(batch.getBatchStatus(), "ACTIVE"))) throw new IllegalArgumentException("批次状态为" + batch.getBatchStatus() + "，不可出库");
        }
        if (value(row.getAvailableQty()) + delta < 0) throw new IllegalArgumentException("可用库存不足");
        LambdaUpdateWrapper<Inventory> update = stockUpdate(materialCode, warehouseCode, locationCode, batchNo).eq(Inventory::getVersionNo, row.getVersionNo()).ge(delta < 0, Inventory::getAvailableQty, -delta).setSql("available_qty=available_qty+" + delta).setSql("version_no=version_no+1");
        if (payload.get("expiryDate") != null) update.set(Inventory::getExpiryDate, date(payload.get("expiryDate")));
        if (inventoryMapper.update(null, update) == 0) throw new IllegalArgumentException("库存已被其他操作修改，请刷新后重试");
    }

    private void ensureMaterial(String code, String name, String unit, Map<String, Object> payload, WhMaterial existing) {
        if (existing != null) return;
        WhMaterial material = new WhMaterial(); material.setTenantId(tenantId()); material.setMaterialCode(code); material.setMaterialName(name); material.setMaterialType(defaultText(payload.get("materialType"), "RAW")); material.setUnit(unit); material.setLotControl(true); material.setSerialControl(false); material.setShelfLifeDays(0); material.setSafetyStock(number(payload.get("safetyStock"), 0)); material.setStatus("ACTIVE"); materialMapper.insert(material);
    }

    private void ensureBatch(String materialCode, String batchNo, Map<String, Object> payload) {
        if (batchMapper.selectOne(new LambdaQueryWrapper<WhBatch>().eq(WhBatch::getTenantId, tenantId()).eq(WhBatch::getMaterialCode, materialCode).eq(WhBatch::getBatchNo, batchNo)) != null) return;
        WhBatch batch = new WhBatch(); batch.setTenantId(tenantId()); batch.setMaterialCode(materialCode); batch.setBatchNo(batchNo); batch.setProductionDate(date(payload.get("productionDate"))); batch.setExpiryDate(date(payload.get("expiryDate"))); batch.setSupplierCode(text(payload.get("supplierCode"))); batch.setQualityStatus(defaultText(payload.get("qualityStatus"), "PENDING")); batch.setBatchStatus("ACTIVE"); batch.setRemark(text(payload.get("remark"))); batchMapper.insert(batch);
    }

    private int materialSafetyStock(String code) { WhMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<WhMaterial>().eq(WhMaterial::getTenantId, tenantId()).eq(WhMaterial::getMaterialCode, code)); return material == null ? 0 : value(material.getSafetyStock()); }
    private void recordSimpleTransaction(String type, String materialCode, String warehouseCode, String locationCode, String batchNo, int quantity, String actor, String reason) { MaterialTransaction row = new MaterialTransaction(); row.setTenantId(tenantId()); row.setTransactionNo("TX-" + TenantContext.require().tenantCode() + "-" + UUID.randomUUID()); row.setTransactionType(type); row.setMaterialCode(materialCode); row.setWarehouseCode(warehouseCode); row.setLocationCode(locationCode); row.setBatchNo(batchNo); row.setQuantity(quantity); row.setUnit("件"); row.setOperatorName(actor); row.setReasonCode(reason); row.setStatus("COMPLETED"); transactionMapper.insert(row); }

    private long tenantId() { return TenantContext.require().tenantId(); }
    private static int value(Integer number) { return number == null ? 0 : number; }
    private static boolean notBlank(String text) { return text != null && !text.isBlank(); }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    private static String defaultText(Object value, String fallback) { return notBlank(text(value)) ? text(value) : fallback; }
    private static String required(Map<String, Object> payload, String key, String label) { String value = text(payload.get(key)); if (!notBlank(value)) throw new IllegalArgumentException(label + "不能为空"); return value; }
    private static int number(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; } }
    private static int positive(Object value, String label) { int number = number(value, 0); if (number <= 0) throw new IllegalArgumentException(label + "必须大于 0"); return number; }
    private static boolean flag(Object value, boolean fallback) { return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)) || "1".equals(String.valueOf(value)); }
    private static LocalDate date(Object value) { if (value == null || String.valueOf(value).isBlank()) return null; try { return LocalDate.parse(String.valueOf(value)); } catch (RuntimeException ex) { throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd"); } }

    private Map<String, Object> warehouseMap(WhWarehouse row) { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", row.getId()); m.put("warehouse_code", row.getWarehouseCode()); m.put("warehouse_name", row.getWarehouseName()); m.put("warehouse_type", row.getWarehouseType()); m.put("owner_code", row.getOwnerCode()); m.put("status", row.getStatus()); m.put("remark", row.getRemark()); m.put("created_at", row.getCreatedAt()); m.put("updated_at", row.getUpdatedAt()); return m; }
    private Map<String, Object> areaMap(WhStorageArea row) { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", row.getId()); m.put("warehouse_code", row.getWarehouseCode()); m.put("area_code", row.getAreaCode()); m.put("area_name", row.getAreaName()); m.put("area_type", row.getAreaType()); m.put("status", row.getStatus()); m.put("remark", row.getRemark()); return m; }
    private Map<String, Object> locationMap(WhLocation row) { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", row.getId()); m.put("warehouse_code", row.getWarehouseCode()); m.put("area_code", row.getAreaCode()); m.put("location_code", row.getLocationCode()); m.put("location_name", row.getLocationName()); m.put("location_type", row.getLocationType()); m.put("capacity_qty", row.getCapacityQty()); m.put("status", row.getStatus()); return m; }
    private Map<String, Object> materialMap(WhMaterial row) { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", row.getId()); m.put("material_code", row.getMaterialCode()); m.put("material_name", row.getMaterialName()); m.put("material_type", row.getMaterialType()); m.put("unit", row.getUnit()); m.put("lot_control", Boolean.TRUE.equals(row.getLotControl()) ? 1 : 0); m.put("serial_control", Boolean.TRUE.equals(row.getSerialControl()) ? 1 : 0); m.put("shelf_life_days", row.getShelfLifeDays()); m.put("safety_stock", row.getSafetyStock()); m.put("status", row.getStatus()); m.put("remark", row.getRemark()); return m; }
    private Map<String, Object> batchMap(WhBatch row) { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", row.getId()); m.put("material_code", row.getMaterialCode()); m.put("batch_no", row.getBatchNo()); m.put("production_date", row.getProductionDate()); m.put("expiry_date", row.getExpiryDate()); m.put("supplier_code", row.getSupplierCode()); m.put("quality_status", row.getQualityStatus()); m.put("batch_status", row.getBatchStatus()); m.put("remark", row.getRemark()); m.put("created_at", row.getCreatedAt()); return m; }
    private Map<String, Object> inventoryMap(Inventory row) { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", row.getId()); m.put("material_code", row.getMaterialCode()); m.put("material_name", row.getMaterialName()); m.put("warehouse_code", row.getWarehouseCode()); m.put("location_code", row.getLocationCode()); m.put("batch_no", row.getBatchNo()); m.put("available_qty", row.getAvailableQty()); m.put("locked_qty", row.getLockedQty()); m.put("reserved_qty", row.getReservedQty()); m.put("in_transit_qty", row.getInTransitQty()); m.put("unit", row.getUnit()); m.put("safety_stock", row.getSafetyStock()); m.put("stock_status", row.getStockStatus()); m.put("expiry_date", row.getExpiryDate()); m.put("version_no", row.getVersionNo()); m.put("updated_at", row.getUpdatedAt()); return m; }
    private Map<String, Object> transactionMap(MaterialTransaction row) { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", row.getId()); m.put("transaction_no", row.getTransactionNo()); m.put("transaction_type", row.getTransactionType()); m.put("material_code", row.getMaterialCode()); m.put("material_name", row.getMaterialName()); m.put("warehouse_code", row.getWarehouseCode()); m.put("location_code", row.getLocationCode()); m.put("batch_no", row.getBatchNo()); m.put("quantity", row.getQuantity()); m.put("unit", row.getUnit()); m.put("operator_name", row.getOperatorName()); m.put("source_doc_no", row.getSourceDocNo()); m.put("document_no", row.getDocumentNo()); m.put("from_warehouse_code", row.getFromWarehouseCode()); m.put("from_location_code", row.getFromLocationCode()); m.put("to_warehouse_code", row.getToWarehouseCode()); m.put("to_location_code", row.getToLocationCode()); m.put("reason_code", row.getReasonCode()); m.put("status", row.getStatus()); m.put("remark", row.getRemark()); m.put("created_at", row.getCreatedAt()); return m; }
    private Map<String, Object> traceMap(MaterialTransaction row) { Map<String, Object> m = transactionMap(row); m.remove("id"); m.remove("from_warehouse_code"); m.remove("from_location_code"); m.remove("to_warehouse_code"); m.remove("to_location_code"); m.remove("reason_code"); m.remove("status"); m.remove("remark"); return m; }
    private Map<String, Object> barcodeMap(Barcode row) { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", row.getId()); m.put("barcode", row.getBarcode()); m.put("barcode_type", row.getBarcodeType()); m.put("material_code", row.getMaterialCode()); m.put("batch_no", row.getBatchNo()); m.put("warehouse_code", row.getWarehouseCode()); m.put("location_code", row.getLocationCode()); m.put("status", row.getStatus()); m.put("source_doc_no", row.getSourceDocNo()); m.put("printed_count", row.getPrintedCount()); m.put("printed_at", row.getPrintedAt()); m.put("voided_at", row.getVoidedAt()); m.put("created_at", row.getCreatedAt()); return m; }
    private Map<String, Object> documentMap(WhDocument row) { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", row.getId()); m.put("document_no", row.getDocumentNo()); m.put("document_type", row.getDocumentType()); m.put("status", row.getStatus()); m.put("source_doc_no", row.getSourceDocNo()); m.put("warehouse_code", row.getWarehouseCode()); m.put("from_warehouse_code", row.getFromWarehouseCode()); m.put("to_warehouse_code", row.getToWarehouseCode()); m.put("operator_name", row.getOperatorName()); m.put("remark", row.getRemark()); m.put("created_at", row.getCreatedAt()); m.put("completed_at", row.getCompletedAt()); return m; }
    private Map<String, Object> documentLineMap(WhDocumentLine row) { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", row.getId()); m.put("document_id", row.getDocumentId()); m.put("line_no", row.getLineNo()); m.put("material_code", row.getMaterialCode()); m.put("material_name", row.getMaterialName()); m.put("unit", row.getUnit()); m.put("planned_qty", row.getPlannedQty()); m.put("actual_qty", row.getActualQty()); m.put("batch_no", row.getBatchNo()); m.put("from_location_code", row.getFromLocationCode()); m.put("to_location_code", row.getToLocationCode()); m.put("work_order_no", row.getWorkOrderNo()); m.put("quality_status", row.getQualityStatus()); m.put("created_at", row.getCreatedAt()); return m; }

    public Map<String, Object> compatSummary() {
        long tenant = compatTenantId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inventorySkus", compatScalar("select count(distinct material_code) from inventory where tenant_id=?", tenant));
        result.put("inventoryQty", compatScalar("select coalesce(sum(available_qty),0) from inventory where tenant_id=?", tenant));
        result.put("lockedQty", compatScalar("select coalesce(sum(locked_qty),0) from inventory where tenant_id=?", tenant));
        result.put("reservedQty", compatScalar("select coalesce(sum(reserved_qty),0) from inventory where tenant_id=?", tenant));
        result.put("inTransitQty", compatScalar("select coalesce(sum(in_transit_qty),0) from inventory where tenant_id=?", tenant));
        result.put("lowStock", compatScalar("select count(*) from inventory where tenant_id=? and available_qty < safety_stock", tenant));
        result.put("frozenStock", compatScalar("select count(*) from inventory where tenant_id=? and stock_status='FROZEN'", tenant));
        result.put("expiringBatches", compatScalar("select count(*) from wh_batch where tenant_id=? and expiry_date is not null and expiry_date between current_date and current_date + 30", tenant));
        result.put("openCounts", compatScalar("select count(*) from wh_stock_count where tenant_id=? and status='OPEN'", tenant));
        result.put("todayTransactions", compatScalar("select count(*) from material_transaction where tenant_id=? and date(created_at)=current_date", tenant));
        result.put("transactionTrend", jdbc.queryForList("select transaction_type as type, count(*) as count, coalesce(sum(quantity),0) as quantity from material_transaction where tenant_id=? and created_at >= current_date - 6 group by transaction_type order by transaction_type", tenant));
        return result;
    }

    public List<Map<String, Object>> compatListWarehouses() {
        return jdbc.queryForList("select id, warehouse_code, warehouse_name, warehouse_type, owner_code, status, remark, created_at, updated_at from wh_warehouse where tenant_id=? order by warehouse_code", compatTenantId());
    }

    public List<Map<String, Object>> compatListAreas(String warehouseCode) {
        if (compatBlank(warehouseCode)) {
            return jdbc.queryForList("select id, warehouse_code, area_code, area_name, area_type, status, remark from wh_storage_area where tenant_id=? order by warehouse_code, area_code", compatTenantId());
        }
        return jdbc.queryForList("select id, warehouse_code, area_code, area_name, area_type, status, remark from wh_storage_area where tenant_id=? and warehouse_code=? order by area_code", compatTenantId(), warehouseCode);
    }

    public List<Map<String, Object>> compatListLocations(String warehouseCode) {
        if (compatBlank(warehouseCode)) {
            return jdbc.queryForList("select id, warehouse_code, area_code, location_code, location_name, location_type, capacity_qty, status from wh_location where tenant_id=? order by warehouse_code, location_code", compatTenantId());
        }
        return jdbc.queryForList("select id, warehouse_code, area_code, location_code, location_name, location_type, capacity_qty, status from wh_location where tenant_id=? and warehouse_code=? order by location_code", compatTenantId(), warehouseCode);
    }

    public List<Map<String, Object>> compatListMaterials(String keyword) {
        if (compatBlank(keyword)) {
            return jdbc.queryForList("select id, material_code, material_name, material_type, unit, lot_control, serial_control, shelf_life_days, safety_stock, status, remark from wh_material where tenant_id=? order by material_code", compatTenantId());
        }
        String like = "%" + keyword.trim() + "%";
        return jdbc.queryForList("select id, material_code, material_name, material_type, unit, lot_control, serial_control, shelf_life_days, safety_stock, status, remark from wh_material where tenant_id=? and (material_code like ? or material_name like ?) order by material_code", compatTenantId(), like, like);
    }

    public List<Map<String, Object>> compatListBatches(String materialCode, String status) {
        StringBuilder sql = new StringBuilder("select id, material_code, batch_no, production_date, expiry_date, supplier_code, quality_status, batch_status, remark, created_at from wh_batch where tenant_id=?");
        List<Object> args = new ArrayList<>();
        args.add(compatTenantId());
        if (!compatBlank(materialCode)) { sql.append(" and material_code=?"); args.add(materialCode); }
        if (!compatBlank(status)) { sql.append(" and batch_status=?"); args.add(status); }
        sql.append(" order by expiry_date is null, expiry_date, batch_no");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> compatListInventory(String keyword, String warehouseCode, String stockStatus) {
        // Older installations can be upgraded one column at a time. Build a
        // stable projection instead of assuming all additive WMS columns are
        // present once any one of them exists.
        boolean hasReserved = compatColumnExists("inventory", "reserved_qty");
        boolean hasInTransit = compatColumnExists("inventory", "in_transit_qty");
        boolean hasStockStatus = compatColumnExists("inventory", "stock_status");
        boolean hasExpiry = compatColumnExists("inventory", "expiry_date");
        boolean hasVersion = compatColumnExists("inventory", "version_no");
        boolean hasUpdatedAt = compatColumnExists("inventory", "updated_at");
        StringBuilder sql = new StringBuilder("select id, material_code, material_name, warehouse_code, location_code, batch_no, available_qty, locked_qty, ")
                .append(hasReserved ? "reserved_qty" : "0").append(" as reserved_qty, ")
                .append(hasInTransit ? "in_transit_qty" : "0").append(" as in_transit_qty, unit, safety_stock, ")
                .append(hasStockStatus ? "stock_status" : "'AVAILABLE'").append(" as stock_status, ")
                .append(hasExpiry ? "expiry_date" : "null").append(" as expiry_date, ")
                .append(hasVersion ? "version_no" : "0").append(" as version_no, ")
                .append(hasUpdatedAt ? "updated_at" : "current_timestamp").append(" as updated_at from inventory where tenant_id=?");
        List<Object> args = new ArrayList<>();
        args.add(compatTenantId());
        if (!compatBlank(keyword)) {
            String like = "%" + keyword.trim() + "%";
            sql.append(" and (material_code like ? or material_name like ? or coalesce(batch_no,'') like ? or location_code like ?)");
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        if (!compatBlank(warehouseCode)) { sql.append(" and warehouse_code=?"); args.add(warehouseCode); }
        if (hasStockStatus && !compatBlank(stockStatus)) { sql.append(" and stock_status=?"); args.add(stockStatus); }
        sql.append(" order by material_code, warehouse_code, location_code, batch_no");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> compatListTransactions(String keyword, String type, int limit) {
        StringBuilder sql = new StringBuilder("select id, transaction_no, transaction_type, material_code, material_name, warehouse_code, location_code, batch_no, quantity, unit, operator_name, source_doc_no, document_no, from_warehouse_code, from_location_code, to_warehouse_code, to_location_code, reason_code, status, remark, created_at from material_transaction where tenant_id=?");
        List<Object> args = new ArrayList<>();
        args.add(compatTenantId());
        if (!compatBlank(keyword)) {
            String like = "%" + keyword.trim() + "%";
            sql.append(" and (transaction_no like ? or material_code like ? or coalesce(source_doc_no,'') like ? or coalesce(document_no,'') like ?)");
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        if (!compatBlank(type)) { sql.append(" and transaction_type=?"); args.add(type); }
        sql.append(" order by id desc limit ").append(Math.max(1, Math.min(limit, 500)));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> compatListDocuments(String type, String status) {
        StringBuilder sql = new StringBuilder("select id, document_no, document_type, status, source_doc_no, warehouse_code, from_warehouse_code, to_warehouse_code, operator_name, remark, created_at, completed_at from wh_document where tenant_id=?");
        List<Object> args = new ArrayList<>();
        args.add(compatTenantId());
        if (!compatBlank(type)) { sql.append(" and document_type=?"); args.add(type); }
        if (!compatBlank(status)) { sql.append(" and status=?"); args.add(status); }
        sql.append(" order by id desc");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> compatListDocumentLines(long documentId) {
        return jdbc.queryForList("select id, document_id, line_no, material_code, material_name, unit, planned_qty, actual_qty, batch_no, from_location_code, to_location_code, work_order_no, quality_status, created_at from wh_document_line where tenant_id=? and document_id=? order by line_no, id", compatTenantId(), documentId);
    }

    public List<Map<String, Object>> compatListCounts() {
        return jdbc.queryForList("select id, count_no, count_type, status, warehouse_code, location_code, operator_name, remark, created_at, submitted_at from wh_stock_count where tenant_id=? order by id desc", compatTenantId());
    }

    public List<Map<String, Object>> compatListCountLines(long countId) {
        return jdbc.queryForList("select id, count_id, material_code, location_code, batch_no, book_qty, count_qty, difference_qty, reason_code from wh_stock_count_line where tenant_id=? and count_id=? order by id", compatTenantId(), countId);
    }

    public List<Map<String, Object>> compatListBarcodes(String keyword, String status) {
        StringBuilder sql = new StringBuilder("select id, barcode, barcode_type, material_code, batch_no, warehouse_code, location_code, status, source_doc_no, printed_count, printed_at, voided_at, created_at from barcode where tenant_id=?");
        List<Object> args = new ArrayList<>();
        args.add(compatTenantId());
        if (!compatBlank(keyword)) { sql.append(" and (barcode like ? or material_code like ? or coalesce(batch_no,'') like ?)"); String like = "%" + keyword.trim() + "%"; args.add(like); args.add(like); args.add(like); }
        if (!compatBlank(status)) { sql.append(" and status=?"); args.add(status); }
        sql.append(" order by id desc");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> compatListBarcodeRules() {
        return jdbc.queryForList("select id, rule_code, rule_name, barcode_type, prefix, sequence_no, status, created_at from wh_barcode_rule where tenant_id=? order by rule_code", compatTenantId());
    }

    @Transactional
    public Map<String, Object> compatPostTransaction(Map<String, Object> payload, String actor) {
        String type = compatStringOr(payload.get("transactionType"), "RECEIPT").toUpperCase();
        if (!COMPAT_TRANSACTION_TYPES.contains(type)) throw new IllegalArgumentException("不支持的库存事务类型: " + type);
        int quantity = compatPositive(payload.get("quantity"), "数量");
        String materialCode = compatRequired(payload, "materialCode", "物料编码");
        String materialName = compatStringOr(payload.get("materialName"), compatMaterialName(materialCode));
        String unit = compatStringOr(payload.get("unit"), compatMaterialUnit(materialCode));
        String warehouseCode = compatRequired(payload, "warehouseCode", "仓库编码");
        String locationCode = compatRequired(payload, "locationCode", "库位编码");
        String batchNo = compatString(payload.get("batchNo"));
        String fromWarehouse = compatStringOr(payload.get("fromWarehouseCode"), warehouseCode);
        String fromLocation = compatString(payload.get("fromLocationCode"));
        String toWarehouse = compatStringOr(payload.get("toWarehouseCode"), warehouseCode);
        String toLocation = compatStringOr(payload.get("toLocationCode"), locationCode);
        String idempotencyKey = compatString(payload.get("idempotencyKey"));
        if (!compatBlank(idempotencyKey)) {
            List<Map<String, Object>> existing = jdbc.queryForList("select transaction_no, document_no, transaction_type, quantity, status from material_transaction where tenant_id=? and idempotency_key=? limit 1", compatTenantId(), idempotencyKey);
            if (!existing.isEmpty()) return existing.get(0);
        }

        boolean source = COMPAT_OUTBOUND_TYPES.contains(type) || type.equals("PUTAWAY") || type.equals("MOVE");
        boolean target = Set.of("RECEIPT", "RETURN", "FINISHED_RECEIPT", "TRANSFER_IN", "PUTAWAY", "MOVE", "ADJUSTMENT").contains(type);
        if (type.equals("PUTAWAY") || type.equals("MOVE")) {
            if (compatBlank(fromLocation)) throw new IllegalArgumentException("移库/上架必须提供来源库位");
            if (fromWarehouse.equals(toWarehouse) && fromLocation.equals(toLocation)) throw new IllegalArgumentException("来源库位和目标库位不能相同");
        }
        if (type.equals("ADJUSTMENT")) {
            int signed = compatNumber(payload.get("adjustment"), 0);
            if (signed == 0) throw new IllegalArgumentException("调整数量不能为 0");
            quantity = Math.abs(signed);
            source = signed < 0;
            target = signed > 0;
        }
        compatEnsureMaterial(materialCode, materialName, unit, payload);
        if (!compatBlank(batchNo)) compatEnsureBatch(materialCode, batchNo, payload);
        if (source) compatChangeStock(fromWarehouse, compatFromLocationOrDefault(fromLocation, locationCode), materialCode, materialName, batchNo, unit, -quantity, payload);
        if (target) compatChangeStock(toWarehouse, toLocation, materialCode, materialName, batchNo, unit, quantity, payload);

        String documentNo = "WD-" + TenantContext.require().tenantCode() + "-" + UUID.randomUUID();
        String transactionNo = "TX-" + TenantContext.require().tenantCode() + "-" + UUID.randomUUID();
        jdbc.update("insert into wh_document(tenant_id, document_no, document_type, status, source_doc_no, warehouse_code, from_warehouse_code, to_warehouse_code, operator_name, remark, idempotency_key, completed_at) values(?,?,?,?,?,?,?,?,?,?,?,current_timestamp)",
                compatTenantId(), documentNo, type, "COMPLETED", compatString(payload.get("sourceDocNo")), warehouseCode, fromWarehouse, toWarehouse, actor, compatString(payload.get("remark")), idempotencyKey);
        Long documentId = jdbc.queryForObject("select id from wh_document where tenant_id=? and document_no=?", Long.class, compatTenantId(), documentNo);
        jdbc.update("insert into wh_document_line(tenant_id, document_id, line_no, material_code, material_name, unit, planned_qty, actual_qty, batch_no, from_location_code, to_location_code, work_order_no, quality_status) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                compatTenantId(), documentId, 1, materialCode, materialName, unit, quantity, quantity, batchNo, fromLocation, toLocation, compatString(payload.get("workOrderNo")), compatString(payload.get("qualityStatus")));
        jdbc.update("insert into material_transaction(tenant_id, transaction_no, transaction_type, material_code, material_name, warehouse_code, location_code, batch_no, quantity, unit, operator_name, source_doc_no, document_no, from_warehouse_code, from_location_code, to_warehouse_code, to_location_code, reason_code, idempotency_key, status, remark) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                compatTenantId(), transactionNo, type, materialCode, materialName, warehouseCode, locationCode, batchNo, quantity, unit, actor,
                compatString(payload.get("sourceDocNo")), documentNo, fromWarehouse, fromLocation, toWarehouse, toLocation,
                compatString(payload.get("reasonCode")), idempotencyKey, "COMPLETED", compatString(payload.get("remark")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transactionNo", transactionNo);
        result.put("documentNo", documentNo);
        result.put("transactionType", type);
        result.put("quantity", quantity);
        result.put("materialCode", materialCode);
        result.put("fromLocationCode", fromLocation);
        result.put("toLocationCode", toLocation);
        result.put("status", "COMPLETED");
        return result;
    }

    @Transactional
    public Map<String, Object> compatChangeLock(Map<String, Object> payload, String actor, boolean lock) {
        String materialCode = compatRequired(payload, "materialCode", "物料编码");
        String warehouseCode = compatRequired(payload, "warehouseCode", "仓库编码");
        String locationCode = compatRequired(payload, "locationCode", "库位编码");
        int quantity = compatPositive(payload.get("quantity"), "数量");
        String batchNo = compatString(payload.get("batchNo"));
        String where = "tenant_id=? and material_code=? and warehouse_code=? and location_code=? and coalesce(batch_no,'')=coalesce(?, '')";
        int updated;
        if (lock) {
            updated = jdbc.update("update inventory set available_qty=available_qty-?, locked_qty=locked_qty+?, version_no=version_no+1, updated_at=current_timestamp where " + where + " and available_qty>=?", quantity, quantity, compatTenantId(), materialCode, warehouseCode, locationCode, batchNo, quantity);
        } else {
            updated = jdbc.update("update inventory set available_qty=available_qty+?, locked_qty=locked_qty-?, version_no=version_no+1, updated_at=current_timestamp where " + where + " and locked_qty>=?", quantity, quantity, compatTenantId(), materialCode, warehouseCode, locationCode, batchNo, quantity);
        }
        if (updated == 0) throw new IllegalArgumentException(lock ? "可用库存不足或库存记录不存在" : "锁定库存不足或库存记录不存在");
        compatRecordSimpleTransaction(lock ? "LOCK" : "UNLOCK", materialCode, warehouseCode, locationCode, batchNo, quantity, actor, compatString(payload.get("reasonCode")));
        return Map.of("materialCode", materialCode, "quantity", quantity, "locked", lock);
    }

    @Transactional
    public Map<String, Object> compatChangeReservation(Map<String, Object> payload, String actor, boolean reserve) {
        String materialCode = compatRequired(payload, "materialCode", "物料编码");
        String warehouseCode = compatRequired(payload, "warehouseCode", "仓库编码");
        String locationCode = compatRequired(payload, "locationCode", "库位编码");
        int quantity = compatPositive(payload.get("quantity"), "数量");
        String batchNo = compatString(payload.get("batchNo"));
        String where = "tenant_id=? and material_code=? and warehouse_code=? and location_code=? and coalesce(batch_no,'')=coalesce(?, '')";
        int updated;
        if (reserve) {
            updated = jdbc.update("update inventory set available_qty=available_qty-?, reserved_qty=reserved_qty+?, version_no=version_no+1, updated_at=current_timestamp where " + where + " and available_qty>=? and stock_status='AVAILABLE'", quantity, quantity, compatTenantId(), materialCode, warehouseCode, locationCode, batchNo, quantity);
        } else {
            updated = jdbc.update("update inventory set available_qty=available_qty+?, reserved_qty=reserved_qty-?, version_no=version_no+1, updated_at=current_timestamp where " + where + " and reserved_qty>=?", quantity, quantity, compatTenantId(), materialCode, warehouseCode, locationCode, batchNo, quantity);
        }
        if (updated == 0) throw new IllegalArgumentException(reserve ? "可用库存不足、已冻结或库存记录不存在" : "预留库存不足或库存记录不存在");
        compatRecordSimpleTransaction(reserve ? "RESERVE" : "RELEASE", materialCode, warehouseCode, locationCode, batchNo, quantity, actor, compatString(payload.get("reasonCode")));
        return Map.of("materialCode", materialCode, "quantity", quantity, "reserved", reserve);
    }

    @Transactional
    public Map<String, Object> compatChangeStockStatus(Map<String, Object> payload, String actor, boolean freeze) {
        String materialCode = compatRequired(payload, "materialCode", "物料编码");
        String warehouseCode = compatRequired(payload, "warehouseCode", "仓库编码");
        String locationCode = compatRequired(payload, "locationCode", "库位编码");
        String batchNo = compatString(payload.get("batchNo"));
        String status = freeze ? "FROZEN" : "AVAILABLE";
        int updated = jdbc.update("update inventory set stock_status=?, version_no=version_no+1, updated_at=current_timestamp where tenant_id=? and material_code=? and warehouse_code=? and location_code=? and coalesce(batch_no,'')=coalesce(?, '')", status, compatTenantId(), materialCode, warehouseCode, locationCode, batchNo);
        if (updated == 0) throw new IllegalArgumentException("库存记录不存在");
        compatRecordSimpleTransaction(freeze ? "FREEZE" : "UNFREEZE", materialCode, warehouseCode, locationCode, batchNo, 0, actor, compatString(payload.get("reasonCode")));
        return Map.of("materialCode", materialCode, "stockStatus", status);
    }

    @Transactional
    public Map<String, Object> compatCreateCount(Map<String, Object> payload, String actor) {
        String warehouseCode = compatRequired(payload, "warehouseCode", "仓库编码");
        String locationCode = compatString(payload.get("locationCode"));
        String countNo = "CK-" + TenantContext.require().tenantCode() + "-" + UUID.randomUUID();
        jdbc.update("insert into wh_stock_count(tenant_id, count_no, count_type, status, warehouse_code, location_code, operator_name, remark) values(?,?,?,?,?,?,?,?)",
                compatTenantId(), countNo, compatStringOr(payload.get("countType"), "CYCLE"), "OPEN", warehouseCode, locationCode, actor, compatString(payload.get("remark")));
        Long countId = jdbc.queryForObject("select id from wh_stock_count where tenant_id=? and count_no=?", Long.class, compatTenantId(), countNo);
        String sql = "select material_code, material_name, unit, location_code, batch_no, available_qty from inventory where tenant_id=? and warehouse_code=?" + (compatBlank(locationCode) ? "" : " and location_code=?") + " order by material_code, location_code, batch_no";
        List<Object> args = new ArrayList<>(); args.add(compatTenantId()); args.add(warehouseCode); if (!compatBlank(locationCode)) args.add(locationCode);
        for (Map<String, Object> row : jdbc.queryForList(sql, args.toArray())) {
            jdbc.update("insert into wh_stock_count_line(tenant_id, count_id, material_code, location_code, batch_no, book_qty) values(?,?,?,?,?,?)",
                    compatTenantId(), countId, row.get("material_code"), row.get("location_code"), row.get("batch_no"), row.get("available_qty"));
        }
        Map<String, Object> result = new LinkedHashMap<>(); result.put("id", countId); result.put("countNo", countNo); result.put("status", "OPEN"); result.put("lineCount", compatListCountLines(countId).size()); return result;
    }

    @Transactional
    public Map<String, Object> compatSubmitCount(long countId, Map<String, Object> payload, String actor) {
        Map<String, Object> header = compatOne("select id, count_no, status, warehouse_code from wh_stock_count where tenant_id=? and id=?", compatTenantId(), countId);
        if (header == null) throw new IllegalArgumentException("盘点单不存在");
        if ("SUBMITTED".equals(String.valueOf(header.get("status")))) throw new IllegalArgumentException("盘点单已提交");
        Object linesValue = payload.get("lines");
        if (linesValue instanceof List<?> lines) {
            for (Object value : lines) {
                if (value instanceof Map<?, ?> raw) {
                    Map<String, Object> line = new LinkedHashMap<>(); raw.forEach((key, val) -> line.put(String.valueOf(key), val));
                    compatSubmitCountLine(countId, line);
                }
            }
        }
        for (Map<String, Object> line : compatListCountLines(countId)) {
            if (line.get("count_qty") == null) continue;
            int difference = compatNumber(line.get("difference_qty"), 0);
            if (difference == 0) continue;
            Map<String, Object> adjustment = new LinkedHashMap<>();
            adjustment.put("transactionType", "ADJUSTMENT"); adjustment.put("adjustment", difference); adjustment.put("quantity", Math.abs(difference));
            adjustment.put("materialCode", line.get("material_code")); adjustment.put("warehouseCode", header.get("warehouse_code"));
            adjustment.put("locationCode", line.get("location_code")); adjustment.put("batchNo", line.get("batch_no")); adjustment.put("reasonCode", "COUNT");
            adjustment.put("sourceDocNo", header.get("count_no")); adjustment.put("remark", "盘点差异调整");
            compatPostTransaction(adjustment, actor);
        }
        jdbc.update("update wh_stock_count set status='SUBMITTED', submitted_at=current_timestamp where tenant_id=? and id=?", compatTenantId(), countId);
        return Map.of("id", countId, "status", "SUBMITTED", "countNo", header.get("count_no"));
    }

    public Map<String, Object> compatQuickCount(Map<String, Object> payload, String actor) {
        Map<String, Object> count = compatCreateCount(payload, actor);
        List<Map<String, Object>> lines = compatListCountLines(compatNumber(count.get("id"), 0));
        String materialCode = compatString(payload.get("materialCode"));
        String locationCode = compatString(payload.get("locationCode"));
        for (Map<String, Object> line : lines) {
            if ((compatBlank(materialCode) || materialCode.equals(String.valueOf(line.get("material_code")))) && (compatBlank(locationCode) || locationCode.equals(String.valueOf(line.get("location_code"))))) {
                line.put("countQty", payload.get("countQty"));
                compatSubmitCountLine(compatNumber(count.get("id"), 0), line);
            }
        }
        return compatSubmitCount(compatNumber(count.get("id"), 0), Map.of(), actor);
    }

    public Map<String, Object> compatParseBarcode(String barcode) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> record = compatOne("select id, barcode, barcode_type, material_code, batch_no, warehouse_code, location_code, status, source_doc_no from barcode where tenant_id=? and barcode=?", compatTenantId(), barcode);
        result.put("found", record != null);
        result.put("barcode", barcode);
        if (record != null) {
            result.putAll(record);
            result.put("inventory", compatListInventory(String.valueOf(record.get("material_code")), compatString(record.get("warehouse_code")), null));
            result.put("trace", compatTrace(compatString(record.get("material_code")), compatString(record.get("batch_no")), barcode));
        }
        return result;
    }

    public List<Map<String, Object>> compatTrace(String materialCode, String batchNo, String barcode) {
        if (!compatBlank(barcode)) {
            Map<String, Object> item = compatOne("select material_code, batch_no from barcode where tenant_id=? and barcode=?", compatTenantId(), barcode);
            if (item != null) { materialCode = compatString(item.get("material_code")); batchNo = compatString(item.get("batch_no")); }
        }
        if (compatBlank(materialCode)) return List.of();
        if (compatBlank(batchNo)) return jdbc.queryForList("select transaction_no, transaction_type, material_code, material_name, warehouse_code, location_code, batch_no, quantity, unit, operator_name, source_doc_no, document_no, created_at from material_transaction where tenant_id=? and material_code=? order by id", compatTenantId(), materialCode);
        return jdbc.queryForList("select transaction_no, transaction_type, material_code, material_name, warehouse_code, location_code, batch_no, quantity, unit, operator_name, source_doc_no, document_no, created_at from material_transaction where tenant_id=? and material_code=? and batch_no=? order by id", compatTenantId(), materialCode, batchNo);
    }

    @Transactional
    public Map<String, Object> compatCreateBarcode(Map<String, Object> payload) {
        String barcode = compatString(payload.get("barcode"));
        if (compatBlank(barcode)) barcode = compatGenerateBarcode(compatStringOr(payload.get("ruleCode"), "MATERIAL"));
        String materialCode = compatString(payload.get("materialCode"));
        jdbc.update("insert into barcode(tenant_id, barcode, barcode_type, material_code, batch_no, status, source_doc_no, warehouse_code, location_code) values(?,?,?,?,?,?,?,?,?)",
                compatTenantId(), barcode, compatStringOr(payload.get("barcodeType"), "MATERIAL"), materialCode, compatString(payload.get("batchNo")), "ACTIVE", compatString(payload.get("sourceDocNo")), compatString(payload.get("warehouseCode")), compatString(payload.get("locationCode")));
        return compatOne("select id, barcode, barcode_type, material_code, batch_no, warehouse_code, location_code, status, source_doc_no, printed_count, printed_at from barcode where tenant_id=? and barcode=?", compatTenantId(), barcode);
    }

    @Transactional
    public int compatPrintBarcodes(List<String> barcodes) {
        if (barcodes == null || barcodes.isEmpty()) throw new IllegalArgumentException("请选择要打印的条码");
        int total = 0;
        for (String barcode : barcodes) total += jdbc.update("update barcode set printed_count=printed_count+1, printed_at=current_timestamp where tenant_id=? and barcode=? and status='ACTIVE'", compatTenantId(), barcode);
        return total;
    }

    public void compatVoidBarcode(String barcode) {
        if (jdbc.update("update barcode set status='VOID', voided_at=current_timestamp where tenant_id=? and barcode=? and status<>'VOID'", compatTenantId(), barcode) == 0) throw new IllegalArgumentException("条码不存在或已作废");
    }

    public Map<String, Object> compatSaveWarehouse(Map<String, Object> payload) {
        String code = compatRequired(payload, "warehouseCode", "仓库编码"); String name = compatRequired(payload, "warehouseName", "仓库名称");
        Map<String, Object> existing = compatOne("select id from wh_warehouse where tenant_id=? and warehouse_code=?", compatTenantId(), code);
        if (existing == null) jdbc.update("insert into wh_warehouse(tenant_id, warehouse_code, warehouse_name, warehouse_type, owner_code, status, remark) values(?,?,?,?,?,?,?)", compatTenantId(), code, name, compatStringOr(payload.get("warehouseType"), "GENERAL"), compatString(payload.get("ownerCode")), compatStringOr(payload.get("status"), "ACTIVE"), compatString(payload.get("remark")));
        else jdbc.update("update wh_warehouse set warehouse_name=?, warehouse_type=?, owner_code=?, status=?, remark=?, updated_at=current_timestamp where tenant_id=? and warehouse_code=?", name, compatStringOr(payload.get("warehouseType"), "GENERAL"), compatString(payload.get("ownerCode")), compatStringOr(payload.get("status"), "ACTIVE"), compatString(payload.get("remark")), compatTenantId(), code);
        return compatOne("select id, warehouse_code, warehouse_name, warehouse_type, owner_code, status, remark from wh_warehouse where tenant_id=? and warehouse_code=?", compatTenantId(), code);
    }

    public Map<String, Object> compatSaveLocation(Map<String, Object> payload) {
        String warehouse = compatRequired(payload, "warehouseCode", "仓库编码"); String code = compatRequired(payload, "locationCode", "库位编码");
        Map<String, Object> existing = compatOne("select id from wh_location where tenant_id=? and warehouse_code=? and location_code=?", compatTenantId(), warehouse, code);
        if (existing == null) jdbc.update("insert into wh_location(tenant_id, warehouse_code, area_code, location_code, location_name, location_type, capacity_qty, status) values(?,?,?,?,?,?,?,?)", compatTenantId(), warehouse, compatString(payload.get("areaCode")), code, compatString(payload.get("locationName")), compatStringOr(payload.get("locationType"), "BIN"), compatNumber(payload.get("capacityQty"), 0), compatStringOr(payload.get("status"), "AVAILABLE"));
        else jdbc.update("update wh_location set area_code=?, location_name=?, location_type=?, capacity_qty=?, status=? where tenant_id=? and warehouse_code=? and location_code=?", compatString(payload.get("areaCode")), compatString(payload.get("locationName")), compatStringOr(payload.get("locationType"), "BIN"), compatNumber(payload.get("capacityQty"), 0), compatStringOr(payload.get("status"), "AVAILABLE"), compatTenantId(), warehouse, code);
        return compatOne("select id, warehouse_code, area_code, location_code, location_name, location_type, capacity_qty, status from wh_location where tenant_id=? and warehouse_code=? and location_code=?", compatTenantId(), warehouse, code);
    }

    public Map<String, Object> compatSaveArea(Map<String, Object> payload) {
        String warehouse = compatRequired(payload, "warehouseCode", "仓库编码"); String code = compatRequired(payload, "areaCode", "库区编码"); String name = compatRequired(payload, "areaName", "库区名称");
        Map<String, Object> existing = compatOne("select id from wh_storage_area where tenant_id=? and warehouse_code=? and area_code=?", compatTenantId(), warehouse, code);
        if (existing == null) jdbc.update("insert into wh_storage_area(tenant_id, warehouse_code, area_code, area_name, area_type, status, remark) values(?,?,?,?,?,?,?)", compatTenantId(), warehouse, code, name, compatStringOr(payload.get("areaType"), "NORMAL"), compatStringOr(payload.get("status"), "ACTIVE"), compatString(payload.get("remark")));
        else jdbc.update("update wh_storage_area set area_name=?, area_type=?, status=?, remark=? where tenant_id=? and warehouse_code=? and area_code=?", name, compatStringOr(payload.get("areaType"), "NORMAL"), compatStringOr(payload.get("status"), "ACTIVE"), compatString(payload.get("remark")), compatTenantId(), warehouse, code);
        return compatOne("select id, warehouse_code, area_code, area_name, area_type, status, remark from wh_storage_area where tenant_id=? and warehouse_code=? and area_code=?", compatTenantId(), warehouse, code);
    }

    public Map<String, Object> compatSaveMaterial(Map<String, Object> payload) {
        String code = compatRequired(payload, "materialCode", "物料编码"); String name = compatRequired(payload, "materialName", "物料名称");
        Map<String, Object> existing = compatOne("select id from wh_material where tenant_id=? and material_code=?", compatTenantId(), code);
        Object[] args = {name, compatStringOr(payload.get("materialType"), "RAW"), compatStringOr(payload.get("unit"), "件"), compatFlag(payload.get("lotControl"), true), compatFlag(payload.get("serialControl"), false), compatNumber(payload.get("shelfLifeDays"), 0), compatNumber(payload.get("safetyStock"), 0), compatStringOr(payload.get("status"), "ACTIVE"), compatString(payload.get("remark"))};
        if (existing == null) jdbc.update("insert into wh_material(tenant_id, material_code, material_name, material_type, unit, lot_control, serial_control, shelf_life_days, safety_stock, status, remark) values(?,?,?,?,?,?,?,?,?,?,?)", compatTenantId(), code, args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
        else jdbc.update("update wh_material set material_name=?, material_type=?, unit=?, lot_control=?, serial_control=?, shelf_life_days=?, safety_stock=?, status=?, remark=?, updated_at=current_timestamp where tenant_id=? and material_code=?", args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], compatTenantId(), code);
        return compatOne("select id, material_code, material_name, material_type, unit, lot_control, serial_control, shelf_life_days, safety_stock, status, remark from wh_material where tenant_id=? and material_code=?", compatTenantId(), code);
    }

    public Map<String, Object> compatSaveBarcodeRule(Map<String, Object> payload) {
        String code = compatRequired(payload, "ruleCode", "规则编码"); String name = compatRequired(payload, "ruleName", "规则名称");
        Map<String, Object> existing = compatOne("select id from wh_barcode_rule where tenant_id=? and rule_code=?", compatTenantId(), code);
        if (existing == null) jdbc.update("insert into wh_barcode_rule(tenant_id, rule_code, rule_name, barcode_type, prefix, sequence_no, status) values(?,?,?,?,?,?,?)", compatTenantId(), code, name, compatStringOr(payload.get("barcodeType"), "MATERIAL"), compatStringOr(payload.get("prefix"), "PDA"), compatNumber(payload.get("sequenceNo"), 0), compatStringOr(payload.get("status"), "ACTIVE"));
        else jdbc.update("update wh_barcode_rule set rule_name=?, barcode_type=?, prefix=?, status=? where tenant_id=? and rule_code=?", name, compatStringOr(payload.get("barcodeType"), "MATERIAL"), compatStringOr(payload.get("prefix"), "PDA"), compatStringOr(payload.get("status"), "ACTIVE"), compatTenantId(), code);
        return compatOne("select id, rule_code, rule_name, barcode_type, prefix, sequence_no, status from wh_barcode_rule where tenant_id=? and rule_code=?", compatTenantId(), code);
    }

    private void compatSubmitCountLine(long countId, Map<String, Object> line) {
        long id = compatNumber(line.get("id"), 0);
        int countQty = compatNumber(line.get("countQty"), compatNumber(line.get("count_qty"), -1));
        if (countQty < 0) throw new IllegalArgumentException("盘点数量不能小于 0");
        if (id > 0) jdbc.update("update wh_stock_count_line set count_qty=?, difference_qty=? - book_qty, reason_code=? where tenant_id=? and count_id=? and id=?", countQty, countQty, compatString(line.get("reasonCode")), compatTenantId(), countId, id);
        else {
            String materialCode = compatRequired(line, "materialCode", "盘点物料编码"); String locationCode = compatRequired(line, "locationCode", "盘点库位"); String batchNo = compatString(line.get("batchNo"));
            jdbc.update("update wh_stock_count_line set count_qty=?, difference_qty=? - book_qty, reason_code=? where tenant_id=? and count_id=? and material_code=? and location_code=? and coalesce(batch_no,'')=coalesce(?, '')", countQty, countQty, compatString(line.get("reasonCode")), compatTenantId(), countId, materialCode, locationCode, batchNo);
        }
    }

    private void compatChangeStock(String warehouseCode, String locationCode, String materialCode, String materialName, String batchNo, String unit, int delta, Map<String, Object> payload) {
        if (compatBlank(locationCode)) throw new IllegalArgumentException("库位不能为空");
        String where = "tenant_id=? and material_code=? and warehouse_code=? and location_code=? and coalesce(batch_no,'')=coalesce(?, '')";
        Map<String, Object> row = compatOne("select id, available_qty, stock_status from inventory where " + where + " for update", compatTenantId(), materialCode, warehouseCode, locationCode, batchNo);
        if (row == null) {
            if (delta < 0) throw new IllegalArgumentException("可用库存不足或库存记录不存在");
            jdbc.update("insert into inventory(tenant_id, material_code, material_name, warehouse_code, location_code, batch_no, available_qty, locked_qty, reserved_qty, in_transit_qty, unit, safety_stock, stock_status, expiry_date, version_no) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    compatTenantId(), materialCode, materialName, warehouseCode, locationCode, batchNo, delta, 0, 0, 0, unit, compatMaterialSafetyStock(materialCode), compatStringOr(payload.get("stockStatus"), "AVAILABLE"), compatDate(payload.get("expiryDate")), 1);
            return;
        }
        int current = compatNumber(row.get("available_qty"), 0);
        String stockStatus = compatString(row.get("stock_status"));
        if (delta < 0 && ("FROZEN".equals(stockStatus) || "HOLD".equals(stockStatus)) && !compatFlag(payload.get("force"), false)) {
            throw new IllegalArgumentException("库存状态为" + stockStatus + "，不可直接出库");
        }
        if (delta < 0 && !compatFlag(payload.get("force"), false) && !compatBlank(batchNo)) {
            Map<String, Object> batch = compatOne("select quality_status, batch_status from wh_batch where tenant_id=? and material_code=? and batch_no=?", compatTenantId(), materialCode, batchNo);
            String qualityStatus = batch == null ? "PENDING" : compatStringOr(batch.get("quality_status"), "PENDING");
            String batchStatus = batch == null ? "ACTIVE" : compatStringOr(batch.get("batch_status"), "ACTIVE");
            if (!"PASSED".equalsIgnoreCase(qualityStatus)) throw new IllegalArgumentException("批次质量状态为" + qualityStatus + "，检验放行前不可出库");
            if (!"ACTIVE".equalsIgnoreCase(batchStatus)) throw new IllegalArgumentException("批次状态为" + batchStatus + "，不可出库");
        }
        if (current + delta < 0) throw new IllegalArgumentException("可用库存不足");
        int updated = jdbc.update("update inventory set available_qty=available_qty+?, version_no=version_no+1, stock_status=?, expiry_date=coalesce(?, expiry_date), updated_at=current_timestamp where " + where + " and available_qty+? >= 0", delta, compatStringOr(payload.get("stockStatus"), "AVAILABLE"), compatDate(payload.get("expiryDate")), compatTenantId(), materialCode, warehouseCode, locationCode, batchNo, delta);
        if (updated == 0) throw new IllegalArgumentException("库存已被其他操作修改，请刷新后重试");
    }

    private void compatRecordSimpleTransaction(String type, String materialCode, String warehouseCode, String locationCode, String batchNo, int quantity, String actor, String reason) {
        jdbc.update("insert into material_transaction(tenant_id, transaction_no, transaction_type, material_code, warehouse_code, location_code, batch_no, quantity, unit, operator_name, reason_code, status) values(?,?,?,?,?,?,?,?,?,?,?,?)", compatTenantId(), "TX-" + TenantContext.require().tenantCode() + "-" + UUID.randomUUID(), type, materialCode, warehouseCode, locationCode, batchNo, quantity, compatMaterialUnit(materialCode), actor, reason, "COMPLETED");
    }

    private void compatEnsureMaterial(String code, String name, String unit, Map<String, Object> payload) {
        if (compatOne("select id from wh_material where tenant_id=? and material_code=?", compatTenantId(), code) == null) compatSaveMaterial(new LinkedHashMap<>(Map.of("materialCode", code, "materialName", name, "unit", unit, "materialType", compatStringOr(payload.get("materialType"), "RAW"), "safetyStock", compatNumber(payload.get("safetyStock"), 0))));
    }

    private void compatEnsureBatch(String materialCode, String batchNo, Map<String, Object> payload) {
        if (compatOne("select id from wh_batch where tenant_id=? and material_code=? and batch_no=?", compatTenantId(), materialCode, batchNo) == null) jdbc.update("insert into wh_batch(tenant_id, material_code, batch_no, production_date, expiry_date, supplier_code, quality_status, batch_status, remark) values(?,?,?,?,?,?,?,?,?)", compatTenantId(), materialCode, batchNo, compatDate(payload.get("productionDate")), compatDate(payload.get("expiryDate")), compatString(payload.get("supplierCode")), compatStringOr(payload.get("qualityStatus"), "PENDING"), "ACTIVE", compatString(payload.get("remark")));
    }

    private String compatGenerateBarcode(String ruleCode) {
        Map<String, Object> rule = compatOne("select id, prefix, sequence_no, barcode_type from wh_barcode_rule where tenant_id=? and rule_code=? and status='ACTIVE'", compatTenantId(), ruleCode);
        String prefix = rule == null ? "PDA" : compatStringOr(rule.get("prefix"), "PDA");
        long sequence = rule == null ? 0 : compatNumber(rule.get("sequence_no"), 0);
        if (rule != null) jdbc.update("update wh_barcode_rule set sequence_no=sequence_no+1 where tenant_id=? and id=?", compatTenantId(), rule.get("id"));
        return prefix + "-" + String.format("%08d", sequence + 1);
    }

    private String compatMaterialName(String code) { Map<String, Object> row = compatOne("select material_name from wh_material where tenant_id=? and material_code=?", compatTenantId(), code); return row == null ? code : compatStringOr(row.get("material_name"), code); }
    private String compatMaterialUnit(String code) { Map<String, Object> row = compatOne("select unit from wh_material where tenant_id=? and material_code=?", compatTenantId(), code); return row == null ? "件" : compatStringOr(row.get("unit"), "件"); }
    private int compatMaterialSafetyStock(String code) { Map<String, Object> row = compatOne("select safety_stock from wh_material where tenant_id=? and material_code=?", compatTenantId(), code); return row == null ? 0 : compatNumber(row.get("safety_stock"), 0); }
    private Map<String, Object> compatOne(String sql, Object... args) { List<Map<String, Object>> rows = jdbc.queryForList(sql, args); return rows.isEmpty() ? null : rows.get(0); }
    private boolean compatColumnExists(String table, String column) {
        Integer count = jdbc.queryForObject("select count(*) from information_schema.columns where lower(table_name)=lower(?) and lower(column_name)=lower(?)", Integer.class, table, column);
        return count != null && count > 0;
    }
    private Object compatScalar(String sql, Object... args) { return jdbc.queryForObject(sql, Object.class, args); }
    private long compatTenantId() { return TenantContext.require().tenantId(); }
    private static String compatRequired(Map<String, Object> payload, String key, String label) { String value = compatString(payload.get(key)); if (compatBlank(value)) throw new IllegalArgumentException(label + "不能为空"); return value; }
    private static int compatPositive(Object value, String label) { int number = compatNumber(value, 0); if (number <= 0) throw new IllegalArgumentException(label + "必须大于 0"); return number; }
    private static int compatNumber(Object value, int fallback) { if (value == null) return fallback; try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; } }
    private static boolean compatFlag(Object value, boolean fallback) { if (value == null) return fallback; return Boolean.parseBoolean(String.valueOf(value)) || "1".equals(String.valueOf(value)); }
    private static String compatString(Object value) { return value == null ? null : String.valueOf(value); }
    private static String compatStringOr(Object value, String fallback) { String valueText = compatString(value); return compatBlank(valueText) ? fallback : valueText; }
    private static boolean compatBlank(String value) { return value == null || value.isBlank(); }
    private static String compatFromLocationOrDefault(String from, String fallback) { return compatBlank(from) ? fallback : from; }
    private static java.sql.Date compatDate(Object value) { if (value == null || String.valueOf(value).isBlank()) return null; try { return java.sql.Date.valueOf(String.valueOf(value)); } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd"); } }
}
