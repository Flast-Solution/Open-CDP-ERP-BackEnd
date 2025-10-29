package vn.flast.entities;

import jakarta.validation.constraints.NotNull;

public record ImageSlideUpdate(
    @NotNull(message = "productId không được để trống")
    Long productId,

    @NotNull(message = "imageId không được để trống")
    Integer imageId,

    @NotNull(message = "checked không được để trống")
    Boolean checked
) {}
