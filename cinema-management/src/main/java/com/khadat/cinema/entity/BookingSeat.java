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
@Table(name = "booking_seats")
public class BookingSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
    private Long id;

    @Column(name = "booking_id", nullable = false, columnDefinition = "bigint unsigned", updatable = false)
    private Long bookingId;

    @Column(name = "showtime_id", nullable = false, columnDefinition = "bigint unsigned", updatable = false)
    private Long showtimeId;

    @Column(name = "room_id", nullable = false, columnDefinition = "bigint unsigned", updatable = false)
    private Long roomId;

    @Column(name = "seat_id", nullable = false, columnDefinition = "bigint unsigned", updatable = false)
    private Long seatId;

    @Column(name = "seat_code_snapshot", nullable = false, length = 16, updatable = false)
    private String seatCodeSnapshot;

    @Column(name = "seat_type_snapshot", nullable = false, length = 16, updatable = false)
    private String seatTypeSnapshot;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 0, updatable = false)
    private BigDecimal unitPrice;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "booking_status", nullable = false, length = 16, insertable = false, updatable = false)
    private String bookingStatus;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "active_seat_id", columnDefinition = "bigint unsigned", insertable = false, updatable = false)
    private Long activeSeatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({@JoinColumn(name = "booking_id", referencedColumnName = "id", insertable = false, updatable = false), @JoinColumn(name = "showtime_id", referencedColumnName = "showtime_id", insertable = false, updatable = false), @JoinColumn(name = "room_id", referencedColumnName = "room_id", insertable = false, updatable = false)})
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({@JoinColumn(name = "seat_id", referencedColumnName = "id", insertable = false, updatable = false), @JoinColumn(name = "room_id", referencedColumnName = "room_id", insertable = false, updatable = false)})
    private Seat seat;

    public BookingSeat() { }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Long getShowtimeId() { return showtimeId; }
    public void setShowtimeId(Long showtimeId) { this.showtimeId = showtimeId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    public String getSeatCodeSnapshot() { return seatCodeSnapshot; }
    public void setSeatCodeSnapshot(String seatCodeSnapshot) { this.seatCodeSnapshot = seatCodeSnapshot; }
    public String getSeatTypeSnapshot() { return seatTypeSnapshot; }
    public void setSeatTypeSnapshot(String seatTypeSnapshot) { this.seatTypeSnapshot = seatTypeSnapshot; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getBookingStatus() { return bookingStatus; }
    public Long getActiveSeatId() { return activeSeatId; }
    public Booking getBooking() { return booking; }
    public Seat getSeat() { return seat; }
}
