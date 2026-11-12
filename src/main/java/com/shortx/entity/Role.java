package com.shortx.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Maps the static `roles` reference table (V2 migration). Rows are seeded
 * by Flyway, not created at runtime — there is deliberately no
 * RoleService.createRole(); roles are a fixed, deploy-time concept.
 *
 * <p>No {@code @GeneratedValue} on {@code id}: role IDs are assigned
 * explicitly in the migration (1 = ROLE_USER, 2 = ROLE_ADMIN) and referenced
 * by those fixed values from {@link com.shortx.common.constants.RoleConstants}
 * wherever the application needs to assign a default role.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Role {

    @Id
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
