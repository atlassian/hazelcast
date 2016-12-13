/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.instance;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.Member;
import com.hazelcast.internal.cluster.ClusterService;
import com.hazelcast.internal.cluster.impl.ClusterDataSerializerHook;
import com.hazelcast.internal.cluster.impl.operations.MemberAttributeChangedOperation;
import com.hazelcast.internal.cluster.impl.operations.MemberCapabilityUpdateRequestOperation;
import com.hazelcast.logging.ILogger;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.IOUtil;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.spi.Operation;
import com.hazelcast.spi.OperationService;
import com.hazelcast.spi.annotation.PrivateApi;
import com.hazelcast.spi.impl.NodeEngineImpl;
import com.hazelcast.util.ExceptionUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.hazelcast.cluster.MemberAttributeOperationType.PUT;
import static com.hazelcast.cluster.MemberAttributeOperationType.REMOVE;
import static com.hazelcast.util.Preconditions.isNotNull;

@PrivateApi
public final class MemberImpl extends AbstractMember implements Member, HazelcastInstanceAware, IdentifiedDataSerializable {

    private boolean localMember;
    private volatile HazelcastInstanceImpl instance;
    private volatile ILogger logger;
    private volatile Set<Capability> capabilities;

    public MemberImpl() {
    }

    public MemberImpl(Address address, boolean localMember) {
        this(address, localMember, null, null);
    }

    public MemberImpl(Address address, boolean localMember, boolean liteMember) {
        this(address, localMember, null, null, null, liteMember);
    }

    public MemberImpl(Address address, boolean localMember, String uuid, HazelcastInstanceImpl instance) {
        this(address, localMember, uuid, instance, null, false);
    }

    public MemberImpl(Address address, boolean localMember, String uuid, HazelcastInstanceImpl instance,
                      Map<String, Object> attributes, boolean liteMember) {
        this(address, localMember, uuid, instance, EnumSet.allOf(Capability.class), attributes, liteMember);
    }

