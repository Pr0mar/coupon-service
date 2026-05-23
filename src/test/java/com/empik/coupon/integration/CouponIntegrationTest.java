package com.empik.coupon.integration;

import com.empik.coupon.api.request.CreateCouponRequest;
import com.empik.coupon.api.request.UseCouponRequest;
import com.empik.coupon.infrastructure.geolocation.GeoLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("Coupon API integration tests")
class CouponIntegrationTest extends BaseIntegrationTest {

    private static final String CLIENT_IP = "1.2.3.4";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private GeoLocationService geoLocationService;

    @BeforeEach
    void setUp() {
        when(geoLocationService.getCountryCode(anyString())).thenReturn("PL");
    }

    @Test
    @DisplayName("creates a coupon and redeems it once")
    void shouldCreateAndUseCoupon() throws Exception {
        // given
        final CreateCouponRequest createRequest = new CreateCouponRequest("INTEGRATION01", 5, "PL");
        final UseCouponRequest useRequest = new UseCouponRequest("user-integration-1");

        // when / then
        mockMvc.perform(post("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("INTEGRATION01"))
            .andExpect(jsonPath("$.maxUses").value(5))
            .andExpect(jsonPath("$.currentUses").value(0));

        mockMvc.perform(post("/api/v1/coupons/INTEGRATION01/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(useRequest))
                .header("X-Forwarded-For", CLIENT_IP))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.couponCode").value("INTEGRATION01"))
            .andExpect(jsonPath("$.remainingUses").value(4));
    }

    @Test
    @DisplayName("normalizes lowercase code on creation and redemption")
    void shouldBeCaseInsensitive() throws Exception {
        // given
        final CreateCouponRequest createRequest = new CreateCouponRequest("mixedcase", 2, "PL");

        // when / then
        mockMvc.perform(post("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("MIXEDCASE"));

        mockMvc.perform(post("/api/v1/coupons/MixedCase/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UseCouponRequest("u-1")))
                .header("X-Forwarded-For", CLIENT_IP))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.couponCode").value("MIXEDCASE"));
    }

    @Test
    @DisplayName("returns 404 for a non-existent coupon")
    void shouldReturn404ForNonExistentCoupon() throws Exception {
        // given
        final UseCouponRequest request = new UseCouponRequest("user-1");

        // when / then
        mockMvc.perform(post("/api/v1/coupons/DOES-NOT-EXIST/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("COUPON_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 409 when a coupon is exhausted")
    void shouldReturn409WhenCouponExhausted() throws Exception {
        // given
        mockMvc.perform(post("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateCouponRequest("EXHAUSTED01", 1, "PL"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/coupons/EXHAUSTED01/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UseCouponRequest("user-1")))
                .header("X-Forwarded-For", CLIENT_IP))
            .andExpect(status().isOk());

        // when / then
        mockMvc.perform(post("/api/v1/coupons/EXHAUSTED01/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UseCouponRequest("user-2")))
                .header("X-Forwarded-For", CLIENT_IP))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("COUPON_EXHAUSTED"));
    }

    @Test
    @DisplayName("returns 409 when the same user redeems twice")
    void shouldReturn409WhenSameUserRedeemsTwice() throws Exception {
        // given
        mockMvc.perform(post("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateCouponRequest("ONCEPERUSER", 10, "PL"))))
            .andExpect(status().isCreated());

        final UseCouponRequest req = new UseCouponRequest("user-X");

        mockMvc.perform(post("/api/v1/coupons/ONCEPERUSER/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("X-Forwarded-For", CLIENT_IP))
            .andExpect(status().isOk());

        // when / then
        mockMvc.perform(post("/api/v1/coupons/ONCEPERUSER/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("X-Forwarded-For", CLIENT_IP))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("COUPON_ALREADY_USED"));
    }

    @Test
    @DisplayName("returns 403 when the user's country does not match")
    void shouldReturn403ForCountryNotAllowed() throws Exception {
        // given
        when(geoLocationService.getCountryCode(anyString())).thenReturn("DE");

        mockMvc.perform(post("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateCouponRequest("PLONLY01", 10, "PL"))))
            .andExpect(status().isCreated());

        // when / then
        mockMvc.perform(post("/api/v1/coupons/PLONLY01/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UseCouponRequest("user-de-1")))
                .header("X-Forwarded-For", CLIENT_IP))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("COUNTRY_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("returns 400 for invalid input")
    void shouldReturn400ForInvalidRequest() throws Exception {
        // given
        final String invalidJson = "{\"code\":\"\",\"maxUses\":-1,\"country\":\"INVALID\"}";

        // when / then
        mockMvc.perform(post("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
