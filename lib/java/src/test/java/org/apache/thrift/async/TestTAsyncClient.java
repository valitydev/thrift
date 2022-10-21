package org.apache.thrift.async;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.thrift.TException;
import org.junit.jupiter.api.Test;
import thrift.test.SrvSrv.AsyncClient;

public class TestTAsyncClient {
  @Test
  public void testRaisesExceptionWhenUsedConcurrently() throws Exception {
    TAsyncClientManager mockClientManager =
        new TAsyncClientManager() {
          @Override
          public void call(TAsyncMethodCall method) throws TException {
            // do nothing
          }
        };

    SrvSrv.AsyncClient c = new AsyncClient(null, mockClientManager, null);
    c.Janky(0, null);
    assertThrows(Exception.class, c::checkReady);
  }
}
