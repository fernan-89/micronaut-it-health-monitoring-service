package com.thinklab.infrastructure.adapter.out.probe;

import java.io.IOException;
import java.net.InetSocketAddress;

/** Opens a TCP connection and closes it at once: all a TCP check needs to know is whether something answers. Replaceable in tests. */
@FunctionalInterface
interface TcpConnector {

    void connect(InetSocketAddress address, int timeoutMillis) throws IOException;
}
