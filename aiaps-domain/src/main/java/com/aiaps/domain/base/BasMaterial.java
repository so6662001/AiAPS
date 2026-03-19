package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("bas_material")
public class BasMaterial implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "PrdtID", type = IdType.AUTO)
    private Long prdtId;

    @TableField("PrdtNo")
    private String prdtNo;

    @TableField("PrdtName")
    private String prdtName;

    @TableField("category_code")
    private String categoryCode;

    @TableField("category_name")
    private String categoryName;

    @TableField("material_type")
    private String materialType;

    @TableField("unit_code")
    private String unitCode;

    @TableField("aux_unit_code")
    private String auxUnitCode;

    @TableField("unit_convert_rate")
    private BigDecimal unitConvertRate;

    @TableField("spec_desc")
    private String specDesc;

    @TableField("thickness")
    private BigDecimal thickness;

    @TableField("width")
    private BigDecimal width;

    @TableField("outer_diameter")
    private BigDecimal outerDiameter;

    @TableField("height")
    private BigDecimal height;

    @TableField("side_a")
    private BigDecimal sideA;

    @TableField("side_b")
    private BigDecimal sideB;

    @TableField("corner_radius")
    private BigDecimal cornerRadius;

    @TableField("flange_width")
    private BigDecimal flangeWidth;

    @TableField("web_thickness")
    private BigDecimal webThickness;

    @TableField("flange_thickness")
    private BigDecimal flangeThickness;

    @TableField("surface_treatment")
    private String surfaceTreatment;

    @TableField("theory_weight_per_m")
    private BigDecimal theoryWeightPerM;

    @TableField("theory_weight_per_pc")
    private BigDecimal theoryWeightPerPc;

    @TableField("lot_policy")
    private String lotPolicy;

    @TableField("fixed_lot_qty")
    private BigDecimal fixedLotQty;

    @TableField("min_order_qty")
    private BigDecimal minOrderQty;

    @TableField("lot_multiple")
    private BigDecimal lotMultiple;

    @TableField("lead_time_days")
    private Integer leadTimeDays;

    @TableField("safety_stock_qty")
    private BigDecimal safetyStockQty;

    @TableField("safety_lead_days")
    private Integer safetyLeadDays;

    @TableField("procurement_type")
    private String procurementType;

    @TableField("length_in_material")
    private Boolean lengthInMaterial;

    @TableField("default_length")
    private BigDecimal defaultLength;

    @TableField("enable_item_barcode")
    private Boolean enableItemBarcode;

    @TableField("is_active")
    private Boolean isActive;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private Date createdTime;

    @TableField(value = "updated_by", fill = FieldFill.UPDATE)
    private String updatedBy;

    @TableField(value = "updated_time", fill = FieldFill.UPDATE)
    private Date updatedTime;
}
