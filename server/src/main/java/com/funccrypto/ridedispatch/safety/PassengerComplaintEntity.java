package com.funccrypto.ridedispatch.safety;

import java.time.Instant;

import com.funccrypto.ridedispatch.order.RideOrderEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "passenger_complaint")
public class PassengerComplaintEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "complaint_no", nullable = false, unique = true, length = 40) private String complaintNo;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id") private RideOrderEntity order;

    @Column(name = "order_no", length = 40) private String orderNo;

    @Column(nullable = false, length = 40) private String category;

    @Column(nullable = false, length = 1000) private String description;

    @Column(name = "contact_mobile", length = 30) private String contactMobile;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ComplaintStatus status;

    @Column(name = "handle_note", length = 500) private String handleNote;

    @Column(name = "handled_by") private Long handledBy;

    @Column(name = "handled_at") private Instant handledAt;

    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PassengerComplaintEntity() {
    }

    public PassengerComplaintEntity(
            String complaintNo,
            RideOrderEntity order,
            String orderNo,
            String category,
            String description,
            String contactMobile,
            Instant now) {
        this.complaintNo = complaintNo;
        this.order = order;
        this.orderNo = orderNo;
        this.category = category;
        this.description = description;
        this.contactMobile = contactMobile;
        this.status = ComplaintStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void applyHandle(ComplaintStatus nextStatus, String note, Long operatorId, Instant now) {
        this.status = nextStatus;
        this.handleNote = note;
        this.handledBy = operatorId;
        this.handledAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getComplaintNo() { return complaintNo; }
    public RideOrderEntity getOrder() { return order; }
    public String getOrderNo() { return orderNo; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getContactMobile() { return contactMobile; }
    public ComplaintStatus getStatus() { return status; }
    public String getHandleNote() { return handleNote; }
    public Long getHandledBy() { return handledBy; }
    public Instant getHandledAt() { return handledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
