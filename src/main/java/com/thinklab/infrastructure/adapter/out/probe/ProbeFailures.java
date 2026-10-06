package com.thinklab.infrastructure.adapter.out.probe;

import com.thinklab.domain.model.ProbeResult;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Instant;
import java.util.concurrent.CompletionException;

/** Turns whatever went wrong into one of the fixed result codes: nothing a target or the network stack says is ever kept. */
final class ProbeFailures {

    private ProbeFailures() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static ProbeResult toResult(Throwable error, Instant at, long latencyMillis) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        String code;
        if (cause instanceof AddressNotAllowedException) {
            code = ProbeResult.BLOCKED;
        } else if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
            code = ProbeResult.TIMEOUT;
        } else if (cause instanceof ConnectException) {
            code = ProbeResult.REFUSED;
        } else if (cause instanceof UnknownHostException || cause instanceof UnresolvedAddressException) {
            code = ProbeResult.DNS;
        } else {
            code = ProbeResult.FAILED;
        }
        return ProbeResult.down(at, latencyMillis, code, null);
    }
}
