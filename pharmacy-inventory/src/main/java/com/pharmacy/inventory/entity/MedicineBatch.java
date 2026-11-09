package com.pharmacy.inventory.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "medicine_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicineBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Batch number for traceability
    @Column(name = "batch_number", nullable = false)
    private String batchNumber;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", precision = 10, scale = 2)
    private BigDecimal sellingPrice;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    // Version field for optimistic locking protection alongside pessimistic write locking
    @Version
    @Column(name = "version")
    private Long version;

    // Many batches -> one medicine
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicine_id", nullable = false)
    @JsonIgnore
    private Medicine medicine;

    // Helper: is this batch expired?
    public boolean isExpired() {
        return expiryDate != null && (LocalDate.now().isAfter(expiryDate) || LocalDate.now().isEqual(expiryDate));
    }
}
