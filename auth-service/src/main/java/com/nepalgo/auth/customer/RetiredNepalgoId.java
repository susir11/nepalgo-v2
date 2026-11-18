package com.nepalgo.auth.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "retired_nepalgo_ids")
@Getter
@NoArgsConstructor
@lombok.AllArgsConstructor
public class RetiredNepalgoId {
    @Id
    @Column(name = "nepalgo_id", length = 8)
    private String nepalgoId;
}