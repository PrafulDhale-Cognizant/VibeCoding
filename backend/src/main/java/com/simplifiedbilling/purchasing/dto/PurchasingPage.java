package com.simplifiedbilling.purchasing.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PurchasingPage<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <S, T> PurchasingPage<T> from(Page<S> source, Function<S, T> mapper) {
        return new PurchasingPage<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(), source.getSize(), source.getTotalElements(),
                source.getTotalPages(), source.isFirst(), source.isLast());
    }
}
