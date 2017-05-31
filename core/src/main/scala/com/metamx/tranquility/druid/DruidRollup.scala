/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.metamx.tranquility.druid

import io.druid.data.input.impl.SpatialDimensionSchema
import io.druid.data.input.impl.TimestampSpec
import io.druid.java.util.common.granularity.Granularity
import io.druid.query.aggregation.AggregatorFactory
import java.{util => ju}
import scala.collection.JavaConverters._

/**
  * Describes rollup (dimensions, aggregators, index granularity) desired for a Druid datasource. Java users should use
  * the create methods on [[DruidRollup$]], as those accepts Java collections rather than Scala ones.
  *
  * See [[DruidDimensions.specific]], [[DruidDimensions.schemaless]], and [[DruidDimensions.schemalessWithExclusions]]
  * for three common ways of creating druid dimensions objects.
  */
class DruidRollup(
  val dimensions: DruidDimensions,
  val aggregators: IndexedSeq[AggregatorFactory],
  val indexGranularity: Granularity,
  val isRollup: Boolean = true
)
{
  private val additionalExclusions: Set[String] = {
    (aggregators.flatMap(_.requiredFields().asScala) ++
      aggregators.map(_.getName)).toSet
  }

  validate()

  def validate() {
    val dimensionNames = dimensions.knownDimensions
    val metricNames = aggregators.map(_.getName)

    val allColumnNames = Seq(DruidRollup.InternalTimeColumnName) ++ dimensionNames ++ metricNames
    val duplicateColumns = allColumnNames.groupBy(identity).filter(_._2.size > 1).keySet

    if (duplicateColumns.nonEmpty) {
      throw new IllegalArgumentException("Duplicate columns: %s" format duplicateColumns.mkString(", "))
    }
  }

  def isStringDimension(timestampSpec: TimestampSpec, fieldName: String) = {
    dimensions match {
      case dims: SpecificDruidDimensions => dims.dimensionsSet.contains(fieldName)
      case SchemalessDruidDimensions(exclusions, _) =>
        fieldName != timestampSpec.getTimestampColumn &&
          !additionalExclusions.contains(fieldName) &&
          !exclusions.contains(fieldName)
    }
  }
}

sealed abstract class DruidDimensions
{
  def specMap: ju.Map[String, AnyRef]

  def knownDimensions: Seq[String]

  def spatialDimensions: Seq[DruidSpatialDimension]

  def withSpatialDimensions(xs: java.util.List[DruidSpatialDimension]): DruidDimensions
}

sealed abstract class DruidSpatialDimension
{
  def schema: SpatialDimensionSchema
}

case class SingleFieldDruidSpatialDimension(name: String) extends DruidSpatialDimension
{
  override def schema = new SpatialDimensionSchema(name, List.empty[String].asJava)
}

case class MultipleFieldDruidSpatialDimension(name: String, fieldNames: Seq[String]) extends DruidSpatialDimension
{
  override def schema = new SpatialDimensionSchema(name, fieldNames.asJava)
}

case class SpecificDruidDimensions(
  dimensions: Seq[String],
  spatialDimensions: Seq[DruidSpatialDimension] = Nil
) extends DruidDimensions
{
  val dimensionsSet = dimensions.toSet

  @transient override lazy val specMap: ju.Map[String, AnyRef] = {
    Map[String, AnyRef](
      "dimensions" -> dimensions.toIndexedSeq.asJava,
      "spatialDimensions" -> spatialDimensions.map(_.schema).asJava
    ).asJava
  }

  override def knownDimensions: Seq[String] = {
    dimensions ++ spatialDimensions.map(_.schema.getDimName)
  }

  /**
    * Convenience method for Java users. Scala users should use "copy".
    */
  override def withSpatialDimensions(xs: java.util.List[DruidSpatialDimension]) = copy(
    spatialDimensions = xs.asScala.toIndexedSeq
  )
}

