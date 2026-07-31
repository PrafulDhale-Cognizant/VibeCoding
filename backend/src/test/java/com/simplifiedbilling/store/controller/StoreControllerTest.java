package com.simplifiedbilling.store.controller;

import com.simplifiedbilling.store.dto.StoreDetails;
import com.simplifiedbilling.store.dto.StoreLogo;
import com.simplifiedbilling.store.dto.UpdateStoreRequest;
import com.simplifiedbilling.store.service.StoreService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreControllerTest {

    @Test
    void delegatesStoreAndLogoEndpoints() {
        StoreService service = mock(StoreService.class);
        StoreController controller = new StoreController(service);
        StoreDetails details = mock(StoreDetails.class);
        UpdateStoreRequest update = mock(UpdateStoreRequest.class);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[]{1, 2});
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("actor");
        when(service.getStore()).thenReturn(details);
        when(service.updateStore("actor", update)).thenReturn(details);
        when(service.updateLogo("actor", file)).thenReturn(details);
        when(service.getLogo()).thenReturn(new StoreLogo(
                "logo.png",
                "image/png",
                new byte[]{1, 2}));

        assertThat(controller.getStore()).isSameAs(details);
        assertThat(controller.updateStore(jwt, update)).isSameAs(details);
        assertThat(controller.updateLogo(jwt, file)).isSameAs(details);
        assertThat(controller.deleteLogo(jwt).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var logoResponse = controller.getLogo();
        assertThat(logoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logoResponse.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(logoResponse.getHeaders().getContentLength()).isEqualTo(2);
        assertThat(logoResponse.getBody().getByteArray()).containsExactly(1, 2);
        verify(service).deleteLogo("actor");
    }
}
