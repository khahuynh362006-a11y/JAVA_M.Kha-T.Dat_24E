package com.khadat.cinema.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Scalar FK columns own writes; associations are lazy read-only views. */
@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(columnNames = {"id", "showtime_id", "room_id"}))
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
    private Long id;

    @Column(name = "code", nullable = false, length = 40, updatable = false)
    private String code;

    @Column(name = "showtime_id", nullable = false, columnDefinition = "bigint unsigned", updatable = false)
    private Long showtimeId;

    @Column(name = "room_id", nullable = false, columnDefinition = "bigint unsigned", updatable = false)
    private Long roomId;

    @Column(name = "created_by", nullable = false, columnDefinition = "bigint unsigned", updatable = false)
    private Long createdBy;

    @Column(name = "idempotency_key", length = 36, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64, updatable = false)
    private String requestHash;

    @Column(name = "created_by_name_snapshot", nullable = false, length = 100, updatable = false)
    private String createdByNameSnapshot;

    @Column(name = "movie_title_snapshot", nullable = false, length = 200, updatable = false)
    private String movieTitleSnapshot;

    @Column(name = "room_code_snapshot", nullable = false, length = 32, updatable = false)
    private String roomCodeSnapshot;

    @Column(name = "price_version_snapshot", nullable = false, columnDefinition = "bigint unsigned", updatable = false)
    private Long priceVersionSnapshot;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 0, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "age_checked", nullable = false, updatable = false)
    private Boolean ageChecked;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by", columnDefinition = "bigint unsigned")
    private Long cancelledBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({@JoinColumn(name = "showtime_id", referencedColumnName = "id", insertable = false, updatable = false), @JoinColumn(name = "room_id", referencedColumnName = "room_id", insertable = false, updatable = false)})
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id", insertable = false, updatable = false)
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by", referencedColumnName = "id", insertable = false, updatable = false)
    private User cancelledByUser;

    public Booking() { }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey=value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { requestHash=value; }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getShowtimeId() { return showtimeId; }
    public void setShowtimeId(Long showtimeId) { this.showtimeId = showtimeId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getCreatedByNameSnapshot() { return createdByNameSnapshot; }
    public void setCreatedByNameSnapshot(String createdByNameSnapshot) { this.createdByNameSnapshot = createdByNameSnapshot; }
    public String getMovieTitleSnapshot() { return movieTitleSnapshot; }
    public void setMovieTitleSnapshot(String movieTitleSnapshot) { this.movieTitleSnapshot = movieTitleSnapshot; }
    public String getRoomCodeSnapshot() { return roomCodeSnapshot; }
    public void setRoomCodeSnapshot(String roomCodeSnapshot) { this.roomCodeSnapshot = roomCodeSnapshot; }
    public Long getPriceVersionSnapshot() { return priceVersionSnapshot; }
    public void setPriceVersionSnapshot(Long priceVersionSnapshot) { this.priceVersionSnapshot = priceVersionSnapshot; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public Boolean getAgeChecked() { return ageChecked; }
    public void setAgeChecked(Boolean ageChecked) { this.ageChecked = ageChecked; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public Long getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(Long cancelledBy) { this.cancelledBy = cancelledBy; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Showtime getShowtime() { return showtime; }
    public User getCreatedByUser() { return createdByUser; }
    public User getCancelledByUser() { return cancelledByUser; }
}
