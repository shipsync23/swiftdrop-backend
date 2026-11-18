package com.swiftdrop.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authorization rules from report section 6.5, tested through the real HTTP layer and database. */
class AuthorizationIntegrationTest extends IntegrationTestBase {

    @Test
    void requestWithoutTokenGets401() throws Exception {
        mvc.perform(get("/api/v1/parcels"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void customerCannotUseDispatcherOrAdminEndpoints() throws Exception {
        String customer = login("customer@swiftdrop.lk");
        mvc.perform(get("/api/v1/dispatch/parcels/unassigned").header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcherCannotManageUsers() throws Exception {
        String dispatcher = login("dispatcher@swiftdrop.lk");
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(dispatcher)))
                .andExpect(status().isForbidden());
    }

    @Test
    void courierCannotAccessAnotherCouriersDelivery() throws Exception {
        JsonNode parcel = createParcel(login("customer@swiftdrop.lk"));
        String parcelId = parcel.get("id").asText();
        String dispatcher = login("dispatcher@swiftdrop.lk");
        assign(dispatcher, parcelId, courierId(dispatcher, "Kamal Perera"));

        // Nimal (courier2) is not assigned: cannot view or update Kamal's parcel
        String nimal = login("courier2@swiftdrop.lk");
        mvc.perform(get("/api/v1/parcels/{id}", parcelId).header("Authorization", bearer(nimal)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/courier/parcels/{id}/status", parcelId)
                        .param("status", "PICKED_UP")
                        .header("Authorization", bearer(nimal)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This parcel is not assigned to you"));

        // Kamal (courier1) is assigned: allowed
        String kamal = login("courier1@swiftdrop.lk");
        mvc.perform(multipart("/api/v1/courier/parcels/{id}/status", parcelId)
                        .param("status", "PICKED_UP")
                        .header("Authorization", bearer(kamal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));
    }

    @Test
    void customerCannotSeeAnotherCustomersParcel() throws Exception {
        String parcelId = createParcel(login("customer@swiftdrop.lk")).get("id").asText();
        String otherCustomer = registerCustomer();

        mvc.perform(get("/api/v1/parcels/{id}", parcelId).header("Authorization", bearer(otherCustomer)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/parcels/{id}/receipt", parcelId).header("Authorization", bearer(otherCustomer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicTrackingShowsNoPersonalData() throws Exception {
        String tracking = createParcel(login("customer@swiftdrop.lk")).get("trackingNumber").asText();

        mvc.perform(get("/api/v1/track/{tn}", tracking))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.recipientName").doesNotExist())
                .andExpect(jsonPath("$.recipientPhone").doesNotExist())
                .andExpect(jsonPath("$.dropoffAddress").doesNotExist());
    }

    @Test
    void merchantApiRejectsMissingOrInvalidKeys() throws Exception {
        String body = "{\"pickupAddress\":\"Warehouse, Kelaniya\",\"dropoffAddress\":\"Lake Road, Galle\","
                + "\"destZoneId\":4,\"recipientName\":\"Dilini\",\"recipientPhone\":\"0763334455\","
                + "\"size\":\"SMALL\",\"weightKg\":1}";
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/orders").header("X-API-Key", "sd_live_not_a_real_key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unapprovedMerchantCannotCreateApiKeys() throws Exception {
        String result = mvc.perform(post("/api/v1/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"shop" + System.nanoTime() + "@example.com\",\"password\":\"Password123\","
                                + "\"contactName\":\"Saman\",\"businessName\":\"Test Store\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String merchantToken = json.readTree(result).get("accessToken").asText();

        mvc.perform(post("/api/v1/merchant/api-keys").header("Authorization", bearer(merchantToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidStatusTransitionIsRejected() throws Exception {
        String parcelId = createParcel(login("customer@swiftdrop.lk")).get("id").asText();
        String dispatcher = login("dispatcher@swiftdrop.lk");
        assign(dispatcher, parcelId, courierId(dispatcher, "Kamal Perera"));

        mvc.perform(multipart("/api/v1/courier/parcels/{id}/status", parcelId)
                        .param("status", "DELIVERED")
                        .param("recipientName", "Sunil")
                        .header("Authorization", bearer(login("courier1@swiftdrop.lk"))))
                .andExpect(status().isConflict());
    }
}
