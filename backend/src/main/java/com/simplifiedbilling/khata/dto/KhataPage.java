package com.simplifiedbilling.khata.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record KhataPage<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <S, T> KhataPage<T> from(Page<S> source, Function<S, T> mapper) {
        return new KhataPage<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(), source.getSize(), source.getTotalElements(),
                source.getTotalPages(), source.isFirst(), source.isLast());
    }
}
