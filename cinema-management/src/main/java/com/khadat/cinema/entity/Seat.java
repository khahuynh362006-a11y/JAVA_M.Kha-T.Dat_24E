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
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(columnNames = {"id", "room_id"}))
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
    private Long id;

    @Column(name = "room_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long roomId;

    @Column(name = "code", nullable = false, length = 16)
    private String code;

    @Column(name = "row_label", nullable = false, length = 4)
    private String rowLabel;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "seat_number", nullable = false, columnDefinition = "smallint unsigned")
    private Integer seatNumber;

    @Column(name = "seat_type", nullable = false, length = 16)
    private String seatType;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Room room;

    public Seat() { }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getRowLabel() { return rowLabel; }
    public void setRowLabel(String rowLabel) { this.rowLabel = rowLabel; }
    public Integer getSeatNumber() { return seatNumber; }
    public void setSeatNumber(Integer seatNumber) { this.seatNumber = seatNumber; }
    public String getSeatType() { return seatType; }
    public void setSeatType(String seatType) { this.seatType = seatType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Room getRoom() { return room; }
}
