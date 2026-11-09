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
public class RevenueTrendDTO {
    private String date;
    private BigDecimal netRevenue;
    private BigDecimal costOfGoodsSold;
    private BigDecimal grossProfit;
    private int salesCount;
}
