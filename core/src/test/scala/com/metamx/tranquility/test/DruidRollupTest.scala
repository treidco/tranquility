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

package com.metamx.tranquility.test

import com.metamx.tranquility.druid.DruidRollup
import com.metamx.tranquility.druid.SchemalessDruidDimensions
import com.metamx.tranquility.druid.SpecificDruidDimensions
import io.druid.data.input.impl.TimestampSpec
import io.druid.java.util.common.granularity.Granularities
import io.druid.query.aggregation.CountAggregatorFactory
import io.druid.query.aggregation.LongSumAggregatorFactory
import org.scalatest.FunSuite
import org.scalatest.Matchers
import scala.collection.JavaConverters._

class DruidRollupTest extends FunSuite with Matchers
{
  test("Validations: Passing") {
    val rollup = DruidRollup(
      SpecificDruidDimensions(Vector("hey", "what"), Vector.empty),
      Seq(new CountAggregatorFactory("heyyo")),
      Granularities.NONE,
      true
    )
    rollup.validate()
  }

  test("Validations: Dimension and metric with the same name") {
    val e = the[IllegalArgumentException] thrownBy {
      DruidRollup(
        SpecificDruidDimensions(Vector("hey", "what"), Vector.empty),
        Seq(new CountAggregatorFactory("hey")),
        Granularities.NONE,
        true
      )
    }
    e.getMessage should be("Duplicate columns: hey")
  }

  test("Validations: Two metrics with the same name") {
    val e = the[IllegalArgumentException] thrownBy {
      DruidRollup(
        SpecificDruidDimensions(Vector("what"), Vector.empty),
        Seq(new CountAggregatorFactory("hey"), new LongSumAggregatorFactory("hey", "blah")),
        Granularities.NONE,
        true
      )
    }
    e.getMessage should be("Duplicate columns: hey")
  }

  test("Validations: Two dimensions with the same name") {
    val e = the[IllegalArgumentException] thrownBy {
      DruidRollup(
        SpecificDruidDimensions(Vector("what", "what"), Vector.empty),
        Seq(new CountAggregatorFactory("hey")),
        Granularities.NONE,
        true
      )
    }
    e.getMessage should be("Duplicate columns: what")
  }

  test("Dimension order is preserved") {
    val rollup = DruidRollup(
      SpecificDruidDimensions(Vector("e", "f", "a", "b", "z", "t"), Vector.empty),
      Seq(new CountAggregatorFactory("hey")),
      Granularities.NONE,
      true
    )
    rollup.dimensions.specMap.get("dimensions").asInstanceOf[java.util.List[String]].asScala should
      be(Seq("e", "f", "a", "b", "z", "t"))
  }

  test("isStringDimension: Specific") {
    val rollup = DruidRollup(
      SpecificDruidDimensions(Seq("foo", "bar")),
      Seq(new LongSumAggregatorFactory("hey", "there")),
      Granularities.NONE,
      true
    )
    val timestampSpec = new TimestampSpec("t", "auto", null)
    rollup.isStringDimension(timestampSpec, "t") should be(false)
    rollup.isStringDimension(timestampSpec, "hey") should be(false)
    rollup.isStringDimension(timestampSpec, "there") should be(false)
    rollup.isStringDimension(timestampSpec, "foo") should be(true)
    rollup.isStringDimension(timestampSpec, "bar") should be(true)
    rollup.isStringDimension(timestampSpec, "baz") should be(false)
    rollup.isStringDimension(timestampSpec, "qux") should be(false)
  }

  test("isStringDimension: Schemaless") {
    val rollup = DruidRollup(
      SchemalessDruidDimensions(Set("qux")),
      Seq(new LongSumAggregatorFactory("hey", "there")),
      Granularities.NONE,
      true
    )
    val timestampSpec = new TimestampSpec("t", "auto", null)
    rollup.isStringDimension(timestampSpec, "t") should be(false)
    rollup.isStringDimension(timestampSpec, "hey") should be(false)
    rollup.isStringDimension(timestampSpec, "there") should be(false)
    rollup.isStringDimension(timestampSpec, "foo") should be(true)
    rollup.isStringDimension(timestampSpec, "bar") should be(true)
    rollup.isStringDimension(timestampSpec, "baz") should be(true)
    rollup.isStringDimension(timestampSpec, "qux") should be(false)
  }
}
