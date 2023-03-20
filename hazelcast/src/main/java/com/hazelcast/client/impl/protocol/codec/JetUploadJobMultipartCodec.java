/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.client.impl.protocol.codec;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.Generated;
import com.hazelcast.client.impl.protocol.codec.builtin.*;
import com.hazelcast.client.impl.protocol.codec.custom.*;

import javax.annotation.Nullable;

import static com.hazelcast.client.impl.protocol.ClientMessage.*;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.*;

/*
 * This file is auto-generated by the Hazelcast Client Protocol Code Generator.
 * To change this file, edit the templates or the protocol
 * definitions on the https://github.com/hazelcast/hazelcast-client-protocol
 * and regenerate it.
 */

/**
 */
@Generated("d4dcbb8be4a3b5c1c96869fff8cfeb32")
public final class JetUploadJobMultipartCodec {
    //hex: 0xFE1200
    public static final int REQUEST_MESSAGE_TYPE = 16650752;
    //hex: 0xFE1201
    public static final int RESPONSE_MESSAGE_TYPE = 16650753;
    private static final int REQUEST_SESSION_ID_FIELD_OFFSET = PARTITION_ID_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int REQUEST_CURRENT_PART_NUMBER_FIELD_OFFSET = REQUEST_SESSION_ID_FIELD_OFFSET + UUID_SIZE_IN_BYTES;
    private static final int REQUEST_TOTAL_PART_NUMBER_FIELD_OFFSET = REQUEST_CURRENT_PART_NUMBER_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int REQUEST_PART_SIZE_FIELD_OFFSET = REQUEST_TOTAL_PART_NUMBER_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int REQUEST_INITIAL_FRAME_SIZE = REQUEST_PART_SIZE_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int RESPONSE_INITIAL_FRAME_SIZE = RESPONSE_BACKUP_ACKS_FIELD_OFFSET + BYTE_SIZE_IN_BYTES;

    private JetUploadJobMultipartCodec() {
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
    public static class RequestParameters {

        /**
         * Unique session ID of the job upload request
         */
        public java.util.UUID sessionId;

        /**
         * The current part number being sent. Starts from 1
         */
        public int currentPartNumber;

        /**
         * The total number of parts to be sent. Minimum value is 1
         */
        public int totalPartNumber;

        /**
         * The binary data of the message part
         */
        public byte[] partData;

        /**
         * The size of binary data
         */
        public int partSize;

        /**
         * Hexadecimal SHA256 of the message part
         */
        public java.lang.String sha256Hex;
    }

    public static ClientMessage encodeRequest(java.util.UUID sessionId, int currentPartNumber, int totalPartNumber, byte[] partData, int partSize, java.lang.String sha256Hex) {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        clientMessage.setRetryable(true);
        clientMessage.setOperationName("Jet.UploadJobMultipart");
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[REQUEST_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, REQUEST_MESSAGE_TYPE);
        encodeInt(initialFrame.content, PARTITION_ID_FIELD_OFFSET, -1);
        encodeUUID(initialFrame.content, REQUEST_SESSION_ID_FIELD_OFFSET, sessionId);
        encodeInt(initialFrame.content, REQUEST_CURRENT_PART_NUMBER_FIELD_OFFSET, currentPartNumber);
        encodeInt(initialFrame.content, REQUEST_TOTAL_PART_NUMBER_FIELD_OFFSET, totalPartNumber);
        encodeInt(initialFrame.content, REQUEST_PART_SIZE_FIELD_OFFSET, partSize);
        clientMessage.add(initialFrame);
        ByteArrayCodec.encode(clientMessage, partData);
        StringCodec.encode(clientMessage, sha256Hex);
        return clientMessage;
    }

    public static JetUploadJobMultipartCodec.RequestParameters decodeRequest(ClientMessage clientMessage) {
        ClientMessage.ForwardFrameIterator iterator = clientMessage.frameIterator();
        RequestParameters request = new RequestParameters();
        ClientMessage.Frame initialFrame = iterator.next();
        request.sessionId = decodeUUID(initialFrame.content, REQUEST_SESSION_ID_FIELD_OFFSET);
        request.currentPartNumber = decodeInt(initialFrame.content, REQUEST_CURRENT_PART_NUMBER_FIELD_OFFSET);
        request.totalPartNumber = decodeInt(initialFrame.content, REQUEST_TOTAL_PART_NUMBER_FIELD_OFFSET);
        request.partSize = decodeInt(initialFrame.content, REQUEST_PART_SIZE_FIELD_OFFSET);
        request.partData = ByteArrayCodec.decode(iterator);
        request.sha256Hex = StringCodec.decode(iterator);
        return request;
    }

    public static ClientMessage encodeResponse() {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[RESPONSE_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, RESPONSE_MESSAGE_TYPE);
        clientMessage.add(initialFrame);

        return clientMessage;
    }
}