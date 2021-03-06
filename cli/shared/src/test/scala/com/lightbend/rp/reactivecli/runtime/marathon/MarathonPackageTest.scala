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
import com.lightbend.rp.reactivecli.argparse.GenerateDeploymentArgs
import com.lightbend.rp.reactivecli.argparse.marathon.MarathonArgs
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.docker.Config
import com.lightbend.rp.reactivecli.files._
import java.io.{ ByteArrayOutputStream, PrintStream }

import scala.collection.immutable.Seq
import utest._
import Argonaut._

object MarathonPackageTest extends TestSuite {
  val tests = this{
    "generateResources" - {
      val imageName = "fsat/testimpl:1.0.0-SNAPSHOT"

      val marathonArgs = MarathonArgs()
      val generateDeploymentArgs = GenerateDeploymentArgs(
        dockerImages = Seq(imageName),
        targetRuntimeArgs = Some(marathonArgs))

      "given valid docker image" - {
        val dockerConfig = Config(
          Config.Cfg(
            Image = Some(imageName),
            Labels = Some(Map(
              "com.lightbend.rp.app-name" -> "my-app",
              "com.lightbend.rp.app-version" -> "3.2.1-SNAPSHOT",
              "com.lightbend.rp.disk-space" -> "65536",
              "com.lightbend.rp.memory" -> "8192",
              "com.lightbend.rp.cpu" -> "0.5",
              "com.lightbend.rp.privileged" -> "true",
              "com.lightbend.rp.environment-variables.0.type" -> "literal",
              "com.lightbend.rp.environment-variables.0.name" -> "testing1",
              "com.lightbend.rp.environment-variables.0.value" -> "testingvalue1",
              "com.lightbend.rp.environment-variables.0.some-key" -> "test",
              "com.lightbend.rp.environment-variables.1.type" -> "ok1",
              "com.lightbend.rp.environment-variables.1.name" -> "testing2",
              "com.lightbend.rp.environment-variables.1.map-name" -> "mymap",
              "com.lightbend.rp.environment-variables.1.key" -> "mykey",
              "com.lightbend.rp.environment-variables.2.type" -> "ok2",
              "com.lightbend.rp.environment-variables.2.name" -> "testing3",
              "com.lightbend.rp.environment-variables.2.field-path" -> "metadata.name",
              "com.lightbend.rp.endpoints.0.name" -> "ep1",
              "com.lightbend.rp.endpoints.0.protocol" -> "http",
              "com.lightbend.rp.endpoints.0.version" -> "9",
              "com.lightbend.rp.endpoints.0.ingress.0.type" -> "http",
              "com.lightbend.rp.endpoints.0.ingress.0.paths.0" -> "/pizza",
              "com.lightbend.rp.endpoints.0.some-key" -> "test",
              "com.lightbend.rp.endpoints.0.acls.0.some-key" -> "test",
              "com.lightbend.rp.endpoints.1.name" -> "ep2",
              "com.lightbend.rp.endpoints.1.protocol" -> "tcp",
              "com.lightbend.rp.endpoints.1.version" -> "1",
              "com.lightbend.rp.endpoints.1.port" -> "1234",
              "com.lightbend.rp.endpoints.2.name" -> "ep3",
              "com.lightbend.rp.endpoints.2.protocol" -> "udp",
              "com.lightbend.rp.endpoints.2.port" -> "1234"))))

        "Validate Akka Clustering" - {
          "Require 2 instances by default" - {
            generateConfiguration(
              Seq(imageName ->
                dockerConfig.copy(config = dockerConfig.config.copy(Labels = dockerConfig.config.Labels.map(_ ++ Vector(
                  "com.lightbend.rp.modules.akka-cluster-bootstrapping.enabled" -> "true"))))),
              generateDeploymentArgs,
              marathonArgs)
              .map { result =>
                val failed = result.swap.toOption.get

                val message = failed.head

                val expected = "Akka Cluster Bootstrapping is enabled so you must specify `--instances 2` (or greater), or provide `--akka-cluster-join-existing` to only join already formed clusters"

                assert(message == expected)
              }
          }

          "Skip instance validation when only joining existing cluster" - {
            generateConfiguration(
              Seq(imageName ->
                dockerConfig.copy(config = dockerConfig.config.copy(Labels = dockerConfig.config.Labels.map(_ ++ Vector(
                  "com.lightbend.rp.modules.akka-cluster-bootstrapping.enabled" -> "true"))))),
              generateDeploymentArgs.copy(akkaClusterJoinExisting = true),
              marathonArgs)
              .map { result =>
                assert(result.isSuccess)
              }
          }
        }

        "Validate Group" - {
          ">= 2 instances" - {
            "fail if namespace omitted" - {
              generateConfiguration(
                Seq(
                  imageName -> dockerConfig,
                  imageName -> dockerConfig),
                generateDeploymentArgs,
                marathonArgs)
                .map { result =>
                  val failed = result.swap.toOption.get

                  val message = failed.head

                  val expected = "Missing group name; provide via `--namespace` flag"

                  assert(message == expected)
                }
            }

            "succeed if namespace provided" - {
              generateConfiguration(
                Seq(
                  imageName -> dockerConfig,
                  imageName -> dockerConfig),
                generateDeploymentArgs,
                marathonArgs.copy(namespace = Some("test")))
                .map { result =>
                  assert(result.isSuccess)
                }
            }
          }
        }
      }
    }

    "outputConfiguration" - {
      "saves generated config on disk" - {
        val config = GeneratedMarathonConfiguration(
          "marathon",
          "test",
          Json("key1" -> "value1".asJson),
          None)

        withTempFile { testFile =>
          outputConfiguration(config, MarathonArgs.Output.SaveToFile(testFile)).map { _ =>
            val deploymentFileContent = readFile(testFile)
            val deploymentFileExpected =
              """|{
                 |    "key1" : "value1"
                 |}""".stripMargin

            assert(deploymentFileContent == deploymentFileExpected)
          }
        }
      }

      "print generated config as json format into outputstream" - {
        val config = GeneratedMarathonConfiguration(
          "marathon",
          "test",
          Json("key1" -> "value1".asJson),
          None)

        val output = new ByteArrayOutputStream()
        val printStream = new PrintStream(output)

        outputConfiguration(config, MarathonArgs.Output.PipeToStream(printStream)).map { _ =>
          printStream.close()

          val generatedText = new String(output.toByteArray)
          val expectedText =
            """|{
               |    "key1" : "value1"
               |}
               |""".stripMargin

          assert(generatedText == expectedText)
        }
      }
    }

    "serviceName" - {
      "normalize service names" - {
        Seq(
          "--akka-remote--" -> "akka-remote",
          "__akka_remote__" -> "akka-remote",
          "user-search" -> "user-search",
          "USER_SEARCH" -> "user-search",
          "h!e**(l+l??O" -> "h-e---l-l--o").foreach {
            case (input, expectedResult) =>
              val result = serviceName(input)
              assert(result == expectedResult)
          }

        Seq(
          "akka!remote" -> "akka-remote",
          "my/test" -> "my/test").foreach {
            case (input, expectedResult) =>
              val result = serviceName(input, Set('/'))
              assert(result == expectedResult)
          }
      }
    }

    "envVarName" - {
      "normalize endpoint names" - {
        Seq(
          "akka-remote" -> "AKKA_REMOTE",
          "--akka-remote--" -> "AKKA_REMOTE",
          "__akka-remote__" -> "AKKA_REMOTE",
          "user-search" -> "USER_SEARCH",
          "h!e**(l+l??O" -> "H_E___L_L__O").foreach {
            case (input, expectedResult) =>
              val result = envVarName(input)
              assert(result == expectedResult)
          }
      }
    }
  }
}
