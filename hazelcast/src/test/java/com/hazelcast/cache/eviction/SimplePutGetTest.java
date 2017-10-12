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

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 *
 */
@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class SimplePutGetTest {
    private HazelcastInstance hazelcast;

    @Before
    public void setUp() {
        Config cfg = new Config();
        hazelcast = Hazelcast.newHazelcastInstance(cfg);
    }

    @After
    public void tearDown() {
        Hazelcast.shutdownAll();
    }

    @Test
    public void testPut() throws InterruptedException {
//        tester.hazelcast.getConfig().addMapConfig(new MapConfig("jjj")
        hazelcast.getConfig().addMapConfig(new MapConfig("jjj")
                .setMaxSizeConfig(new MaxSizeConfig(50, MaxSizeConfig.MaxSizePolicy.PER_NODE)) //
                .setTimeToLiveSeconds(3600)
                .setBackupCount(1)
                .setMaxIdleSeconds(3600)
                .setEvictionPolicy(EvictionPolicy.LRU) //
                .setEvictionPercentage(10)
                .setMinEvictionCheckMillis(100)
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
//        final IMap<String, String> bbq = tester.hazelcast.getMap("jjj");
        final IMap<String, String> bbq = hazelcast.getMap("jjj");
        bbq.put("one", "two");
        for(int i = 0; i < 100; i++) {
            bbq.put(String.valueOf(i), UUID.randomUUID().toString());
//            Thread.sleep(1000);
        }

        /*System.out.println("NEW MAP");
        System.out.println("NEW MAP");
        System.out.println("NEW MAP");
        System.out.println("NEW MAP");
        final IMap<String, String> bbq1 = hazelcast.getMap("iii");
        for(int i = 0; i < 100; i++) {
            bbq1.put(String.valueOf(i), UUID.randomUUID().toString());
        }*/
//        System.out.println(bbq.put("one", "two"));
//        System.out.println(bbq.put("one", "three"));
//        Thread.sleep(10000);
        assertThat(bbq.get("one"), equalTo("two"));
    }
}
