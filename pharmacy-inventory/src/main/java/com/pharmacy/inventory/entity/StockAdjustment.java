package com.pharmacy.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_adjustments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private MedicineBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false)
    private AdjustmentType adjustmentType;

    @Column(name = "quantity_adjusted", nullable = false)
    private int quantityAdjusted;

    @Column(name = "reason")
    private String reason;

    @Column(name = "adjusted_by")
    private String adjustedBy;

    @Builder.Default
    @Column(name = "adjusted_at", nullable = false)
    private LocalDateTime adjustedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (adjustedAt == null) {
            adjustedAt = LocalDateTime.now();
        }
    }
}
