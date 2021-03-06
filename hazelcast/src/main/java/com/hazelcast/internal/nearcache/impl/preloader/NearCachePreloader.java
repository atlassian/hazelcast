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

package com.hazelcast.internal.nearcache.impl.preloader;

import com.hazelcast.config.NearCachePreloaderConfig;
import com.hazelcast.internal.adapter.DataStructureAdapter;
import com.hazelcast.internal.nearcache.NearCacheRecord;
import com.hazelcast.internal.nearcache.impl.NearCacheRecordMap;
import com.hazelcast.internal.serialization.impl.HeapData;
import com.hazelcast.internal.util.BufferingInputStream;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.memory.MemoryUnit;
import com.hazelcast.monitor.impl.NearCacheStatsImpl;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.serialization.SerializationService;
import com.hazelcast.util.collection.InflatableSet;
import com.hazelcast.util.collection.InflatableSet.Builder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Set;

import static com.hazelcast.nio.Bits.INT_SIZE_IN_BYTES;
import static com.hazelcast.nio.Bits.readIntB;
import static com.hazelcast.nio.Bits.writeIntB;
import static com.hazelcast.nio.IOUtil.closeResource;
import static com.hazelcast.nio.IOUtil.deleteQuietly;
import static com.hazelcast.nio.IOUtil.readFullyOrNothing;
import static com.hazelcast.nio.IOUtil.rename;
import static com.hazelcast.nio.IOUtil.toFileName;
import static com.hazelcast.util.StringUtil.isNullOrEmpty;
import static java.lang.String.format;
import static java.nio.ByteBuffer.allocate;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Loads and stores the keys from a Near Cache into a file.
 *
 * @param <KS> type of the {@link NearCacheRecord} keys
 */
public class NearCachePreloader<KS> {

    /**
     * File format for the file header.
     */
    private enum FileFormat {
        INTERLEAVED_LENGTH_FIELD
    }

    /**
     * Magic bytes for the file header.
     */
    private static final int MAGIC_BYTES = 0xEA3CAC4E;

    /**
     * Base-2 logarithm of buffer size.
     */
    private static final int LOG_OF_BUFFER_SIZE = 16;
    /**
     * Buffer size used for file I/O. Invariant: buffer size is a power of two.
     */
    private static final int BUFFER_SIZE = 1 << LOG_OF_BUFFER_SIZE;

    /**
     * Batch size for the pre-loader.
     */
    private static final int LOAD_BATCH_SIZE = 100;

    private final ILogger logger = Logger.getLogger(NearCachePreloader.class);
    private final ByteBuffer buf = allocate(BUFFER_SIZE);
    private final byte[] tmpBytes = new byte[INT_SIZE_IN_BYTES];

    private final String nearCacheName;
    private final NearCacheStatsImpl nearCacheStats;
    private final SerializationService serializationService;

    private final File storeFile;
    private final File tmpStoreFile;

    private int lastWrittenBytes;
    private int lastKeyCount;

    public NearCachePreloader(String nearCacheName, NearCachePreloaderConfig preloaderConfig,
                              NearCacheStatsImpl nearCacheStats, SerializationService serializationService) {
        this.nearCacheName = nearCacheName;
        this.nearCacheStats = nearCacheStats;
        this.serializationService = serializationService;

        String fileName = getFileName(preloaderConfig.getFileName(), nearCacheName);
        this.storeFile = new File(fileName);
        this.tmpStoreFile = new File(fileName + "~");
    }

    /**
     * Loads the values via a stored key file into the supplied {@link DataStructureAdapter}.
     *
     * @param adapter the {@link DataStructureAdapter} to load the values from
     */
    public void loadKeys(DataStructureAdapter<Data, ?> adapter) {
        long startedNanos = System.nanoTime();
        BufferingInputStream bis = null;
        try {
            bis = new BufferingInputStream(new FileInputStream(storeFile), BUFFER_SIZE);
            if (!checkHeader(bis)) {
                return;
            }

            int loadedKeys = loadKeySet(bis, adapter);

            long elapsedMillis = getElapsedMillis(startedNanos);
            logger.info(format("Loaded %d keys of Near Cache %s in %d ms", loadedKeys, nearCacheName, elapsedMillis));
        } catch (Exception e) {
            logger.warning(format("Could not pre-load Near Cache %s (%s): [%s] %s", nearCacheName, storeFile.getAbsolutePath(),
                    e.getClass().getSimpleName(), e.getMessage()));
        } finally {
            closeResource(bis);
        }
    }

    private boolean checkHeader(BufferingInputStream bis) throws IOException {
        int magicBytes = readInt(bis);
        if (magicBytes != MAGIC_BYTES) {
            logger.warning(format("Found invalid header for Near Cache %s (%s)", nearCacheName, storeFile.getAbsolutePath()));
            return false;
        }
        int fileFormat = readInt(bis);
        if (fileFormat < 0 || fileFormat > FileFormat.values().length - 1) {
            logger.warning(format("Found invalid file format for Near Cache %s (%s)", nearCacheName,
                    storeFile.getAbsolutePath()));
            return false;
        }
        return true;
    }

