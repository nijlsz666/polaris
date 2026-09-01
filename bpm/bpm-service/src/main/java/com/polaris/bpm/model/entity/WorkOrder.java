package com.polaris.bpm.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Read/write projection of the manufacturing service's shared work_order table. */
@TableName("work_order")
public class WorkOrder {
    @TableId
    private Long id;
    @TableField("order_no")
    private String orderNo;
    @TableField("product_code")
    private String productCode;
    @TableField("product_name")
    private String productName;
    @TableField("plan_qty")
    private Integer planQty;
    private String status;
    private Integer deleted;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String value) { this.orderNo = value; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String value) { this.productCode = value; }
    public String getProductName() { return productName; }
    public void setProductName(String value) { this.productName = value; }
    public Integer getPlanQty() { return planQty; }
    public void setPlanQty(Integer value) { this.planQty = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer value) { this.deleted = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
}
