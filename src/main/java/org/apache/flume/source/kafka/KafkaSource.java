/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.flume.source.kafka;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.PollableSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.conf.ConfigurationException;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.source.AbstractSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Source for Kafka which reads messages from kafka. I use this in company production environment 
 * and its performance is good. Over 100k messages per second can be read from kafka in one source.<p>
 * <tt>zk.connect: </tt> the zookeeper ip kafka use.<p>
 * <tt>topic: </tt> the topic to read from kafka.<p>
 * <tt>groupid: </tt> the groupid of consumer group.<p>
 */
public class KafkaSource extends AbstractSource implements Configurable, PollableSource {
    private static final Logger log = LoggerFactory.getLogger(KafkaSource.class);
    private ConsumerConnector consumer;
    private ConsumerIterator<byte[], byte[]> it;
    private String topic;
    private String keyField;

    public Status process() throws EventDeliveryException {
        try {
            List<Event> eventList = new ArrayList<Event>();
            byte [] body;
            if(it.hasNext()) {
                MessageAndMetadata<byte[], byte[]> message = it.next();
                body = message.message();
                Map<String, String> headers = new HashMap<String, String>();
                if (keyField != null) {
                    headers.put(keyField, new String(message.key()));
                }
                eventList.add(EventBuilder.withBody(body, headers));
            }
            getChannelProcessor().processEventBatch(eventList);
            return Status.READY;
        } catch (Exception e) {
            log.error("KafkaSource EXCEPTION, {}", e.getMessage());
            return Status.BACKOFF;
        }
    }

    public void configure(Context context) {
        topic = context.getString("topic");
        keyField = context.getString("keyField", null);
        if(topic == null) {
            throw new ConfigurationException("Kafka topic must be specified.");
        }
        try {
            this.consumer = KafkaSourceUtil.getConsumer(context);
        } catch (IOException e) {
            log.error("IOException occur, {}", e.getMessage());
        } catch (InterruptedException e) {
            log.error("InterruptedException occur, {}", e.getMessage());
        }
        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(topic, new Integer(1));
        Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = consumer.createMessageStreams(topicCountMap);
        if(consumerMap == null) {
            throw new ConfigurationException("topicCountMap is null");
        }
        List<KafkaStream<byte[], byte[]>> topicList = consumerMap.get(topic);
        if(topicList == null || topicList.isEmpty()) {
            throw new ConfigurationException("topicList is null or empty");
        }
        KafkaStream<byte[], byte[]> stream =  topicList.get(0);
        it = stream.iterator();
    }

    @Override
    public synchronized void stop() {
        consumer.shutdown();
        super.stop();
    }

}
