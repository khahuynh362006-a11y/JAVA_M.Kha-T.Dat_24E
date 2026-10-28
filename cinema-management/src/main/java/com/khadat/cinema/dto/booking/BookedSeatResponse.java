package com.khadat.cinema.dto.booking;
import java.math.BigDecimal;
public record BookedSeatResponse(Long seatId,String code,String seatType,BigDecimal unitPrice,String bookingStatus) { }
