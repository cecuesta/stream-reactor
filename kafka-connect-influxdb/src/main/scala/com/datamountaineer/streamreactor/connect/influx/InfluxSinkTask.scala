/*
 *  Copyright 2017 Datamountaineer.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datamountaineer.streamreactor.connect.influx

import java.util

import com.datamountaineer.streamreactor.connect.errors.ErrorPolicyEnum
import com.datamountaineer.streamreactor.connect.influx.config.{InfluxSettings, InfluxSinkConfig}
import com.datamountaineer.streamreactor.connect.influx.writers.{InfluxDbWriter, WriterFactoryFn}
import com.datamountaineer.streamreactor.connect.utils.ProgressCounter
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.connect.sink.{SinkRecord, SinkTask}

import scala.collection.JavaConversions._

/**
  * <h1>InfluxSinkTask</h1>
  *
  * Kafka Connect InfluxDb sink task. Called by framework to put records to the target database
  **/
class InfluxSinkTask extends SinkTask with StrictLogging {

  var writer: Option[InfluxDbWriter] = None
  private val progressCounter = new ProgressCounter

  /**
    * Parse the configurations and setup the writer
    **/
  override def start(props: util.Map[String, String]): Unit = {
    logger.info(

      """
        |  ____        _        __  __                   _        _
        | |  _ \  __ _| |_ __ _|  \/  | ___  _   _ _ __ | |_ __ _(_)_ __   ___  ___ _ __
        | | | | |/ _` | __/ _` | |\/| |/ _ \| | | | '_ \| __/ _` | | '_ \ / _ \/ _ \ '__|
        | | |_| | (_| | || (_| | |  | | (_) | |_| | | | | || (_| | | | | |  __/  __/ |
        | |____/ \__,_|\__\__,_|_|  |_|\___/ \__,_|_| |_|\__\__,_|_|_| |_|\___|\___|_|
        |  ___        __ _            ____  _       ____  _       _ by Stefan Bocutiu
        | |_ _|_ __  / _| |_   ___  _|  _ \| |__   / ___|(_)_ __ | | __
        |  | || '_ \| |_| | | | \ \/ / | | | '_ \  \___ \| | '_ \| |/ /
        |  | || | | |  _| | |_| |>  <| |_| | |_) |  ___) | | | | |   <
        | |___|_| |_|_| |_|\__,_/_/\_\____/|_.__/  |____/|_|_| |_|_|\_\
        | """.stripMargin)

    InfluxSinkConfig.config.parse(props)
    val sinkConfig = InfluxSinkConfig(props)
    val influxSettings = InfluxSettings(sinkConfig)

    //if error policy is retry set retry interval
    if (influxSettings.errorPolicy.equals(ErrorPolicyEnum.RETRY)) {
      context.timeout(sinkConfig.getInt(InfluxSinkConfig.ERROR_RETRY_INTERVAL_CONFIG).toLong)
    }

    writer = Some(WriterFactoryFn(influxSettings))

  }

  /**
    * Pass the SinkRecords to the writer for Writing
    **/
  override def put(records: util.Collection[SinkRecord]): Unit = {
    if (records.size() == 0) {
      logger.info("Empty list of records received.")
    }
    else {
      require(writer.nonEmpty, "Writer is not set!")
      writer.foreach(w => w.write(records.toSeq))
      progressCounter.update(records.toVector)
    }
  }

  /**
    * Clean up Influx connections
    **/
  override def stop(): Unit = {
    logger.info("Stopping InfluxDb sink.")
    writer.foreach(w => w.close())
    progressCounter.empty()
  }

  override def version(): String = getClass.getPackage.getImplementationVersion

  override def flush(offsets: util.Map[TopicPartition, OffsetAndMetadata]): Unit = {}
}
