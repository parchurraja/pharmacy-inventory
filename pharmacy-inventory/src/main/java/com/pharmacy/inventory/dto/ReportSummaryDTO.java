package com.pharmacy.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportSummaryDTO {
    private BigDecimal netRevenue;
    private BigDecimal costOfGoodsSold;
    private BigDecimal grossProfit;
    private double grossMarginPercentage;
    private int totalUnitsSold;
    private long totalSalesCount;
    private long totalReturnsCount;
    private BigDecimal totalRefundAmount;
    private List<TopMedicineReportDTO> topMedicines;
    private List<RevenueTrendDTO> revenueTrends;
}