    public MemberImpl(Address address, boolean localMember, String uuid, HazelcastInstanceImpl instance,
                      Set<Capability> capabilities, Map<String, Object> attributes, boolean liteMember) {
        super(address, uuid, attributes, liteMember);
        this.localMember = localMember;
        this.instance = instance;

        this.capabilities = (capabilities == null || capabilities.isEmpty())
                ? Collections.unmodifiableSet(EnumSet.noneOf(Capability.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    public MemberImpl(MemberImpl member) {
        this(member.address, member.localMember(), member.uuid, member.instance, member.capabilities, member.attributes, member.liteMember);
    }

    @Override
    protected ILogger getLogger() {
        return logger;
    }

    public Set<Capability> getCapabilities() {
        return capabilities;
    }

    public boolean setCapabilities(Set<Capability> capabilities) {
        if (this.capabilities.equals(capabilities)) {
            return false;
        }

        this.capabilities = EnumSet.copyOf(capabilities);

        return true;
    }

    @Override
    public void updateCapabilities(Set<Capability> capabilities) {
        invokeOnMaster(new MemberCapabilityUpdateRequestOperation(getUuid(), capabilities));
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        if (hazelcastInstance instanceof HazelcastInstanceImpl) {
            instance = (HazelcastInstanceImpl) hazelcastInstance;
            localMember = instance.node.address.equals(address);
            logger = instance.node.getLogger(this.getClass().getName());
        }
    }

    @Override
    public boolean localMember() {
        return localMember;
    }

    @Override
    public String getStringAttribute(String key) {
        return (String) getAttribute(key);
    }

    @Override
    public void setStringAttribute(String key, String value) {
        setAttribute(key, value);
    }

    @Override
    public Boolean getBooleanAttribute(String key) {
        return (Boolean) getAttribute(key);
    }

    @Override
    public void setBooleanAttribute(String key, boolean value) {
        setAttribute(key, value);
    }

    @Override
    public Byte getByteAttribute(String key) {
        return (Byte) getAttribute(key);
    }

    @Override
    public void setByteAttribute(String key, byte value) {
        setAttribute(key, value);
    }

    @Override
    public Short getShortAttribute(String key) {
        return (Short) getAttribute(key);
    }

    @Override
    public void setShortAttribute(String key, short value) {
        setAttribute(key, value);
    }

    @Override
    public Integer getIntAttribute(String key) {
        return (Integer) getAttribute(key);
    }

    @Override
    public void setIntAttribute(String key, int value) {
        setAttribute(key, value);
    }

    @Override
    public Long getLongAttribute(String key) {
        return (Long) getAttribute(key);
    }

    @Override
    public void setLongAttribute(String key, long value) {
        setAttribute(key, value);
    }

    @Override
    public Float getFloatAttribute(String key) {
        return (Float) getAttribute(key);
    }

    @Override
    public void setFloatAttribute(String key, float value) {
        setAttribute(key, value);
    }

    @Override
    public Double getDoubleAttribute(String key) {
        return (Double) getAttribute(key);
    }

    @Override
    public void setDoubleAttribute(String key, double value) {
        setAttribute(key, value);
    }

    @Override
    public void removeAttribute(String key) {
        isLocalMember();
        isNotNull(key, "key");

        Object value = attributes.remove(key);
        if (value == null) {
            return;
        }

        if (instance != null) {
            MemberAttributeChangedOperation operation = new MemberAttributeChangedOperation(REMOVE, key, null);
            invokeOnAllMembers(operation);
        }
    }

    private void isLocalMember() {
        if (!localMember) {
            throw new UnsupportedOperationException("Attributes on remote members must not be changed");
        }
    }

    private void setAttribute(String key, Object value) {
        isLocalMember();
        isNotNull(key, "key");
        isNotNull(value, "value");

        Object oldValue = attributes.put(key, value);
        if (value.equals(oldValue)) {
            return;
        }

        if (instance != null) {
            MemberAttributeChangedOperation operation = new MemberAttributeChangedOperation(PUT, key, value);
            invokeOnAllMembers(operation);
        }
    }

    private void invokeOnMaster(Operation operation) {
        ClusterService clusterService = instance.node.clusterService;
        Address masterAddress = clusterService.getMasterAddress();
        MemberImpl masterMember = clusterService.getMember(masterAddress);
        invokeOn(operation, masterMember);
    }

    private void invokeOn(Operation operation, MemberImpl member) {
        NodeEngineImpl nodeEngine = instance.node.nodeEngine;
        OperationService os = nodeEngine.getOperationService();
        String uuid = nodeEngine.getLocalMember().getUuid();
        operation.setCallerUuid(uuid).setNodeEngine(nodeEngine);
        try {
            if (member.localMember()) {
                os.executeOperation(operation);
            } else {
                os.send(operation, member.getAddress());
            }
        } catch (Throwable t) {
            throw ExceptionUtil.rethrow(t);
        }
    }

    private void invokeOnAllMembers(Operation operation) {
        NodeEngineImpl nodeEngine = instance.node.nodeEngine;
        OperationService os = nodeEngine.getOperationService();
        String uuid = nodeEngine.getLocalMember().getUuid();
        operation.setCallerUuid(uuid).setNodeEngine(nodeEngine);
        try {
            for (Member member : nodeEngine.getClusterService().getMembers()) {
                if (!member.localMember()) {
                    os.send(operation, member.getAddress());
                } else {
                    os.executeOperation(operation);
                }
            }
        } catch (Throwable t) {
            throw ExceptionUtil.rethrow(t);
        }
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        address = new Address();
        address.readData(in);
        uuid = in.readUTF();
        liteMember = in.readBoolean();
        capabilities = Capability.readCapabilities(in);

        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            Object value = IOUtil.readAttributeValue(in);
            attributes.put(key, value);
        }
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        address.writeData(out);
        out.writeUTF(uuid);
        out.writeBoolean(liteMember);
        Capability.writeCapabilities(out, getCapabilities());

        Map<String, Object> attributes = new HashMap<String, Object>(this.attributes);
        out.writeInt(attributes.size());
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            out.writeUTF(entry.getKey());
            IOUtil.writeAttributeValue(entry.getValue(), out);
        }
    }

    public int getFactoryId() {
        return ClusterDataSerializerHook.F_ID;
    }

    @Override
    public int getId() {
        return ClusterDataSerializerHook.MEMBER;
    }

    public boolean hasCapability(Capability capability) {
        return capabilities.contains(capability);
    }
}
