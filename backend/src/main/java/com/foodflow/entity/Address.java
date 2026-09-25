package com.foodflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A delivery address. Relationship: many addresses -> one user.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "addresses")
public class Address extends BaseEntity {

    /*
     * @ManyToOne defaults to EAGER in JPA; we override it to LAZY everywhere.
     * The owning user is rarely needed when showing an address, and
     * address.getUser().getId() is answered from the proxy without a query.
     * optional = false tells Hibernate the FK is NOT NULL (enables inner joins).
     */
    @Setter(AccessLevel.NONE) // an address never moves to another user
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String label;

    @Column(nullable = false, length = 255)
    private String line1;

    @Column(length = 255)
    private String line2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    /** The text copied into an order at checkout, e.g. "Home: 12 MG Road, Bengaluru, Karnataka 560001". */
    public String toSingleLine() {
        StringBuilder text = new StringBuilder(label).append(": ").append(line1);
        if (line2 != null && !line2.isBlank()) {
            text.append(", ").append(line2);
        }
        return text.append(", ").append(city).append(", ").append(state).append(' ').append(postalCode).toString();
    }

    public Address(User user, String label, String line1, String line2,
                   String city, String state, String postalCode, boolean defaultAddress) {
        this.user = user;
        this.label = label;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.defaultAddress = defaultAddress;
    }
}
