package com.thinklab.infrastructure.adapter.out.probe;

/** A name resolved to an address a probe must never reach (ADR-031). Internal to the probes: it becomes the fixed result code, never a message. */
class AddressNotAllowedException extends RuntimeException {

    AddressNotAllowedException() {
        super("address not allowed");
    }
}
