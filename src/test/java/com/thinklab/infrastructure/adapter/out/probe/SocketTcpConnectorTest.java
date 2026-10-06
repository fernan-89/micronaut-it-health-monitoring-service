package com.thinklab.infrastructure.adapter.out.probe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocketTcpConnectorTest {

    @Test
    @DisplayName("it connects to something that listens, and a port nothing listens on is a refused connection")
    void connects() throws IOException {
        SocketTcpConnector connector = new SocketTcpConnector();
        int closedPort;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            assertDoesNotThrow(() -> connector.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort()), 1000));
            closedPort = server.getLocalPort();
        }

        assertThrows(ConnectException.class, () -> connector.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), closedPort), 1000));
    }
}
