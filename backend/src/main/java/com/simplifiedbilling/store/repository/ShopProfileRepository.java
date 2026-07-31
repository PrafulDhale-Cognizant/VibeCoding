package com.simplifiedbilling.store.repository;

import com.simplifiedbilling.store.domain.ShopProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopProfileRepository extends JpaRepository<ShopProfile, Long> {
}
