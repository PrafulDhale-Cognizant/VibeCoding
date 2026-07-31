package com.simplifiedbilling.store.service;

import com.simplifiedbilling.store.dto.StoreDetails;
import com.simplifiedbilling.store.dto.StoreLogo;
import com.simplifiedbilling.store.dto.UpdateStoreRequest;
import org.springframework.web.multipart.MultipartFile;

public interface StoreService {

    StoreDetails getStore();

    StoreDetails updateStore(String actorUserId, UpdateStoreRequest request);

    StoreDetails updateLogo(String actorUserId, MultipartFile file);

    void deleteLogo(String actorUserId);

    StoreLogo getLogo();
}
