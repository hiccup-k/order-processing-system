package com.example.order.controller;

import com.example.order.domain.Order;
import com.example.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Servlet-filter-level JWT parsing is skipped here (addFilters = false); this slice
 * verifies request/response mapping and method-level @PreAuthorize authorization only.
 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    @WithMockUser(roles = "USER")
    void createOrder_returns201() throws Exception {
        Order order = new Order("cust-1", BigDecimal.valueOf(20));
        when(orderService.createOrder(any())).thenReturn(order);

        String body = """
            {
              "customerId": "cust-1",
              "items": [ { "sku": "SKU-1", "quantity": 2, "price": 10.00 } ]
            }
            """;

        mockMvc.perform(post("/api/v1/orders")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.customerId").value("cust-1"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createOrder_rejectsEmptyItems() throws Exception {
        String body = """
            { "customerId": "cust-1", "items": [] }
            """;

        mockMvc.perform(post("/api/v1/orders")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getOrder_returns404WhenMissing() throws Exception {
        when(orderService.getOrder(eq(404L)))
            .thenThrow(new com.example.order.exception.ResourceNotFoundException("Order 404 not found"));

        mockMvc.perform(get("/api/v1/orders/404"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createOrder_unauthenticatedIsRejectedByMethodSecurity() throws Exception {
        // no @WithMockUser => anonymous principal => @PreAuthorize denies => 403
        String body = """
            { "customerId": "cust-1", "items": [ { "sku": "SKU-1", "quantity": 1, "price": 5.00 } ] }
            """;

        mockMvc.perform(post("/api/v1/orders")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isForbidden());
    }
}
