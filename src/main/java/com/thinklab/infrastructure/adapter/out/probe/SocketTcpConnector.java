package com.thinklab.infrastructure.adapter.out.probe;

import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** The plain-socket {@link TcpConnector}. */
@Singleton
class SocketTcpConnector implements TcpConnector {

    @Override
    public void connect(InetSocketAddress address, int timeoutMillis) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(address, timeoutMillis);
        }
    }
}