case class SchemalessDruidDimensions(
  dimensionExclusions: Set[String],
  spatialDimensions: Seq[DruidSpatialDimension] = Nil
) extends DruidDimensions
{
  @transient override lazy val specMap: ju.Map[String, AnyRef] = {
    // Null dimensions causes the Druid parser to go schemaless.
    Map[String, AnyRef](
      "dimensionExclusions" -> dimensionExclusions.toSeq.asJava,
      "spatialDimensions" -> spatialDimensions.map(_.schema).asJava
    ).asJava
  }

  override def knownDimensions: Seq[String] = {
    spatialDimensions.map(_.schema.getDimName)
  }

  /**
    * Convenience method for Java users. Scala users should use "copy".
    */
  override def withSpatialDimensions(xs: java.util.List[DruidSpatialDimension]) = copy(
    spatialDimensions = xs
      .asScala
      .toIndexedSeq
  )
}

object SchemalessDruidDimensions
{
  def apply(
    dimensionExclusions: Seq[String]
  ): SchemalessDruidDimensions =
  {
    SchemalessDruidDimensions(dimensionExclusions.toSet, Vector.empty)
  }

  def apply(
    dimensionExclusions: Seq[String],
    spatialDimensions: IndexedSeq[DruidSpatialDimension]
  ): SchemalessDruidDimensions =
  {
    SchemalessDruidDimensions(dimensionExclusions.toSet, spatialDimensions)
  }
}

object DruidRollup
{
  private val InternalTimeColumnName = "__time"

  /**
    * Builder for Scala users. Accepts a druid dimensions object and can be used to build rollups based on specific
    * or schemaless dimensions.
    */
  def apply(
    dimensions: DruidDimensions,
    aggregators: Seq[AggregatorFactory],
    indexGranularity: Granularity,
    isRollup: Boolean
  ) =
  {
    new DruidRollup(dimensions, aggregators.toIndexedSeq, indexGranularity, isRollup)
  }

  /**
    * Builder for Java users. Accepts a druid dimensions object and can be used to build rollups based on specific
    * or schemaless dimensions.
    *
    * See [[DruidDimensions.specific]], [[DruidDimensions.schemaless]], and [[DruidDimensions.schemalessWithExclusions]]
    * for three common ways of creating druid dimensions objects.
    */
  def create(
    dimensions: DruidDimensions,
    aggregators: java.util.List[AggregatorFactory],
    indexGranularity: Granularity,
    isRollup: Boolean
  ): DruidRollup =
  {
    new DruidRollup(
      dimensions,
      aggregators.asScala.toIndexedSeq,
      indexGranularity,
      isRollup
    )
  }

  /**
    * Builder for Java users. Accepts dimensions as strings, and creates a rollup with those specific dimensions.
    */
  def create(
    dimensions: java.util.List[String],
    aggregators: java.util.List[AggregatorFactory],
    indexGranularity: Granularity,
    isRollup: Boolean
  ): DruidRollup =
  {
    new DruidRollup(
      SpecificDruidDimensions(dimensions.asScala, Vector.empty),
      aggregators.asScala.toIndexedSeq,
      indexGranularity,
      isRollup
    )
  }
}

object DruidDimensions
{
  /**
    * Creates a druid dimensions object representing a specific set of dimensions. Only these fields will be
    * indexed as dimensions.
    */
  def specific(dimensions: java.util.List[String]): DruidDimensions = {
    SpecificDruidDimensions(dimensions.asScala, Vector.empty)
  }

  /**
    * Creates a druid dimensions object representing schemaless dimensions. All fields that are not part of an
    * aggregator will be indexed as dimensions.
    */
  def schemaless(): DruidDimensions = {
    SchemalessDruidDimensions(Vector.empty, Vector.empty)
  }

  /**
    * Creates a druid dimensions object representing schemaless dimensions. All fields that are not part of an
    * aggregator, and not in the exclusions list, will be indexed as dimensions.
    */
  def schemalessWithExclusions(dimensionExclusions: java.util.List[String]): DruidDimensions = {
    SchemalessDruidDimensions(dimensionExclusions.asScala.toSet, Vector.empty)
  }
}

object DruidSpatialDimension
{
  /**
    * Builder for Java users.
    */
  def singleField(name: String): DruidSpatialDimension = {
    new SingleFieldDruidSpatialDimension(name)
  }

  /**
    * Builder for Java users.
    */
  def multipleField(name: String, fieldNames: java.util.List[String]): DruidSpatialDimension = {
    new MultipleFieldDruidSpatialDimension(name, fieldNames.asScala)
  }
}
