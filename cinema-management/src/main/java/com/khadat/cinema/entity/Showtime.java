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
@Table(name = "showtimes", uniqueConstraints = @UniqueConstraint(columnNames = {"id", "room_id"}))
public class Showtime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
    private Long id;

    @Column(name = "movie_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long movieId;

    @Column(name = "room_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long roomId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "duration_minutes_snapshot", nullable = false, columnDefinition = "smallint unsigned")
    private Integer durationMinutesSnapshot;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "minimum_age_snapshot", nullable = false, columnDefinition = "tinyint unsigned")
    private Integer minimumAgeSnapshot;

    @Column(name = "starts_at", nullable = false, columnDefinition = "datetime")
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false, columnDefinition = "datetime")
    private LocalDateTime endsAt;

    @Column(name = "standard_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal standardPrice;

    @Column(name = "vip_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal vipPrice;

    @Column(name = "price_version", nullable = false, columnDefinition = "bigint unsigned")
    private Long priceVersion;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "capacity_snapshot", columnDefinition = "smallint unsigned")
    private Integer capacitySnapshot;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by", columnDefinition = "bigint unsigned")
    private Long cancelledBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by", referencedColumnName = "id", insertable = false, updatable = false)
    private User cancelledByUser;

    public Showtime() { }

    public Long getId() { return id; }
    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Integer getDurationMinutesSnapshot() { return durationMinutesSnapshot; }
    public void setDurationMinutesSnapshot(Integer durationMinutesSnapshot) { this.durationMinutesSnapshot = durationMinutesSnapshot; }
    public Integer getMinimumAgeSnapshot() { return minimumAgeSnapshot; }
    public void setMinimumAgeSnapshot(Integer minimumAgeSnapshot) { this.minimumAgeSnapshot = minimumAgeSnapshot; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
    public BigDecimal getStandardPrice() { return standardPrice; }
    public void setStandardPrice(BigDecimal standardPrice) { this.standardPrice = standardPrice; }
    public BigDecimal getVipPrice() { return vipPrice; }
    public void setVipPrice(BigDecimal vipPrice) { this.vipPrice = vipPrice; }
    public Long getPriceVersion() { return priceVersion; }
    public void setPriceVersion(Long priceVersion) { this.priceVersion = priceVersion; }
    public Integer getCapacitySnapshot() { return capacitySnapshot; }
    public void setCapacitySnapshot(Integer capacitySnapshot) { this.capacitySnapshot = capacitySnapshot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public Long getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(Long cancelledBy) { this.cancelledBy = cancelledBy; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Movie getMovie() { return movie; }
    public Room getRoom() { return room; }
    public User getCancelledByUser() { return cancelledByUser; }
}
