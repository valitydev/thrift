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

import static org.apache.thrift.transport.sasl.TSaslNegotiationException.ErrorType.AUTHENTICATION_FAILURE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingServerTransport;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.transport.TTransportFactory;
import org.apache.thrift.transport.TestTSaslTransports;
import org.apache.thrift.transport.TestTSaslTransports.TestSaslCallbackHandler;
import org.apache.thrift.transport.sasl.TSaslNegotiationException;
import org.junit.jupiter.api.Test;
import thrift.test.ThriftTestSrv;

public class TestSaslNonblockingServer extends TestTSaslTransports.TestTSaslTransportsWithServer {

  private TSaslNonblockingServer server;

  @Override
  public void startServer(
      TProcessor processor, TProtocolFactory protoFactory, TTransportFactory factory)
      throws Exception {
    TNonblockingServerTransport serverSocket =
        new TNonblockingServerSocket(
            new TNonblockingServerSocket.NonblockingAbstractServerSocketArgs().port(PORT));
    TSaslNonblockingServer.Args args =
        new TSaslNonblockingServer.Args(serverSocket)
            .processor(processor)
            .transportFactory(factory)
            .protocolFactory(protoFactory)
            .addSaslMechanism(
                TestTSaslTransports.WRAPPED_MECHANISM,
                TestTSaslTransports.SERVICE,
                TestTSaslTransports.HOST,
                TestTSaslTransports.WRAPPED_PROPS,
                new TestSaslCallbackHandler(TestTSaslTransports.PASSWORD));
    server = new TSaslNonblockingServer(args);
    server.serve();
  }

  @Override
  public void stopServer() throws Exception {
    server.shutdown();
  }

  @Override
  @Test
  public void testIt() throws Exception {
    super.testIt();
  }

  @Test
  public void testBadPassword() throws Exception {
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    TProcessor processor = new ThriftTestSrv.Processor<>(new TestHandler());
    startServer(processor, protocolFactory);

    TSocket socket = new TSocket(HOST, PORT);
    socket.setTimeout(SOCKET_TIMEOUT);
    try (TSaslClientTransport client =
        new TSaslClientTransport(
            TestTSaslTransports.WRAPPED_MECHANISM,
            TestTSaslTransports.PRINCIPAL,
            TestTSaslTransports.SERVICE,
            TestTSaslTransports.HOST,
            TestTSaslTransports.WRAPPED_PROPS,
            new TestSaslCallbackHandler("bad_password"),
            socket)) {
      TTransportException error =
          assertThrows(
              TTransportException.class, client::open, "Client should fail with sasl negotiation.");
      TSaslNegotiationException serverSideError =
          new TSaslNegotiationException(
              AUTHENTICATION_FAILURE,
              "Authentication failed with " + TestTSaslTransports.WRAPPED_MECHANISM);
      assertTrue(
          error.getMessage().contains(serverSideError.getSummary()),
          "Server should return error message \"" + serverSideError.getSummary() + "\"");
    } finally {
      stopServer();
    }
  }

  @Test
  @Override
  public void testTransportFactory() {
    // This test is irrelevant here, so skipped.
  }
}
