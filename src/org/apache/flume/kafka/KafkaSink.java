package org.apache.flume.kafka;

import com.cloudera.flume.conf.Context;
import com.cloudera.flume.conf.SinkFactory;
import com.cloudera.flume.conf.SinkFactory.SinkBuilder;
import com.cloudera.flume.core.Event;
import com.cloudera.flume.core.EventSink;
import com.cloudera.util.Pair;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import kafka.javaapi.producer.Producer;
import kafka.javaapi.producer.ProducerData;
import kafka.producer.ProducerConfig;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Arrays.asList;

public class KafkaSink extends EventSink.Base {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaSink.class);

  public static final String USAGE = "usage: kafka(\"zk.connect\", \"topic\")";

  private Producer producer;
  private String zkConnect;
  private String topic;
  private String appendField = "";

  public KafkaSink(String zkConnect, String topic) {
    this.zkConnect = zkConnect;
    this.topic = topic;
  }
  public KafkaSink(String zkConnect, String topic, String appendField) {
	    this.zkConnect = zkConnect;
	    this.topic = topic;
	    this.appendField = "\t" + appendField;
  }

  @Override
  public void append(Event e) throws IOException {
    byte[] partition = e.get("kafka.partition.key");
    String message = new String(e.getBody()) + appendField;
    if (partition == null) {
      producer.send(new ProducerData<String, byte[]>(topic, message.getBytes()));
    } else {
      producer.send(new ProducerData<String, byte[]>(topic, new String(partition, "UTF-8"),
          Lists.newArrayList(message.getBytes())));
    }
  }

  @Override
  synchronized public void close() throws IOException {
    if (producer != null) {
      producer.close();
      producer = null;
      LOG.info("Kafka sink successfully closed");
    } else {
      LOG.warn("Double close of Kafka sink");
    }
  }

  @Override
  synchronized public void open() throws IOException {
    checkState(producer == null, "Kafka sink is already initialized. Looks like sink close() " +
        "hasn't proceeded properly.");

    Properties props = new Properties();
    props.setProperty("zk.connect", zkConnect);
    props.put("serializer.class", "kafka.serializer.StringEncoder");

    ProducerConfig config = new ProducerConfig(props);
    producer = new Producer<String, byte[]>(config);
    LOG.info("Kafka sink successfully opened");
  }

  public static SinkBuilder builder() {
    return new SinkBuilder() {

      @Override
      public EventSink build(Context context, String... argv) {
        //checkArgument(argv.length == 2 || argv.length == 3, USAGE);

        String zkConnect = argv[0];
        String topic = argv[1];
        System.out.println(zkConnect);
        System.out.println(topic);
        checkArgument(!isNullOrEmpty(zkConnect), "zk.connect cannot be empty");
        checkArgument(!isNullOrEmpty(topic), "topic cannot be empty");
        if(argv.length == 3) {
        	return new KafkaSink(zkConnect, topic, argv[2]);
        }
        return new KafkaSink(zkConnect, topic);
      }
    };
  }

  @SuppressWarnings("unchecked")
  public static List<Pair<String, SinkFactory.SinkBuilder>> getSinkBuilders() {
    return asList(new Pair<String, SinkFactory.SinkBuilder>("kafka", builder()));
  }
}
