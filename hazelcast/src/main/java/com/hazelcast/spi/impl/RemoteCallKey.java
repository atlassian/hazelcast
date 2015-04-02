package com.hazelcast.spi.impl;

import com.hazelcast.nio.Address;
import com.hazelcast.spi.Operation;
import com.hazelcast.util.Clock;

/**
 * Created by csmajda on 14/11/2014.
 */
public final class RemoteCallKey {
    private final long time = Clock.currentTimeMillis();
    // human readable caller
    private final Address callerAddress;
    private final String callerUuid;
    private final long callId;

    public RemoteCallKey(Address callerAddress, String callerUuid, long callId) {
        if (callerUuid == null) {
            throw new IllegalArgumentException("Caller UUID is required!");
        }
        this.callerAddress = callerAddress;
        if (callerAddress == null) {
            throw new IllegalArgumentException("Caller address is required!");
        }
        this.callerUuid = callerUuid;
        this.callId = callId;
    }

    public RemoteCallKey(final Operation op) {
        callerUuid = op.getCallerUuid();
        if (callerUuid == null) {
            throw new IllegalArgumentException("Caller UUID is required! -> " + op);
        }
        callerAddress = op.getCallerAddress();
        if (callerAddress == null) {
            throw new IllegalArgumentException("Caller address is required! -> " + op);
        }
        callId = op.getCallId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RemoteCallKey callKey = (RemoteCallKey) o;
        if (callId != callKey.callId) {
            return false;
        }
        if (!callerUuid.equals(callKey.callerUuid)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = callerUuid.hashCode();
        result = 31 * result + (int) (callId ^ (callId >>> 32));
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RemoteCallKey");
        sb.append("{callerAddress=").append(callerAddress);
        sb.append(", callerUuid=").append(callerUuid);
        sb.append(", callId=").append(callId);
        sb.append(", time=").append(time);
        sb.append('}');
        return sb.toString();
    }
}
