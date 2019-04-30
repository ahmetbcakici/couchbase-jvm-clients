package com.couchbase.client.scala

import com.couchbase.client.scala.env.ClusterEnvironment
import com.couchbase.client.scala.json.JsonObject
import com.couchbase.client.scala.util.ScalaIntegrationTest
import com.couchbase.client.test.ClusterAwareIntegrationTest
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}

import scala.util.{Failure, Success}

@TestInstance(Lifecycle.PER_CLASS)
class ProjectionsSpec extends ScalaIntegrationTest {

  private var env: ClusterEnvironment = null
  private var cluster: Cluster = null
  private var coll: Collection = null
  private val docId = "projection-test"

  @BeforeAll
  def beforeAll(): Unit = {
    val config = ClusterAwareIntegrationTest.config()
    val x: ClusterEnvironment.Builder = environment
    env = x.build
    cluster = Cluster.connect(env)
    val bucket = cluster.bucket(config.bucketname)
    coll = bucket.defaultCollection

    val raw =
      """{
        |    "name": "Emmy-lou Dickerson",
        |    "age": 26,
        |    "animals": ["cat", "dog", "parrot"],
        |    "attributes": {
        |        "hair": "brown",
        |        "dimensions": {
        |            "height": 67,
        |            "weight": 175
        |        },
        |        "hobbies": [{
        |                "type": "winter sports",
        |                "name": "curling"
        |            },
        |            {
        |                "type": "summer sports",
        |                "name": "water skiing",
        |                "details": {
        |                    "location": {
        |                        "lat": 49.282730,
        |                        "long": -123.120735
        |                    }
        |                }
        |            }
        |        ]
        |    }
        |}
        |""".stripMargin
    coll.upsert(docId, raw)

  }

  @AfterAll
  def afterAll(): Unit = {
    cluster.shutdown()
    env.shutdown()
  }


  private def wrap(project: Seq[String]): JsonObject = {
    coll.get(docId, project = project) match {
      case Success(result) =>
        result.contentAs[JsonObject] match {
          case Success(body) => body
          case Failure(err) =>
            assert(false, s"unexpected error $err")
            throw new IllegalStateException()
        }
      case Failure(err) =>
        assert(false, s"unexpected error $err")
        throw new IllegalStateException()
    }
  }

  @Test
  def name() {
    val json = wrap(Seq("name"))
    assert(json.str("name") == "Emmy-lou Dickerson")
  }

  @Test
  def age() {
    val json = wrap(Seq("age"))
    assert(json.num("age") == 26)
  }

  @Test
  def animals() {
    val json = wrap(Seq("animals"))
    val arr = json.arr("animals")
    assert(arr.size == 3)
    assert(arr.toSeq == Seq("cat", "dog", "parrot"))
  }

  @Test
  def animals0() {
    val json = wrap(Seq("animals[0]"))
    val arr = json.arr("animals")
    assert(arr.toSeq == Seq("cat"))
  }

  @Test
  def animals2() {
    val json = wrap(Seq("animals[2]"))
    val arr = json.arr("animals")
    assert(arr.toSeq == Seq("parrot"))
  }

  @Test
  def attributes() {
    val json = wrap(Seq("attributes"))
    val field = json.obj("attributes")
    assert(field.size == 3)
    assert(field.arr("hobbies").obj(1).str("type") == "summer sports")
    assert(field.dyn.hobbies(1).name.str == "water skiing")
  }

  @Test
  def attributesHair() {
    val json = wrap(Seq("attributes.hair"))
    assert(json.obj("attributes").str("hair") == "brown")
  }

  @Test
  def attributesDimensions() {
    val json = wrap(Seq("attributes.dimensions"))
    assert(json.obj("attributes").obj("dimensions").num("height") == 67)
    assert(json.obj("attributes").obj("dimensions").num("weight") == 175)
  }

  @Test
  def attributesDimensionsHeight() {
    val json = wrap(Seq("attributes.dimensions"))
    assert(json.obj("attributes").obj("dimensions").num("height") == 67)
  }

}