package com.nepalgo.booking.review;

/** PENDING -> PUBLISHED | REJECTED (by staff); PUBLISHED -> HIDDEN (taken down later). Any customer edit -> PENDING. */
public enum ReviewStatus { PENDING, PUBLISHED, REJECTED, HIDDEN }
