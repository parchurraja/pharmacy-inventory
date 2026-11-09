package com.pharmacy.inventory.service;

import com.pharmacy.inventory.dto.ReportSummaryDTO;
import com.pharmacy.inventory.dto.RevenueTrendDTO;
import com.pharmacy.inventory.dto.TopMedicineReportDTO;
import com.pharmacy.inventory.entity.Sale;
import com.pharmacy.inventory.entity.SaleItem;
import com.pharmacy.inventory.entity.SaleStatus;
import com.pharmacy.inventory.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final SaleRepository saleRepository;

    @Transactional(readOnly = true)
    public ReportSummaryDTO getSummary(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Computing financial & profitability report between {} and {}", startDate, endDate);

        List<Sale> sales = saleRepository.findSalesForReporting(startDate, endDate);

        BigDecimal netRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal costOfGoodsSold = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRefundAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        int totalUnitsSold = 0;
        long totalReturnsCount = 0;

        // Groupings for top medicines and trends
        Map<Long, TopMedicineAccumulator> medicineMap = new HashMap<>();
        Map<String, TrendAccumulator> trendMap = new TreeMap<>();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (Sale sale : sales) {
            if (sale.getStatus() == SaleStatus.PARTIALLY_REFUNDED || sale.getStatus() == SaleStatus.REFUNDED) {
                totalReturnsCount++;
            }

            String dateKey = sale.getSaleDate() != null
                    ? sale.getSaleDate().format(dateFormatter)
                    : LocalDate.now().format(dateFormatter);

            TrendAccumulator trendAcc = trendMap.computeIfAbsent(dateKey, k -> new TrendAccumulator(k));
            trendAcc.salesCount++;

            if (sale.getItems() != null) {
                for (SaleItem item : sale.getItems()) {
                    int effectiveQty = item.getQuantity() - item.getReturnedQuantity();
                    int returnedQty = item.getReturnedQuantity();

                    BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                    BigDecimal saleTimeCost = item.getCostPrice() != null ? item.getCostPrice() : BigDecimal.ZERO;

                    // Net revenue from retained units
                    BigDecimal itemRevenue = unitPrice.multiply(BigDecimal.valueOf(effectiveQty))
                            .setScale(2, RoundingMode.HALF_UP);

                    // COGS from retained units using FROZEN sale-time cost price
                    BigDecimal itemCogs = saleTimeCost.multiply(BigDecimal.valueOf(effectiveQty))
                            .setScale(2, RoundingMode.HALF_UP);

                    // Refund amount from returned units
                    BigDecimal itemRefund = unitPrice.multiply(BigDecimal.valueOf(returnedQty))
                            .setScale(2, RoundingMode.HALF_UP);

                    netRevenue = netRevenue.add(itemRevenue);
                    costOfGoodsSold = costOfGoodsSold.add(itemCogs);
                    totalRefundAmount = totalRefundAmount.add(itemRefund);
                    totalUnitsSold += effectiveQty;

                    // Accumulate trends
                    trendAcc.netRevenue = trendAcc.netRevenue.add(itemRevenue);
                    trendAcc.costOfGoodsSold = trendAcc.costOfGoodsSold.add(itemCogs);

                    // Accumulate per-medicine metrics
                    if (item.getMedicine() != null) {
                        Long medId = item.getMedicine().getId();
                        TopMedicineAccumulator medAcc = medicineMap.computeIfAbsent(medId, k -> new TopMedicineAccumulator(
                                medId,
                                item.getMedicine().getName(),
                                item.getMedicine().getCategory()
                        ));
                        medAcc.netUnitsSold += effectiveQty;
                        medAcc.netRevenue = medAcc.netRevenue.add(itemRevenue);
                        medAcc.costOfGoodsSold = medAcc.costOfGoodsSold.add(itemCogs);
                    }
                }
            }
        }

        // Gross Profit = Net Revenue - COGS
        BigDecimal grossProfit = netRevenue.subtract(costOfGoodsSold).setScale(2, RoundingMode.HALF_UP);

        // Gross Margin = (Gross Profit / Net Revenue) * 100 (Safe division against zero revenue)
        double grossMarginPercentage = 0.0;
        if (netRevenue.compareTo(BigDecimal.ZERO) > 0) {
            grossMarginPercentage = grossProfit
                    .divide(netRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        // Build top medicines list sorted by units sold DESC
        List<TopMedicineReportDTO> topMedicines = new ArrayList<>();
        for (TopMedicineAccumulator medAcc : medicineMap.values()) {
            BigDecimal medProfit = medAcc.netRevenue.subtract(medAcc.costOfGoodsSold).setScale(2, RoundingMode.HALF_UP);
            double medMargin = 0.0;
            if (medAcc.netRevenue.compareTo(BigDecimal.ZERO) > 0) {
                medMargin = medProfit.divide(medAcc.netRevenue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }

            topMedicines.add(TopMedicineReportDTO.builder()
                    .medicineId(medAcc.medicineId)
                    .medicineName(medAcc.medicineName)
                    .category(medAcc.category)
                    .netUnitsSold(medAcc.netUnitsSold)
                    .netRevenue(medAcc.netRevenue)
                    .costOfGoodsSold(medAcc.costOfGoodsSold)
                    .grossProfit(medProfit)
                    .grossMarginPercentage(medMargin)
                    .build());
        }

        topMedicines.sort((a, b) -> Integer.compare(b.getNetUnitsSold(), a.getNetUnitsSold()));
        if (topMedicines.size() > 5) {
            topMedicines = topMedicines.subList(0, 5);
        }

        // Build revenue trends list
        List<RevenueTrendDTO> trends = new ArrayList<>();
        for (TrendAccumulator t : trendMap.values()) {
            BigDecimal trendProfit = t.netRevenue.subtract(t.costOfGoodsSold).setScale(2, RoundingMode.HALF_UP);
            trends.add(RevenueTrendDTO.builder()
                    .date(t.date)
                    .netRevenue(t.netRevenue)
                    .costOfGoodsSold(t.costOfGoodsSold)
                    .grossProfit(trendProfit)
                    .salesCount(t.salesCount)
                    .build());
        }

        return ReportSummaryDTO.builder()
                .netRevenue(netRevenue)
                .costOfGoodsSold(costOfGoodsSold)
                .grossProfit(grossProfit)
                .grossMarginPercentage(grossMarginPercentage)
                .totalUnitsSold(totalUnitsSold)
                .totalSalesCount(sales.size())
                .totalReturnsCount(totalReturnsCount)
                .totalRefundAmount(totalRefundAmount)
                .topMedicines(topMedicines)
                .revenueTrends(trends)
                .build();
    }

    private static class TopMedicineAccumulator {
        Long medicineId;
        String medicineName;
        String category;
        int netUnitsSold = 0;
        BigDecimal netRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal costOfGoodsSold = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        TopMedicineAccumulator(Long medicineId, String medicineName, String category) {
            this.medicineId = medicineId;
            this.medicineName = medicineName;
            this.category = category;
        }
    }

    private static class TrendAccumulator {
        String date;
        BigDecimal netRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal costOfGoodsSold = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int salesCount = 0;

        TrendAccumulator(String date) {
            this.date = date;
        }
    }
}
