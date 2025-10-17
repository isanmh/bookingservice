package com.example.inix.bookingservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.inix.bookingservice.client.InventoryServiceClient;
import com.example.inix.bookingservice.entity.Customer;
import com.example.inix.bookingservice.event.BookingEvent;
import com.example.inix.bookingservice.repository.CustomerRepository;
import com.example.inix.bookingservice.request.BookingRequest;
import com.example.inix.bookingservice.response.BookingResponse;
import com.example.inix.bookingservice.response.InventoryResponse;

import java.math.BigDecimal;

@Service
@Slf4j
public class BookingService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InventoryServiceClient inventoryServiceClient;

    @Autowired
    private KafkaTemplate<String, BookingEvent> kafkaTemplate;

    public BookingResponse createBooking(final BookingRequest request) {
        // check jika customer ada (userId valid)
        final Customer customer = customerRepository.findById(request.getUserId()).orElse(null);
        if (customer == null) {
            throw new RuntimeException("User not found");
        }
        // check jika inventory cukup (eventId valid & capacity cukup)
        final InventoryResponse inventoryResponse = inventoryServiceClient.getInventory(request.getEventId());
        log.info("Inventory Response: {}", inventoryResponse);
        if (inventoryResponse.getCapacity() < request.getTicketCount()) {
            throw new RuntimeException("Not enough inventory");
        }
        // create booking
        final BookingEvent bookingEvent = createBookingEvent(request, customer, inventoryResponse);
        // send booking to Order Service on a Kafka Topic
        kafkaTemplate.send("booking", bookingEvent);
        log.info("Booking sent to Kafka: {}", bookingEvent);
        return BookingResponse.builder()
                .userId(bookingEvent.getUserId())
                .eventId(bookingEvent.getEventId())
                .ticketCount(bookingEvent.getTicketCount())
                .totalPrice(bookingEvent.getTotalPrice())
                .build();
    }

    private BookingEvent createBookingEvent(final BookingRequest request,
            final Customer customer,
            final InventoryResponse inventoryResponse) {
        return BookingEvent.builder()
                .userId(customer.getId())
                .eventId(request.getEventId())
                .ticketCount(request.getTicketCount())
                .totalPrice(inventoryResponse.getTicketPrice().multiply(BigDecimal.valueOf(request.getTicketCount())))
                .build();
    }
}
