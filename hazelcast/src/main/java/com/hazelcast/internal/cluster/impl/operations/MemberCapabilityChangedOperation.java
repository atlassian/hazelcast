package com.hazelcast.internal.cluster.impl.operations;

import com.hazelcast.internal.cluster.impl.ClusterServiceImpl;
import com.hazelcast.instance.Capability;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;

import java.io.IOException;
import java.util.Set;

public class MemberCapabilityChangedOperation extends AbstractClusterOperation {


    private String uuid;
    private Set<Capability> capabilities;

    public MemberCapabilityChangedOperation() {
    }

    public MemberCapabilityChangedOperation(String uuid, Set<Capability> capabilities){
        this.uuid = uuid;
        this.capabilities = capabilities;
    }

    public String getUuid() {
        return uuid;
    }

    public Set<Capability> getCapabilities() {
        return capabilities;
    }

    @Override
    public void run() throws Exception {
        ((ClusterServiceImpl) getService()).updateMemberCapabilities(uuid, capabilities);
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeUTF(uuid);
        Capability.writeCapabilities(out, capabilities);
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        uuid = in.readUTF();

        capabilities = Capability.readCapabilities(in);
    }
}
