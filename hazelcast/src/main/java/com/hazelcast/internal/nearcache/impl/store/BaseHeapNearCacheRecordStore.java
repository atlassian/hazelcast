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

package com.hazelcast.internal.nearcache.impl.store;

import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionConfig.MaxSizePolicy;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.config.NearCachePreloaderConfig;
import com.hazelcast.internal.adapter.DataStructureAdapter;
import com.hazelcast.internal.eviction.MaxSizeChecker;
import com.hazelcast.internal.nearcache.NearCacheRecord;
import com.hazelcast.internal.nearcache.impl.maxsize.EntryCountNearCacheMaxSizeChecker;
import com.hazelcast.internal.nearcache.impl.preloader.NearCachePreloader;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.serialization.SerializationService;

import java.util.Map;

public abstract class BaseHeapNearCacheRecordStore<K, V, R extends NearCacheRecord>
        extends AbstractNearCacheRecordStore<K, V, K, R, HeapNearCacheRecordMap<K, R>> {

    protected static final int DEFAULT_INITIAL_CAPACITY = 1000;

    private final NearCachePreloader<K> nearCachePreloader;

    public BaseHeapNearCacheRecordStore(String name, NearCacheConfig nearCacheConfig, SerializationService serializationService,
                                        ClassLoader classLoader) {
            super(nearCacheConfig, serializationService, classLoader);

        NearCachePreloaderConfig preloaderConfig = nearCacheConfig.getPreloaderConfig();
        this.nearCachePreloader = preloaderConfig.isEnabled()
                ? new NearCachePreloader<K>(name, preloaderConfig, nearCacheStats, serializationService)
                : null;
    }

    @Override
    protected MaxSizeChecker createNearCacheMaxSizeChecker(EvictionConfig evictionConfig,
                                                           NearCacheConfig nearCacheConfig) {
        MaxSizePolicy maxSizePolicy = evictionConfig.getMaximumSizePolicy();
        if (maxSizePolicy == null) {
            throw new IllegalArgumentException("Max-Size policy cannot be null");
        }
        if (maxSizePolicy == MaxSizePolicy.ENTRY_COUNT) {
            return new EntryCountNearCacheMaxSizeChecker(evictionConfig.getSize(), records);
        }
        throw new IllegalArgumentException("Invalid max-size policy "
                + '(' + maxSizePolicy + ") for " + getClass().getName() + "! Only "
                + MaxSizePolicy.ENTRY_COUNT + " is supported.");
    }

    @Override
    protected HeapNearCacheRecordMap<K, R> createNearCacheRecordMap(NearCacheConfig nearCacheConfig) {
        return new HeapNearCacheRecordMap<K, R>(serializationService, DEFAULT_INITIAL_CAPACITY);
    }

    @Override
    protected R getRecord(K key) {
        return records.get(key);
    }

    @Override
    protected R putRecord(K key, R record) {
        R oldRecord = records.put(key, record);
        nearCacheStats.incrementOwnedEntryMemoryCost(getTotalStorageMemoryCost(key, record));
        return oldRecord;
    }

    @Override
    protected R removeRecord(K key) {
        R removedRecord = records.remove(key);
        if (removedRecord != null) {
            nearCacheStats.decrementOwnedEntryMemoryCost(getTotalStorageMemoryCost(key, removedRecord));
        }
        return removedRecord;
    }

    @Override
    protected boolean containsRecordKey(K key) {
        return records.containsKey(key);
    }

    @Override
    public void onEvict(K key, R record, boolean wasExpired) {
        super.onEvict(key, record, wasExpired);
        nearCacheStats.decrementOwnedEntryMemoryCost(getTotalStorageMemoryCost(key, record));
    }

    @Override
    public void doExpiration() {
        for (Map.Entry<K, R> entry : records.entrySet()) {
            K key = entry.getKey();
            R value = entry.getValue();
            if (isRecordExpired(value)) {
                remove(key);
                onExpire(key, value);
            }
        }
    }

    @Override
    public void loadKeys(DataStructureAdapter<Data, ?> adapter) {
        if (nearCachePreloader != null) {
            nearCachePreloader.loadKeys(adapter);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void storeKeys() {
        if (nearCachePreloader != null) {
            nearCachePreloader.storeKeys(records);
        }
    }
}
