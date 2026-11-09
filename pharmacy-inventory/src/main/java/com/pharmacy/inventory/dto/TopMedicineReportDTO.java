package com.pharmacy.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopMedicineReportDTO {
    private Long medicineId;
    private String medicineName;
    private String category;
    private int netUnitsSold;
    private BigDecimal netRevenue;
    private BigDecimal costOfGoodsSold;
    private BigDecimal grossProfit;
    private double grossMarginPercentage;
}
