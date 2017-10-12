package com.hazelcast.cache.eviction;

import com.google.common.collect.Lists;
import com.hazelcast.config.Config;
import com.hazelcast.config.EntryListenerConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MapIndexConfig;
import com.hazelcast.config.MaxSizeConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class SimplePutGetTest {
    private HazelcastInstance hazelcast;

    @Before
    public void setUp() {
        hazelcast = Hazelcast.newHazelcastInstance(new Config());
    }

    @After
    public void tearDown() {
        Hazelcast.shutdownAll();
    }

    @Test
    public void testPut() throws InterruptedException {
        hazelcast.getConfig().addMapConfig(new MapConfig("test")
                .setMaxSizeConfig(new MaxSizeConfig(50, MaxSizeConfig.MaxSizePolicy.PER_NODE))
                .setTimeToLiveSeconds(3600)
                .setBackupCount(1)
                .setMaxIdleSeconds(3600)
                .setEvictionPolicy(EvictionPolicy.LRU)
                .setAsyncBackupCount(0)
                .setInMemoryFormat(InMemoryFormat.BINARY)
                .setReadBackupData(false)
                .setNearCacheConfig(null)
                .setMapStoreConfig(null)
                .setWanReplicationRef(null)
                .setEntryListenerConfigs(Lists.<EntryListenerConfig>newArrayList())
                .setMapIndexConfigs(Lists.<MapIndexConfig>newArrayList())
                .setMergePolicy("com.hazelcast.map.merge.PutIfAbsentMapMergePolicy")
        );
        final IMap<String, String> map = hazelcast.getMap("test");

        map.put("one", "two");

        assertThat(map.get("one"), equalTo("two"));
    }
}
