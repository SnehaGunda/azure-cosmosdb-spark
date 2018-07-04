/**
  * The MIT License (MIT)
  * Copyright (c) 2016 Microsoft Corporation
  *
  * Permission is hereby granted, free of charge, to any person obtaining a copy
  * of this software and associated documentation files (the "Software"), to deal
  * in the Software without restriction, including without limitation the rights
  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  * copies of the Software, and to permit persons to whom the Software is
  * furnished to do so, subject to the following conditions:
  *
  * The above copyright notice and this permission notice shall be included in all
  * copies or substantial portions of the Software.
  *
  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  * SOFTWARE.
  */
package com.microsoft.azure.cosmosdb.spark.streaming

import org.apache.spark.sql.{ForeachWriter, Row}
import com.microsoft.azure.cosmosdb.spark.AsyncCosmosDBConnection
import com.microsoft.azure.cosmosdb.spark.config.{Config, CosmosDBConfig}

import scala.collection.mutable

case class CosmosDBForeachWriter(configMap: Map[String, String]) extends ForeachWriter[Row] {
  var asyncConnection: AsyncCosmosDBConnection = _
  var rows: mutable.ArrayBuffer[Row] = _

  val config = Config(configMap)
  val upsert: Boolean = config
    .getOrElse(CosmosDBConfig.Upsert, String.valueOf(CosmosDBConfig.DefaultUpsert))
    .toBoolean
  val writingBatchSize = config
    .getOrElse(CosmosDBConfig.WritingBatchSize, String.valueOf(CosmosDBConfig.DefaultWritingBatchSize))
    .toInt
  val writingBatchDelayMs = config
    .getOrElse(CosmosDBConfig.WritingBatchDelayMs, String.valueOf(CosmosDBConfig.DefaultWritingBatchDelayMs))
    .toInt
  val rootPropertyToSave = config
    .get[String](CosmosDBConfig.RootPropertyToSave)

  def open(partitionId: Long, version: Long): Boolean = {
    asyncConnection = new AsyncCosmosDBConnection(config)
    rows = new mutable.ArrayBuffer[Row]()
    true
  }

  def process(value: Row): Unit = {
    rows.append(value)
  }

  def close(errorOrNull: Throwable): Unit = {
    errorOrNull match {
      case t: Throwable => throw t
      case _ => {
        if(rows.nonEmpty) {
          asyncConnection.importWithRxJava(rows.iterator, asyncConnection, writingBatchSize, writingBatchDelayMs, rootPropertyToSave, upsert)
        }
      }
    }
  }
}