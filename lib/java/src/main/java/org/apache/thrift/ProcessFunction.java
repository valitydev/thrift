package org.apache.thrift;

import dev.vality.woody.api.event.CallType;
import dev.vality.woody.api.trace.ContextUtils;
import dev.vality.woody.api.trace.Metadata;
import dev.vality.woody.api.trace.MetadataProperties;
import dev.vality.woody.api.trace.TraceData;
import dev.vality.woody.api.trace.context.TraceContext;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.vality.woody.api.trace.context.TraceContext.getCurrentTraceData;

public abstract class ProcessFunction<I, T extends TBase> {
  private final String methodName;

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessFunction.class.getName());

  public ProcessFunction(String methodName) {
    this.methodName = methodName;
  }

  public final void process(int seqid, TProtocol iprot, TProtocol oprot, I iface)
      throws TException {
    getCurrentTraceData().getServiceSpan().getMetadata().putValue(MetadataProperties.CALL_TYPE,
            isOneway() ?
            CallType.CAST :
            CallType.CALL);
    T args = getEmptyArgsInstance();
    try {
      args.read(iprot);
    } catch (TProtocolException e) {
      iprot.readMessageEnd();
      TApplicationException x =
          new TApplicationException(TApplicationException.PROTOCOL_ERROR, e.getMessage());
      oprot.writeMessageBegin(new TMessage(getMethodName(), TMessageType.EXCEPTION, seqid));
      x.write(oprot);
      oprot.writeMessageEnd();
      oprot.getTransport().flush();
      return;
    }
    iprot.readMessageEnd();
    TSerializable result = null;

    try {
      result = getResult(iface, args);
    } catch (Exception ex) {
      LOGGER.error("Internal error processing " + getMethodName(), ex);
      if (rethrowUnhandledExceptions()) {
        throw new RuntimeException(ex.getMessage(), ex);
      }
      throw ex;
      }

    if (!isOneway()) {
      oprot.writeMessageBegin(new TMessage(getMethodName(), TMessageType.REPLY, seqid));
      result.write(oprot);
      oprot.writeMessageEnd();
      oprot.getTransport().flush();
    }
  }

  private void handleException(int seqid, TProtocol oprot) throws TException {
    if (!isOneway()) {
      TApplicationException x =
          new TApplicationException(
              TApplicationException.INTERNAL_ERROR, "Internal error processing " + getMethodName());
      oprot.writeMessageBegin(new TMessage(getMethodName(), TMessageType.EXCEPTION, seqid));
      x.write(oprot);
      oprot.writeMessageEnd();
      oprot.getTransport().flush();
    }
  }

  protected boolean rethrowUnhandledExceptions() {
    return false;
  }

  protected abstract boolean isOneway();

  public abstract TBase getResult(I iface, T args) throws TException;

  public abstract T getEmptyArgsInstance();

  public String getMethodName() {
    return methodName;
  }
}
