/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.thrift.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ThreadPoolExecutor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TSocket;
import org.junit.jupiter.api.Test;
import thrift.test.ThriftTestSrv;

public class TestThreadPoolServer {

  /** Test server is shut down properly even with some open clients. */
  @Test
  public void testStopServerWithOpenClient() throws Exception {
    TServerSocket serverSocket = new TServerSocket(0, 3000);
    TThreadPoolServer server = buildServer(serverSocket);
    Thread serverThread = new Thread(server::serve);
    serverThread.start();
    try (TSocket client = new TSocket("localhost", serverSocket.getServerSocket().getLocalPort())) {
      client.open();
      Thread.sleep(1000);
      // There is a thread listening to the client
      assertEquals(1, ((ThreadPoolExecutor) server.getExecutorService()).getActiveCount());

      // Trigger the server to stop, but it does not wait
      server.stop();
      assertTrue(server.waitForShutdown());

      // After server is stopped, the executor thread pool should be shut down
      assertTrue(
          server.getExecutorService().isTerminated(), "Server thread pool should be terminated");

      // TODO: The socket is actually closed (timeout) but the client code
      // ignores the timeout Exception and maintains the socket open state
      assertTrue(client.isOpen(), "Client should be closed after server shutdown");
    }
  }

  private TThreadPoolServer buildServer(TServerTransport serverSocket) {
    TThreadPoolServer.Args args =
        new TThreadPoolServer.Args(serverSocket)
            .protocolFactory(new TBinaryProtocol.Factory())
            .processor(new ThriftTestSrv.Processor<>(new ServerTestBase.TestHandler()));
    return new TThreadPoolServer(args);
  }
}
