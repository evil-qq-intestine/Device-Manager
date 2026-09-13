package com.example.tool.scripttask.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, long total, int page, int size, int totalPages) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                page.getTotalPages());
    }
}
