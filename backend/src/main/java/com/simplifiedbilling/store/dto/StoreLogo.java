package com.simplifiedbilling.store.dto;

public record StoreLogo(String fileName, String contentType, byte[] data) {

    public StoreLogo {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
