/*
 * Copyright 2017 Lightbend, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightbend.rp.reactivecli.runtime.marathon

import argonaut._
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.process.jq
import com.lightbend.rp.reactivecli.runtime.GeneratedResource
import scala.concurrent.Future

import Argonaut._

case class GeneratedMarathonConfiguration(resourceType: String, name: String, json: Json, jqExpression: Option[String]) extends GeneratedResource[Json] {
  def payload: Future[Json] = jqExpression.fold(Future.successful(json))(
    jq(_, json.nospaces)
      .map(
        _
          .parse
          .fold(
            error => throw new RuntimeException(s"Unable to parse output from jq: $error"),
            identity)))
}