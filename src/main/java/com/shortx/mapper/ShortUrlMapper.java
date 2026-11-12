package com.shortx.mapper;

import com.shortx.dto.response.ShortUrlResponse;
import com.shortx.entity.ShortUrl;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct generates the implementation of this interface at compile
 * time (via the annotation processor wired in pom.xml) — no reflection,
 * no hand-written boilerplate mapping each field, and a compile error if
 * a field is ever added to one side without an explicit mapping decision
 * on the other.
 *
 * <p>{@code componentModel = "spring"} makes the generated implementation
 * a Spring bean, injectable anywhere via the {@link ShortUrlMapper}
 * interface type — the service layer never references the generated
 * {@code ShortUrlMapperImpl} class directly.
 */
@Mapper(componentModel = "spring")
public interface ShortUrlMapper {

    @Mapping(target = "passwordProtected", expression = "java(shortUrl.isPasswordProtected())")
    @Mapping(target = "fullShortUrl", ignore = true) // base URL isn't known to the entity; set in the service after mapping
    ShortUrlResponse toResponse(ShortUrl shortUrl);
}