    /**
     * Stores the keys from the supplied {@link NearCacheRecordMap} instances.
     *
     * We need to support multiple records maps here, since Hazelcast Enterprise has a segmented
     * {@link com.hazelcast.internal.nearcache.NearCacheRecordStore} which contains multiple records maps.
     *
     * @param records the {@link NearCacheRecordMap} instances to retrieve the keys from
     * @param <R>     the {@link NearCacheRecord} type
     * @param <NCRM>  the {@link NearCacheRecordMap} type
     */
    public <R extends NearCacheRecord, NCRM extends NearCacheRecordMap<KS, R>> void storeKeys(NCRM... records) {
        long startedNanos = System.nanoTime();
        FileOutputStream fos = null;
        try {
            lastWrittenBytes = 0;
            lastKeyCount = 0;

            fos = new FileOutputStream(tmpStoreFile, false);

            // writer header
            writeInt(fos, MAGIC_BYTES);
            writeInt(fos, FileFormat.INTERLEAVED_LENGTH_FIELD.ordinal());

            // writer keys
            for (NCRM record : records) {
                writeKeySet(fos, fos.getChannel(), record.keySet());
            }

            // cleanup if no keys have been written
            if (lastKeyCount == 0) {
                deleteQuietly(storeFile);
                updatePersistenceStats(startedNanos);
                return;
            }

            fos.flush();
            rename(tmpStoreFile, storeFile);

            updatePersistenceStats(startedNanos);
        } catch (Exception e) {
            logger.warning(format("Could not store keys of Near Cache %s (%s): [%s] %s", nearCacheName,
                    storeFile.getAbsolutePath(), e.getClass().getSimpleName(), e.getMessage()));
        } finally {
            deleteQuietly(tmpStoreFile);
            closeResource(fos);
        }
    }

    private void updatePersistenceStats(long startedNanos) {
        long elapsedMillis = getElapsedMillis(startedNanos);
        nearCacheStats.addPersistence(elapsedMillis, lastWrittenBytes, lastKeyCount);

        logger.info(format("Stored %d keys of Near Cache %s in %d ms (%d kB)", lastKeyCount, nearCacheName, elapsedMillis,
                MemoryUnit.BYTES.toKiloBytes(lastWrittenBytes)));
    }

    private int loadKeySet(BufferingInputStream bis, DataStructureAdapter<Data, ?> adapter) throws IOException {
        int loadedKeys = 0;

        Builder<Data> builder = InflatableSet.newBuilder(LOAD_BATCH_SIZE);
        while (readFullyOrNothing(bis, tmpBytes)) {
            int dataSize = readIntB(tmpBytes, 0);
            byte[] payload = new byte[dataSize];
            if (!readFullyOrNothing(bis, payload)) {
                break;
            }
            builder.add(new HeapData(payload));
            if (builder.size() == LOAD_BATCH_SIZE) {
                adapter.getAll(builder.build());
                builder = InflatableSet.newBuilder(LOAD_BATCH_SIZE);
            }
            loadedKeys++;
        }
        if (builder.size() > 0) {
            adapter.getAll(builder.build());
        }
        return loadedKeys;
    }

    private void writeKeySet(FileOutputStream fos, FileChannel outChannel, Set<KS> keySet) throws IOException {
        for (KS key : keySet) {
            Data dataKey = serializationService.toData(key);
            if (dataKey != null) {
                int dataSize = dataKey.totalSize();
                writeInt(fos, dataSize);

                int position = 0;
                int remaining = dataSize;
                while (remaining > 0) {
                    int transferredCount = Math.min(BUFFER_SIZE - buf.position(), remaining);
                    ensureBufHasRoom(fos, transferredCount);
                    buf.put(dataKey.toByteArray(), position, transferredCount);
                    position += transferredCount;
                    remaining -= transferredCount;
                }

                lastWrittenBytes += INT_SIZE_IN_BYTES + dataSize;
                lastKeyCount++;
            }
            flushLocalBuffer(outChannel);
        }
    }

    private int readInt(BufferingInputStream bis) throws IOException {
        readFullyOrNothing(bis, tmpBytes);
        return readIntB(tmpBytes, 0);
    }

    private void writeInt(FileOutputStream fos, int dataSize) throws IOException {
        ensureBufHasRoom(fos, INT_SIZE_IN_BYTES);
        writeIntB(tmpBytes, 0, dataSize);
        buf.put(tmpBytes);
    }

    private void ensureBufHasRoom(FileOutputStream fos, int expectedSize) throws IOException {
        if (buf.position() < BUFFER_SIZE - expectedSize) {
            return;
        }
        fos.write(buf.array());
        buf.position(0);
    }

    private void flushLocalBuffer(FileChannel outChannel) throws IOException {
        if (buf.position() == 0) {
            return;
        }
        buf.flip();
        while (buf.hasRemaining()) {
            outChannel.write(buf);
        }
        buf.clear();
    }

    private static String getFileName(String configFileName, String nearCacheName) {
        if (!isNullOrEmpty(configFileName)) {
            return configFileName;
        }
        return "nearcache-" + toFileName(nearCacheName) + ".store";
    }

    private static long getElapsedMillis(long startedNanos) {
        return NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
